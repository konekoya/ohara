/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package oharastream.ohara.agent.docker

import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

import oharastream.ohara.agent.container.{ContainerClient, ContainerName}
import oharastream.ohara.agent.docker.DockerClient.{ContainerCreator, Inspector}
import oharastream.ohara.agent.{Agent, DataCollie}
import oharastream.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping}
import oharastream.ohara.client.configurator.v0.NodeApi.{Node, Resource}
import oharastream.ohara.common.util.{CommonUtils, Releasable}
import com.typesafe.scalalogging.Logger
import oharastream.ohara.agent.container.ContainerClient.{ContainerVolume, VolumeCreator}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface used to control remote node's docker service.
  * the default implementation is based on ssh client.
  * NOTED: All containers are executed background so as to avoid blocking call.
  */
trait DockerClient extends ContainerClient {
  override def containerCreator: ContainerCreator
  def containerInspector: Inspector
}

object DockerClient {
  private[DockerClient] val LOG = Logger(classOf[DockerClient])

  //-----------------------------[json object]-----------------------------//
  // the json generated by docker command use upper case ...
  private[this] case class Config(
    Hostname: String,
    Env: Seq[String],
    Image: String
  )

  private[this] implicit val CONFIG_FORMAT: RootJsonFormat[Config] = jsonFormat3(Config)

  private[this] case class State(
    Status: String,
    Running: Boolean,
    Paused: Boolean,
    Restarting: Boolean,
    OOMKilled: Boolean,
    Dead: Boolean,
    Pid: Int,
    ExitCode: Int,
    Error: String,
    StartedAt: String,
    FinishedAt: String
  )

  private[this] implicit val STATE_FORMAT: RootJsonFormat[State] = jsonFormat11(State)

  private[this] case class Address(
    HostIp: String,
    HostPort: String
  )

  private[this] implicit val ADDRESS_FORMAT: RootJsonFormat[Address] = jsonFormat2(Address)

  private[this] case class ContainerNetwork(Ports: Map[String, Seq[Address]], IPAddress: String, Gateway: String)

  private[this] implicit val NETWORK_FORMAT: RootJsonFormat[ContainerNetwork] = jsonFormat3(ContainerNetwork)

  private[this] case class Details(
    Id: String,
    Created: String,
    Name: String,
    SizeRw: Long,
    State: State,
    Config: Config,
    NetworkSettings: ContainerNetwork
  )

  private[this] implicit val DETAILS_FORMAT: RootJsonFormat[Details] = jsonFormat7(Details)

  private[this] case class Info(NCPU: Int, MemTotal: Long)

  private[this] implicit val INFO_FORMAT: RootJsonFormat[Info] = jsonFormat2(Info)

  private[this] val PATH_KEY = "path"
  private[this] case class VolumeInfo(Driver: String, Labels: String, Name: String) {
    def labels: Map[String, String] =
      Labels
        .split(",")
        .flatMap { s =>
          val ss = s.split("=")
          if (ss.length < 2) None
          else Some(ss(0) -> ss(1))
        }
        .toMap

    def path: String = labels(PATH_KEY)
  }

  private[this] implicit val VOLUME_INFO_FORMAT: RootJsonFormat[VolumeInfo] = jsonFormat3(VolumeInfo)

  /**
    * this is a specific label to ohara docker. It is useful in filtering out what we created.
    */
  private[this] val LABEL_KEY   = "createdByOhara"
  private[this] val LABEL_VALUE = "docker"

  //-----------------------------[constructor]-----------------------------//
  def apply(dataCollie: DataCollie): DockerClient = new DockerClient {
    private[this] val agentCache = new ConcurrentHashMap[Node, Agent]()

    private[this] def agent(hostname: String)(implicit executionContext: ExecutionContext): Future[Agent] =
      dataCollie.value[Node](hostname).map(agent)

    private[this] def agents()(implicit executionContext: ExecutionContext): Future[Seq[Agent]] =
      dataCollie
        .values[Node]()
        .map(_.map(agent))

    private[this] def agent(node: Node): Agent = {
      var agent: Agent = null
      do {
        agent = agentCache.compute(
          node,
          (node, previous) =>
            if (previous == null || !previous.isOpen)
              Agent.builder
                .hostname(node.hostname)
                .port(node._port)
                .user(node._user)
                .password(node._password)
                .build
            else previous
        )
      } while (agent == null || !agent.isOpen)
      agent
    }

    override def close(): Unit =
      agentCache.keys().asScala.foreach(node => Releasable.close(agentCache.remove(node)))

    override def containerCreator: ContainerCreator =
      (
        nodeName: String,
        hostname: String,
        imageName: String,
        name: String,
        command: Option[String],
        arguments: Seq[String],
        ports: Map[Int, Int],
        envs: Map[String, String],
        routes: Map[String, String],
        executionContext: ExecutionContext
      ) => {
        implicit val pool: ExecutionContext = executionContext
        agent(nodeName).map(
          _.execute(
            Seq(
              "docker run -d ",
              if (hostname == null) "" else s"-h $hostname",
              routes
                .map {
                  case (host, ip) => s"--add-host $host:$ip"
                }
                .mkString(" "),
              s"--name ${Objects.requireNonNull(name)}",
              ports
                .map {
                  case (hostPort, containerPort) => s"-p $hostPort:$containerPort"
                }
                .mkString(" "),
              envs
                .map {
                  case (key, value) => s"""-e \"$key=$value\""""
                }
                .mkString(" "),
              // add label so we can distinguish the containers from others
              s"--label $LABEL_KEY=$LABEL_VALUE",
              Objects.requireNonNull(imageName),
              command.getOrElse(""),
              arguments.map(arg => s"""\"$arg\"""").mkString(" ")
            ).filter(_.nonEmpty).mkString(" ")
          ).map(_ => Unit)
        )
      }

    override def containerNames()(implicit executionContext: ExecutionContext): Future[Seq[ContainerName]] =
      agents().map(
        _.flatMap(
          agent =>
            try agent
              .execute("docker ps -a --format \"{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Labels}}\"")
              .map(_.split("\n").toSeq)
              .map(_.map { line =>
                val items = line.split("\t")
                if (items.size < 3)
                  throw new IllegalArgumentException(
                    s"""invalid format:$line from docker with format \"{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Labels}}\""""
                  )
                else
                  new ContainerName(
                    id = items(0),
                    name = items(1),
                    imageName = items(2),
                    nodeName = agent.hostname
                  ) -> (if (items.size < 4) Map.empty[String, String] // no labels
                        else CommonUtils.parse(items(3).split(",").toSeq.asJava).asScala.toMap)
              })
              .map(_.toMap)
              .getOrElse(Map.empty)
              .filter {
                case (_, labels) => labels.get(LABEL_KEY).contains(LABEL_VALUE)
              }
              .keys
              .toSeq
            catch {
              case e: Throwable =>
                LOG.error(s"failed to get container names from ${agent.hostname}", e)
                Seq.empty
            }
        )
      )

    override def remove(name: String)(implicit executionContext: ExecutionContext): Future[Unit] =
      containerNames(name)
        .flatMap(
          Future.traverse(_)(
            container =>
              agent(container.nodeName)
                .map { agent =>
                  agent.execute(s"docker stop ${container.id}")
                  agent.execute(s"docker rm ${container.id}")
                }
          )
        )
        .map(_ => Unit)

    override def forceRemove(name: String)(implicit executionContext: ExecutionContext): Future[Unit] =
      containerNames(name)
        .flatMap(
          Future.traverse(_)(container => agent(container.nodeName).map(_.execute(s"docker rm -f ${container.id}")))
        )
        .map(_ => Unit)

    override def logs(name: String, sinceSeconds: Option[Long])(
      implicit executionContext: ExecutionContext
    ): Future[Map[ContainerName, String]] =
      containerNames(name)
        .flatMap(
          Future.traverse(_)(
            containerName =>
              agent(containerName.nodeName)
                .map(
                  _.execute(
                    s"docker container logs ${containerName.id} ${sinceSeconds.map(seconds => s"--since=${seconds}s").getOrElse("")}"
                  )
                )
                .map(_.getOrElse(throw new IllegalArgumentException(s"no response from $name")))
                .map(containerName -> _)
          )
        )
        .map(_.toMap)

    override def containerInspector: Inspector =
      containerInspector(null, false)

    private[this] def containerInspector(_name: String, beRoot: Boolean): Inspector =
      new Inspector {
        private[this] def rootConfig: String = if (beRoot) "-u root" else ""

        private[this] var name: String = _name

        override def name(name: String): Inspector = {
          this.name = CommonUtils.requireNonEmpty(name)
          this
        }

        override def cat(
          path: String
        )(implicit executionContext: ExecutionContext): Future[Map[ContainerName, String]] =
          containerNames(name)
            .flatMap(
              Future.traverse(_)(
                containerName =>
                  agent(containerName.nodeName)
                    .map(_.execute(s"""docker exec $rootConfig ${containerName.id} /bin/bash -c \"cat $path\""""))
                    .map(_.map(containerName -> _))
              )
            )
            .map(_.flatten.toMap)

        override def append(path: String, content: Seq[String])(
          implicit executionContext: ExecutionContext
        ): Future[Map[ContainerName, String]] =
          containerNames(name)
            .flatMap(
              Future.traverse(_)(
                containerName =>
                  agent(containerName.nodeName)
                    .map(_.execute(s"""docker exec $rootConfig ${containerName.id} /bin/bash -c \"echo \\"${content
                      .mkString("\n")}\\" >> $path\""""))
              )
            )
            .flatMap(_ => cat(path))

        override def write(path: String, content: Seq[String])(
          implicit executionContext: ExecutionContext
        ): Future[Map[ContainerName, String]] =
          containerNames(name)
            .flatMap(
              Future.traverse(_)(
                containerName =>
                  agent(containerName.nodeName)
                    .map(_.execute(s"""docker exec $rootConfig ${containerName.id} /bin/bash -c \"echo \\"${content
                      .mkString("\n")}\\" > $path\""""))
              )
            )
            .flatMap(_ => cat(path))

        override def asRoot(): Inspector = containerInspector(name, true)
      }

    override def containers()(implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] =
      containerNames()
        .flatMap { containerNames =>
          Future.sequence(containerNames.map { containerName =>
            agent(containerName.nodeName)
              .map {
                agent =>
                  Some(
                    agent
                      .execute(s"docker inspect --format '{{json .}}' --size ${containerName.id}")
                      .map(_.parseJson)
                      .map(DETAILS_FORMAT.read)
                      .map { details =>
                        ContainerInfo(
                          nodeName = agent.hostname,
                          id = details.Id,
                          imageName = details.Config.Image,
                          // we prefer to use enum to list the finite state and the constant strings are all upper case
                          // hence, we convert the string to upper case here.
                          state = details.State.Status.toUpperCase,
                          kind = "DOCKER",
                          name = containerName.name,
                          portMappings = details.NetworkSettings.Ports
                            .filter(_._1.contains("/tcp"))
                            .flatMap {
                              case (portAndProtocol, hostIpAndPort) =>
                                hostIpAndPort
                                  .map(_.HostPort.toInt)
                                  .map(
                                    hostPort =>
                                      PortMapping(
                                        hostIp = agent.hostname,
                                        hostPort = hostPort,
                                        containerPort = portAndProtocol.replace("/tcp", "").toInt
                                      )
                                  )
                            }
                            .toSeq,
                          size = details.SizeRw,
                          environments = details.Config.Env.flatMap { line =>
                            val index = line.indexOf("=")
                            if (index != 0 && index != line.length - 1)
                              Some(line.substring(0, index) -> line.substring(index + 1))
                            else None
                          }.toMap,
                          hostname = details.Config.Hostname
                        )
                      }
                      .getOrElse(throw new IllegalArgumentException(s"no response from ${agent.hostname}"))
                  )
              }
              .recover {
                case e: Throwable =>
                  DockerClient.LOG.error(s"fail to inspect docker:${containerName.name}", e)
                  None
              }
          })
        }
        .map(_.flatten.toSeq)

    override def imageNames()(implicit executionContext: ExecutionContext): Future[Map[String, Seq[String]]] =
      agents()
        .map(
          _.map(
            agent =>
              agent.hostname -> agent
                .execute("docker images --format {{.Repository}}:{{.Tag}}")
                .map(_.split("\n").toSeq)
                .filter(_.nonEmpty)
                .getOrElse(Seq.empty)
          )
        )
        .map(_.toMap)

    override def volumeCreator: VolumeCreator =
      (nodeName: String, name: String, path: String, executionContext: ExecutionContext) => {
        implicit val pool: ExecutionContext = executionContext
        agent(nodeName)
          .map(
            _.execute(
              s"docker volume create --name $name" +
                s" --label $LABEL_KEY=$LABEL_VALUE --label $PATH_KEY=$path" +
                s" --opt type=none --opt device=$path --opt o=bind"
            )
          )
          .map(_ => Unit)
      }

    override def volumes()(implicit executionContext: ExecutionContext): Future[Seq[ContainerVolume]] =
      agents()
        .map(_.flatMap { agent =>
          try agent
            .execute(s"docker volume ls --format '{{json .}}' --filter label=$LABEL_KEY=$LABEL_VALUE")
            .map(_.split("\n"))
            .map(
              _.map(_.parseJson)
                .map(VOLUME_INFO_FORMAT.read)
                .map { info =>
                  ContainerVolume(
                    name = info.Name,
                    driver = info.Driver,
                    path = info.path,
                    nodeName = agent.hostname
                  )
                }
                .toSeq
            )
            .getOrElse(Seq.empty)
          catch {
            case e: Throwable =>
              LOG.error(s"failed to get resources from ${agent.hostname}", e)
              Seq.empty
          }
        })

    override def removeVolumes(name: String)(implicit executionContext: ExecutionContext): Future[Unit] =
      volumes(name)
        .flatMap(
          Future.traverse(_)(
            volume =>
              agent(volume.nodeName)
                .map(_.execute(s"docker volume rm ${volume.name}"))
          )
        )
        .map(_ => Unit)

    override def resources()(implicit executionContext: ExecutionContext): Future[Map[String, Seq[Resource]]] =
      agents()
        .map(_.map { agent =>
          agent.hostname -> (try agent
            .execute("docker info --format '{{json .}}'")
            .map(_.parseJson)
            .map(INFO_FORMAT.read)
            .map { info =>
              Seq(
                Resource.cpu(info.NCPU, None),
                Resource.memory(info.MemTotal, None)
              )
            }
            .getOrElse(Seq.empty)
          catch {
            case e: Throwable =>
              LOG.error(s"failed to get resources from ${agent.hostname}", e)
              Seq.empty
          })
        }.filter(_._2.nonEmpty).toMap)
  }
  //-----------------------------[Inspector]-----------------------------//

  /**
    * used to "touch" a running container. For example, you can cat a file from a running container
    */
  sealed trait Inspector {
    def name(name: String): Inspector

    /**
      * convert the user to root. If the files accessed by inspect requires the root permission, you can run this method
      * before doing inspect action.
      *
      * @return a new ContainerInspector with root permission
      */
    def asRoot(): Inspector

    /**
      * get content of specified file from a container.
      * This method is useful in debugging when you want to check something according to the file content.
      *
      * @param path file path
      * @return content of file
      */
    def cat(path: String)(implicit executionContext: ExecutionContext): Future[Map[ContainerName, String]]

    /**
      * append something to the file of a running container
      *
      * @param content content
      * @param path    file path
      */
    def append(path: String, content: String)(
      implicit executionContext: ExecutionContext
    ): Future[Map[ContainerName, String]] =
      append(path, Seq(content))

    /**
      * append something to the file of a running container
      *
      * @param content content
      * @param path    file path
      */
    def append(path: String, content: Seq[String])(
      implicit executionContext: ExecutionContext
    ): Future[Map[ContainerName, String]]

    /**
      * clear and write something to the file of a running container
      *
      * @param content content
      * @param path    file path
      */
    def write(path: String, content: String)(
      implicit executionContext: ExecutionContext
    ): Future[Map[ContainerName, String]] =
      write(path, Seq(content))

    /**
      * clear and write something to the file of a running container
      *
      * @param content content
      * @param path    file path
      */
    def write(path: String, content: Seq[String])(
      implicit executionContext: ExecutionContext
    ): Future[Map[ContainerName, String]]
  }

  //-----------------------------[Creator]-----------------------------//

  /**
    * A interface used to run a docker container on remote node
    */
  trait ContainerCreator extends ContainerClient.ContainerCreator
}
