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

package com.island.ohara.connector.jio

import java.util

import com.island.ohara.common.setting.SettingDef
import com.island.ohara.kafka.connector.{RowSinkConnector, RowSinkTask, TaskSetting}

import scala.collection.JavaConverters._

/**
  * JsonOut is a simple restful proxy which allow us to receive data from topic via json request. For example,
  * GET /$path
  * [
  *   {
  *     "a": "b"
  *   }
  * ]
  * is json representation of Row("a" -> "b"), and the row come from topic
  *
  * Noted, the array and nested json is NOT supported. It means the value must be one of following type:
  * 1) string
  * 2) boolean
  * 3) number
  */
class JsonOut extends RowSinkConnector {
  private[this] var setting: TaskSetting = _

  override protected def _taskClass(): Class[_ <: RowSinkTask] = classOf[JsonOutTask]

  override protected def _taskSettings(maxTasks: Int): util.List[TaskSetting] = Seq.fill(maxTasks)(setting).asJava

  override protected def _start(config: TaskSetting): Unit =
    this.setting = config

  override protected def _stop(): Unit = {
    // do nothing
  }

  override protected def _definitions(): util.List[SettingDef] = DEFINITIONS.asJava

  override def needColumnDefinition(): Boolean = false
}
