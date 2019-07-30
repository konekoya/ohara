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

package com.island.ohara.client.configurator

import com.island.ohara.kafka.connector.json.{ObjectKey, TopicKey}
import spray.json.{JsNull, JsValue, RootJsonFormat}

package object v0 {
  val START_COMMAND: String = "start"
  val STOP_COMMAND: String = "stop"
  val PAUSE_COMMAND: String = "pause"
  val RESUME_COMMAND: String = "resume"

  /**
    * In this APIs we have to integrate json format between scala (spray-json) and java (jackson).
    * The JsNull generated by spray-json confuse jackson to generate many "null" object. We remove the key related to
    * JsNull in order to avoid passing null to jackson.
    */
  private[v0] def noJsNull(fields: Map[String, JsValue]): Map[String, JsValue] = fields.filter {
    _._2 match {
      case JsNull => false
      case _      => true
    }
  }

  private[v0] def noJsNull(jsValue: JsValue): Map[String, JsValue] = noJsNull(jsValue.asJsObject.fields)

  implicit val OBJECT_KEY_FORMAT: RootJsonFormat[ObjectKey] = JsonRefiner[ObjectKey]
    .format(new RootJsonFormat[ObjectKey] {
      import spray.json._
      override def write(obj: ObjectKey): JsValue = ObjectKey.toJsonString(obj).parseJson
      override def read(json: JsValue): ObjectKey = ObjectKey.ofJsonString(json.toString())
    })
    .nullToString(Data.GROUP_KEY, () => Data.GROUP_DEFAULT)
    .rejectEmptyString()
    .refine

  implicit val TOPIC_KEY_FORMAT: RootJsonFormat[TopicKey] = JsonRefiner[TopicKey]
    .format(new RootJsonFormat[TopicKey] {
      import spray.json._
      override def write(obj: TopicKey): JsValue = TopicKey.toJsonString(obj).parseJson
      override def read(json: JsValue): TopicKey = TopicKey.ofJsonString(json.toString())
    })
    .nullToString(Data.GROUP_KEY, () => Data.GROUP_DEFAULT)
    .rejectEmptyString()
    .refine
}
