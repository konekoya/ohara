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

package com.island.ohara.client.configurator.v0

import com.island.ohara.client.configurator.v0.FileInfoApi._
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.{CommonUtils, VersionUtils}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, _}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
object WorkerApi {

  /**
    * The default value of group for this API.
    */
  val GROUP_DEFAULT: String = com.island.ohara.client.configurator.v0.GROUP_DEFAULT
  val WORKER_SERVICE_NAME: String = "wk"

  val WORKER_PREFIX_PATH: String = "workers"

  /**
    * the default docker image used to run containers of worker cluster
    */
  val IMAGE_NAME_DEFAULT: String = s"oharastream/connect-worker:${VersionUtils.VERSION}"

  private[this] val BROKER_CLUSTER_NAME_KEY = "brokerClusterName"
  private[this] val CLIENT_PORT_KEY = "clientPort"
  private[this] val JMX_PORT_KEY = "jmxPort"
  private[this] val GROUP_ID_KEY = "groupId"
  private[this] val STATUS_TOPIC_NAME_KEY = "statusTopicName"
  private[this] val STATUS_TOPIC_PARTITIONS_KEY = "statusTopicPartitions"
  private[this] val STATUS_TOPIC_REPLICATIONS_KEY = "statusTopicReplications"
  private[this] val CONFIG_TOPIC_NAME_KEY = "configTopicName"
  private[this] val CONFIG_TOPIC_PARTITIONS_KEY = "configTopicPartitions"
  private[this] val CONFIG_TOPIC_REPLICATIONS_KEY = "configTopicReplications"
  private[this] val OFFSET_TOPIC_NAME_KEY = "offsetTopicName"
  private[this] val OFFSET_TOPIC_PARTITIONS_KEY = "offsetTopicPartitions"
  private[this] val OFFSET_TOPIC_REPLICATIONS_KEY = "offsetTopicReplications"
  private[this] val JAR_KEYS_KEY = "jarKeys"
  private[this] val JAR_INFOS_KEY = "jarInfos"
  private[this] val TAGS_KEY = "tags"

  final case class Creation private[WorkerApi] (settings: Map[String, JsValue]) extends ClusterCreationRequest {

    /**
      * reuse the parser from Update.
      * @param settings settings
      * @return update
      */
    private[this] implicit def update(settings: Map[String, JsValue]): Update = Update(noJsNull(settings))
    override def group: String = GROUP_DEFAULT
    override def name: String = noJsNull(settings)(NAME_KEY).convertTo[String]
    override def imageName: String = settings.imageName.get
    def brokerClusterName: Option[String] = settings.brokerClusterName
    def clientPort: Int = settings.clientPort.get
    def jmxPort: Int = settings.jmxPort.get
    def groupId: String = settings.groupId.get
    def statusTopicName: String = settings.statusTopicName.get
    def statusTopicPartitions: Int = settings.statusTopicPartitions.get
    def statusTopicReplications: Short = settings.statusTopicReplications.get
    def configTopicName: String = settings.configTopicName.get
    def configTopicReplications: Short = settings.configTopicReplications.get
    def offsetTopicName: String = settings.offsetTopicName.get
    def offsetTopicPartitions: Int = settings.offsetTopicPartitions.get
    def offsetTopicReplications: Short = settings.offsetTopicReplications.get
    def jarKeys: Set[ObjectKey] = settings.jarKeys.getOrElse(Set.empty)

    /**
      * expose to WorkerCollie
      */
    private[ohara] def jarInfos: Seq[FileInfo] = settings.jarInfos.getOrElse(Seq.empty)

    override def tags: Map[String, JsValue] = settings.tags.get
    override def nodeNames: Set[String] = settings.nodeNames.get
    override def ports: Set[Int] = Set(clientPort, jmxPort)
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val WORKER_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    basicRulesOfCreation[Creation](IMAGE_NAME_DEFAULT)
      .format(new RootJsonFormat[Creation] {
        override def read(json: JsValue): Creation = Creation(noJsNull(json.asJsObject.fields))
        override def write(obj: Creation): JsValue = JsObject(noJsNull(obj.settings))
      })
      .rejectNegativeNumber()
      // number of config topic's partition is always be 1
      .rejectKeyword(CONFIG_TOPIC_PARTITIONS_KEY)
      .nullToRandomPort(CLIENT_PORT_KEY)
      .requireBindPort(CLIENT_PORT_KEY)
      .nullToRandomPort(JMX_PORT_KEY)
      .requireBindPort(JMX_PORT_KEY)
      .nullToRandomString(GROUP_ID_KEY)
      .nullToRandomString(CONFIG_TOPIC_NAME_KEY)
      .nullToShort(CONFIG_TOPIC_REPLICATIONS_KEY, 1)
      .nullToRandomString(OFFSET_TOPIC_NAME_KEY)
      .nullToInt(OFFSET_TOPIC_PARTITIONS_KEY, 1)
      .nullToShort(OFFSET_TOPIC_REPLICATIONS_KEY, 1)
      .nullToRandomString(STATUS_TOPIC_NAME_KEY)
      .nullToInt(STATUS_TOPIC_PARTITIONS_KEY, 1)
      .nullToShort(STATUS_TOPIC_REPLICATIONS_KEY, 1)
      .nullToEmptyArray(JAR_KEYS_KEY)
      .refine

  final case class Update private[WorkerApi] (settings: Map[String, JsValue]) extends ClusterUpdateRequest {
    override def imageName: Option[String] = noJsNull(settings).get(IMAGE_NAME_KEY).map(_.convertTo[String])
    def brokerClusterName: Option[String] = noJsNull(settings).get(BROKER_CLUSTER_NAME_KEY).map(_.convertTo[String])
    def clientPort: Option[Int] = noJsNull(settings).get(CLIENT_PORT_KEY).map(_.convertTo[Int])
    def jmxPort: Option[Int] = noJsNull(settings).get(JMX_PORT_KEY).map(_.convertTo[Int])
    def groupId: Option[String] = noJsNull(settings).get(GROUP_ID_KEY).map(_.convertTo[String])
    def statusTopicName: Option[String] = noJsNull(settings).get(STATUS_TOPIC_NAME_KEY).map(_.convertTo[String])
    def statusTopicPartitions: Option[Int] = noJsNull(settings).get(STATUS_TOPIC_PARTITIONS_KEY).map(_.convertTo[Int])
    def statusTopicReplications: Option[Short] =
      noJsNull(settings).get(STATUS_TOPIC_REPLICATIONS_KEY).map(_.convertTo[Short])
    def configTopicName: Option[String] = noJsNull(settings).get(CONFIG_TOPIC_NAME_KEY).map(_.convertTo[String])
    def configTopicReplications: Option[Short] =
      noJsNull(settings).get(CONFIG_TOPIC_REPLICATIONS_KEY).map(_.convertTo[Short])
    def offsetTopicName: Option[String] = noJsNull(settings).get(OFFSET_TOPIC_NAME_KEY).map(_.convertTo[String])
    def offsetTopicPartitions: Option[Int] = noJsNull(settings).get(OFFSET_TOPIC_PARTITIONS_KEY).map(_.convertTo[Int])
    def offsetTopicReplications: Option[Short] =
      noJsNull(settings).get(OFFSET_TOPIC_REPLICATIONS_KEY).map(_.convertTo[Short])
    import scala.collection.JavaConverters._
    def jarKeys: Option[Set[ObjectKey]] = jarInfos
      .map(_.map(_.key).toSet)
      .orElse(noJsNull(settings).get(JAR_KEYS_KEY).map(js => ObjectKey.toObjectKeys(js.toString).asScala.toSet))

    /**
      * Normally, Update request should not carry the jar infos since the jar infos is returned by file store according
      * to input jar keys. Hence, this method is not public and it is opened to this scope only.
      * @return jar infos
      */
    private[WorkerApi] def jarInfos: Option[Seq[FileInfo]] =
      noJsNull(settings)
        .get(JAR_INFOS_KEY)
        .map(_.convertTo[JsArray].elements.map(FileInfoApi.FILE_INFO_JSON_FORMAT.read))
    override def tags: Option[Map[String, JsValue]] = noJsNull(settings).get(TAGS_KEY).map(_.asJsObject.fields)

    override def nodeNames: Option[Set[String]] =
      noJsNull(settings).get(NODE_NAMES_KEY).map(_.convertTo[Seq[String]].toSet)
  }
  implicit val WORKER_UPDATE_JSON_FORMAT: OharaJsonFormat[Update] =
    basicRulesOfUpdate[Update]
      .format(new RootJsonFormat[Update] {
        override def read(json: JsValue): Update = Update(noJsNull(json.asJsObject.fields))
        override def write(obj: Update): JsValue = JsObject(obj.settings)
      })
      .rejectNegativeNumber()
      .requireBindPort("clientPort")
      .requireBindPort("jmxPort")
      .refine

  final case class WorkerClusterInfo private[ohara] (settings: Map[String, JsValue],
                                                     connectors: Seq[Definition],
                                                     deadNodes: Set[String],
                                                     lastModified: Long,
                                                     state: Option[String],
                                                     error: Option[String])
      extends ClusterInfo {

    /**
      * reuse the parser from Creation.
      * @param settings settings
      * @return creation
      */
    private[this] implicit def creation(settings: Map[String, JsValue]): Creation = Creation(noJsNull(settings))

    override def name: String = settings.name
    override def imageName: String = settings.imageName
    def brokerClusterName: String = settings.brokerClusterName.get
    def clientPort: Int = settings.clientPort
    def jmxPort: Int = settings.jmxPort
    def groupId: String = settings.groupId
    def statusTopicName: String = settings.statusTopicName
    def statusTopicPartitions: Int = settings.statusTopicPartitions
    def statusTopicReplications: Short = settings.statusTopicReplications
    def configTopicName: String = settings.configTopicName
    def configTopicPartitions: Int = 1
    def configTopicReplications: Short = settings.configTopicReplications
    def offsetTopicName: String = settings.offsetTopicName
    def offsetTopicPartitions: Int = settings.offsetTopicPartitions
    def offsetTopicReplications: Short = settings.offsetTopicReplications
    def jarInfos: Seq[FileInfo] = settings.jarInfos
    def jarKeys: Set[ObjectKey] = settings.jarKeys
    def nodeNames: Set[String] = settings.nodeNames
    override def tags: Map[String, JsValue] = settings.tags

    /**
      * Our client to broker and worker accept the connection props:host:port,host2:port2
      */
    def connectionProps: String = nodeNames.map(n => s"$n:$clientPort").mkString(",")

    override def ports: Set[Int] = settings.ports

    override def group: String = GROUP_DEFAULT

    override def kind: String = WORKER_SERVICE_NAME

    override def clone(nodeNames: Set[String],
                       deadNodes: Set[String],
                       state: Option[String],
                       error: Option[String],
                       metrics: Metrics,
                       tags: Map[String, JsValue]): WorkerClusterInfo = copy(
      settings = access.request.settings(settings).nodeNames(nodeNames).tags(tags).creation.settings,
      deadNodes = deadNodes,
      state = state,
      error = error
    )

    // TODO: expose the metrics for wk
    override def metrics: Metrics = Metrics.EMPTY
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val WORKER_CLUSTER_INFO_JSON_FORMAT: OharaJsonFormat[WorkerClusterInfo] =
    JsonRefiner[WorkerClusterInfo]
      .format(new RootJsonFormat[WorkerClusterInfo] {
        implicit val DEFINITION_JSON_FORMAT: OharaJsonFormat[Definition] = Definition.DEFINITION_JSON_FORMAT
        private[this] val format = jsonFormat6(WorkerClusterInfo)
        override def write(obj: WorkerClusterInfo): JsValue = JsObject(
          noJsNull(
            format.write(obj).asJsObject.fields ++
              Map(
                // TODO: remove the following stale fields ... by chia
                NAME_KEY -> JsString(obj.name),
                IMAGE_NAME_KEY -> JsString(obj.imageName),
                BROKER_CLUSTER_NAME_KEY -> JsString(obj.brokerClusterName),
                CLIENT_PORT_KEY -> JsNumber(obj.clientPort),
                JMX_PORT_KEY -> JsNumber(obj.jmxPort),
                GROUP_ID_KEY -> JsString(obj.groupId),
                STATUS_TOPIC_NAME_KEY -> JsString(obj.statusTopicName),
                STATUS_TOPIC_PARTITIONS_KEY -> JsNumber(obj.statusTopicPartitions),
                STATUS_TOPIC_REPLICATIONS_KEY -> JsNumber(obj.statusTopicReplications),
                CONFIG_TOPIC_NAME_KEY -> JsString(obj.configTopicName),
                CONFIG_TOPIC_PARTITIONS_KEY -> JsNumber(obj.configTopicPartitions),
                CONFIG_TOPIC_REPLICATIONS_KEY -> JsNumber(obj.configTopicReplications),
                OFFSET_TOPIC_NAME_KEY -> JsString(obj.offsetTopicName),
                OFFSET_TOPIC_PARTITIONS_KEY -> JsNumber(obj.offsetTopicPartitions),
                OFFSET_TOPIC_REPLICATIONS_KEY -> JsNumber(obj.offsetTopicReplications),
                JAR_INFOS_KEY -> JsArray(obj.jarInfos.map(FILE_INFO_JSON_FORMAT.write).toVector),
                NODE_NAMES_KEY -> JsArray(obj.nodeNames.map(JsString(_)).toVector),
                TAGS_KEY -> JsObject(obj.tags)
              ))
        )

        override def read(json: JsValue): WorkerClusterInfo = format.read(json)
      })
      .refine

  /**
    * used to generate the payload and url for POST/PUT request.
    */
  sealed trait Request {
    @Optional("default name is a random string")
    def name(name: String): Request = setting(NAME_KEY, JsString(CommonUtils.requireNonEmpty(name)))

    @Optional("the default image is IMAGE_NAME_DEFAULT")
    def imageName(imageName: String): Request =
      setting(IMAGE_NAME_KEY, JsString(CommonUtils.requireNonEmpty(imageName)))

    @Optional("the default port is random")
    def clientPort(clientPort: Int): Request =
      setting(CLIENT_PORT_KEY, JsNumber(CommonUtils.requireConnectionPort(clientPort)))

    @Optional("the default port is random")
    def jmxPort(jmxPort: Int): Request = setting(JMX_PORT_KEY, JsNumber(CommonUtils.requireConnectionPort(jmxPort)))

    @Optional("Ignoring the name will invoke an auto-mapping to existent broker cluster")
    def brokerClusterName(brokerClusterName: String): Request =
      setting(BROKER_CLUSTER_NAME_KEY, JsString(CommonUtils.requireNonEmpty(brokerClusterName)))

    @Optional("the default port is random")
    def groupId(groupId: String): Request = setting(GROUP_ID_KEY, JsString(CommonUtils.requireNonEmpty(groupId)))
    @Optional("the default port is random")
    def statusTopicName(statusTopicName: String): Request =
      setting(STATUS_TOPIC_NAME_KEY, JsString(CommonUtils.requireNonEmpty(statusTopicName)))
    @Optional("the default number is 1")
    def statusTopicPartitions(statusTopicPartitions: Int): Request =
      setting(STATUS_TOPIC_PARTITIONS_KEY, JsNumber(CommonUtils.requirePositiveInt(statusTopicPartitions)))
    @Optional("the default number is 1")
    def statusTopicReplications(statusTopicReplications: Short): Request =
      setting(STATUS_TOPIC_REPLICATIONS_KEY, JsNumber(CommonUtils.requirePositiveShort(statusTopicReplications)))
    @Optional("the default number is random")
    def configTopicName(configTopicName: String): Request =
      setting(CONFIG_TOPIC_NAME_KEY, JsString(CommonUtils.requireNonEmpty(configTopicName)))
    @Optional("the default number is 1")
    def configTopicReplications(configTopicReplications: Short): Request =
      setting(CONFIG_TOPIC_REPLICATIONS_KEY, JsNumber(CommonUtils.requirePositiveShort(configTopicReplications)))
    def offsetTopicName(offsetTopicName: String): Request =
      setting(OFFSET_TOPIC_NAME_KEY, JsString(CommonUtils.requireNonEmpty(offsetTopicName)))
    @Optional("the default number is 1")
    def offsetTopicPartitions(offsetTopicPartitions: Int): Request =
      setting(OFFSET_TOPIC_PARTITIONS_KEY, JsNumber(CommonUtils.requirePositiveInt(offsetTopicPartitions)))
    @Optional("the default number is 1")
    def offsetTopicReplications(offsetTopicReplications: Short): Request =
      setting(OFFSET_TOPIC_REPLICATIONS_KEY, JsNumber(CommonUtils.requirePositiveShort(offsetTopicReplications)))
    @Optional("the default value is empty")
    def jarKeys(jarKeys: Set[ObjectKey]): Request =
      setting(JAR_KEYS_KEY, JsArray(jarKeys.map(ObjectKey.toJsonString).map(_.parseJson).toVector))
    @Optional("the default value is empty")
    def jarInfos(jarInfos: Seq[FileInfo]): Request =
      setting(JAR_INFOS_KEY, JsArray(jarInfos.map(FILE_INFO_JSON_FORMAT.write).toVector))
    def nodeName(nodeName: String): Request = nodeNames(Set(CommonUtils.requireNonEmpty(nodeName)))
    def nodeNames(nodeNames: Set[String]): Request =
      setting(NODE_NAMES_KEY, JsArray(CommonUtils.requireNonEmpty(nodeNames.asJava).asScala.map(JsString(_)).toVector))
    @Optional("default value is empty array in creation and None in update")
    def tags(tags: Map[String, JsValue]): Request = setting(TAGS_KEY, JsObject(tags))

    def setting(key: String, value: JsValue): Request = settings(Map(key -> value))

    def settings(settings: Map[String, JsValue]): Request

    /**
      * generate the POST request
      * @param executionContext thread pool
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo]

    /**
      * generate the PUT request
      * @param executionContext execution context
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo]

    /**
      * Creation instance includes many useful parsers for custom settings so we open it to code with a view to reusing
      * those convenient parsers.
      * @return the payload of creation
      */
    def creation: Creation

    /**
      * for testing only
      * @return the payload of update
      */
    private[v0] def update: Update
  }

  final class Access private[WorkerApi] extends ClusterAccess[WorkerClusterInfo](WORKER_PREFIX_PATH, GROUP_DEFAULT) {
    def request: Request = new Request {
      private[this] val settings: mutable.Map[String, JsValue] = mutable.Map[String, JsValue]()

      override def settings(settings: Map[String, JsValue]): Request = {
        this.settings ++= settings
        this
      }

      override def creation: Creation =
        WORKER_CREATION_JSON_FORMAT.read(WORKER_CREATION_JSON_FORMAT.write(Creation(noJsNull(settings.toMap))))

      override private[v0] def update: Update =
        WORKER_UPDATE_JSON_FORMAT.read(WORKER_UPDATE_JSON_FORMAT.write(Update(noJsNull(settings.toMap))))

      override def create()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo] =
        exec.post[Creation, WorkerClusterInfo, ErrorApi.Error](
          url,
          creation
        )

      override def update()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo] =
        exec.put[Update, WorkerClusterInfo, ErrorApi.Error](
          // use creation to parse the name :)
          s"$url/${creation.name}",
          update
        )
    }
  }

  def access: Access = new Access
}
