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

package com.island.ohara.agent
import java.util.Objects

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.client.configurator.v0.ClusterStatus.Kind
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping}
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.ZookeeperApi.{
  CLIENT_PORT_DEFINITION,
  Creation,
  DATA_DIR_DEFINITION,
  INIT_LIMIT_DEFINITION,
  SYNC_LIMIT_DEFINITION,
  TICK_TIME_DEFINITION
}
import com.island.ohara.client.configurator.v0.{ClusterStatus, ZookeeperApi}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface of controlling zookeeper cluster.
  * It isolates the implementation of container manager from Configurator.
  */
trait ZookeeperCollie extends Collie {
  protected val log = Logger(classOf[ZookeeperCollie])
  // the required files for zookeeper
  // TODO: remove this hard code (see #2957)
  private[this] val homeFolder: String = ZookeeperApi.ZOOKEEPER_HOME_FOLDER
  private[this] val configPath: String = s"$homeFolder/conf/zoo.cfg"
  private[this] val dataFolder: String = s"$homeFolder/data"
  private[this] val myIdPath: String   = s"$dataFolder/myid"

  override val kind: Kind = ClusterStatus.Kind.ZOOKEEPER

  /**
    * This is a complicated process. We must address following issues.
    * 1) check the existence of cluster
    * 2) check the existence of nodes
    * 3) Each zookeeper container has got to export peer port, election port, and client port
    * 4) Each zookeeper container should use "docker host name" to replace "container host name".
    * 4) Add routes to all zookeeper containers
    * @return creator of broker cluster
    */
  override def creator: ZookeeperCollie.ClusterCreator = (executionContext, creation) => {
    implicit val exec: ExecutionContext = executionContext

    val resolveRequiredInfos = for {
      allNodes <- dataCollie.valuesByNames[Node](creation.nodeNames)
      existentNodes <- clusters().map(_.find(_.key == creation.key)).flatMap {
        case Some(value) =>
          dataCollie
            .valuesByNames[Node](value.nodeNames)
            .map(nodes => nodes.map(node => node -> value.containers.find(_.nodeName == node.hostname).get).toMap)
        case None => Future.successful(Map.empty[Node, ContainerInfo])
      }
    } yield (existentNodes, allNodes.filterNot(node => existentNodes.exists(_._1.hostname == node.hostname)))

    resolveRequiredInfos
      .map {
        case (existentNodes, newNodes) =>
          if (existentNodes.nonEmpty)
            throw new UnsupportedOperationException(
              s"zookeeper collie doesn't support to add node to a running cluster"
            )
          else newNodes
      }
      .flatMap { newNodes =>
        // add route in order to make zk node can connect to each other.
        val routes: Map[String, String] = newNodes.map(node => node.name -> CommonUtils.address(node.name)).toMap
        val successfulContainersFuture =
          if (newNodes.isEmpty) Future.successful(Seq.empty)
          else {
            // ssh connection is slow so we submit request by multi-thread
            Future.sequence(newNodes.zipWithIndex.map {
              case (newNode, nodeIndex) =>
                val hostname = Collie.containerHostName(creation.group, creation.name, kind)
                val zkServers = newNodes
                  .map(_.name)
                  .zipWithIndex
                  .map {
                    case (nodeName, serverIndex) =>
                      /**
                        * this is a long story.
                        * zookeeper quorum has to bind three ports: client port, peer port and election port
                        * 1) the client port, by default, is bound on all network interface (0.0.0.0)
                        * 2) the peer port and election port are bound on the "server name". this config has form:
                        *    server.$i=$serverName:$peerPort:$electionPort
                        *    Hence, the $serverName must be equal to hostname of container. Otherwise, the BindException
                        *    will be thrown. By contrast, the other $serverNames are used to connect (if the quorum is not lead)
                        *    Hence, the other $serverNames MUST be equal to "node names"
                        */
                      val serverName = if (serverIndex == nodeIndex) hostname else nodeName
                      s"server.$serverIndex=$serverName:${creation.peerPort}:${creation.electionPort}"
                  }
                  .toSet

                val containerInfo = ContainerInfo(
                  nodeName = newNode.hostname,
                  id = Collie.UNKNOWN,
                  imageName = creation.imageName,
                  // this fake container will be cached before refreshing cache so we make it running.
                  // other, it will be filtered later ...
                  state = ContainerState.RUNNING.name,
                  kind = Collie.UNKNOWN,
                  name = Collie.containerName(creation.group, creation.name, kind),
                  size = -1,
                  portMappings = creation.ports
                    .map(
                      port =>
                        PortMapping(
                          hostIp = Collie.UNKNOWN,
                          hostPort = port,
                          containerPort = port
                        )
                    )
                    .toSeq,
                  environments = Map(
                    // zookeeper does not support java.rmi.server.hostname so we have to disable the default settings of jmx from zookeeper
                    // and then add our custom settings.
                    // see https://issues.apache.org/jira/browse/ZOOKEEPER-3606
                    "JMXDISABLE" -> "true",
                    "JVMFLAGS" -> (s"-Dcom.sun.management.jmxremote" +
                      s" -Dcom.sun.management.jmxremote.authenticate=false" +
                      s" -Dcom.sun.management.jmxremote.ssl=false" +
                      s" -Dcom.sun.management.jmxremote.port=${creation.jmxPort}" +
                      s" -Dcom.sun.management.jmxremote.rmi.port=${creation.jmxPort}" +
                      s" -Djava.rmi.server.hostname=${newNode.hostname}")
                  ),
                  hostname = hostname
                )

                /**
                  * Construct the required configs for current container
                  * we will loop all the files in FILE_DATA of arguments : --file A --file B --file C
                  * the format of A, B, C should be file_name=k1=v1,k2=v2,k3,k4=v4...
                  */
                val arguments = ArgumentsBuilder()
                  .mainConfigFile(configPath)
                  .file(configPath)
                  .append(CLIENT_PORT_DEFINITION.key(), creation.clientPort)
                  .append(TICK_TIME_DEFINITION.key(), creation.tickTime)
                  .append(INIT_LIMIT_DEFINITION.key(), creation.initLimit)
                  .append(SYNC_LIMIT_DEFINITION.key(), creation.syncLimit)
                  .append(DATA_DIR_DEFINITION.key(), creation.dataDir)
                  .append(zkServers)
                  .done
                  .file(myIdPath)
                  .append(nodeIndex)
                  .done
                  .build
                doCreator(executionContext, containerInfo, newNode, routes, arguments)
                  .map(_ => Some(containerInfo))
                  .recover {
                    case e: Throwable =>
                      log.error(s"failed to create zookeeper container on ${newNode.hostname}", e)
                      None
                  }
            })
          }

        successfulContainersFuture.map(_.flatten.toSeq).flatMap { aliveContainers =>
          postCreate(
            clusterStatus = ClusterStatus(
              group = creation.group,
              name = creation.name,
              containers = aliveContainers,
              kind = ClusterStatus.Kind.ZOOKEEPER,
              state = toClusterState(aliveContainers).map(_.name),
              error = None
            ),
            existentNodes = Map.empty,
            routes = routes
          )
        }
      }
  }

  override protected[agent] def toStatus(key: ObjectKey, containers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[ClusterStatus] =
    Future.successful(
      ClusterStatus(
        group = key.group(),
        name = key.name(),
        containers = containers,
        kind = ClusterStatus.Kind.ZOOKEEPER,
        state = toClusterState(containers).map(_.name),
        // TODO how could we fetch the error?...by Sam
        error = None
      )
    )
}

object ZookeeperCollie {
  trait ClusterCreator extends Collie.ClusterCreator with ZookeeperApi.Request {
    override def create(): Future[Unit] =
      doCreate(
        executionContext = Objects.requireNonNull(executionContext),
        creation = creation
      )

    protected def doCreate(executionContext: ExecutionContext, creation: Creation): Future[Unit]
  }
}
