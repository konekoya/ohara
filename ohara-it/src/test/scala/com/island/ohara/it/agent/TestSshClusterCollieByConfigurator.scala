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

package com.island.ohara.it.agent

import com.island.ohara.client.configurator.v0.NodeApi
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.common.util.Releasable
import com.island.ohara.configurator.Configurator
import org.junit.{After, Before}

import scala.concurrent.Await
import scala.concurrent.duration._
class TestSshClusterCollieByConfigurator extends BasicTests4ClusterCollieByConfigurator {
  override protected val nodeCache: Seq[Node] = CollieTestUtil.nodeCache()
  private[this] val nameHolder = new ClusterNameHolder(nodeCache)
  override protected val configurator: Configurator = Configurator.builder().build()

  @Before
  final def setup(): Unit = if (nodeCache.isEmpty) skipTest(s"You must assign nodes for collie tests")
  else {
    val nodeApi = NodeApi.access().hostname(configurator.hostname).port(configurator.port)
    nodeCache.foreach { node =>
      Await.result(nodeApi.add(
                     NodeApi.NodeCreationRequest(
                       name = Some(node.name),
                       port = node.port,
                       user = node.user,
                       password = node.password
                     )),
                   30 seconds)
    }

    val nodes = Await.result(nodeApi.list(), 120 seconds)
    nodes.size shouldBe nodeCache.size
    nodeCache.foreach(node => nodes.exists(_.name == node.name) shouldBe true)

  }

  @After
  def cleanAllContainers(): Unit = if (cleanup) Releasable.close(nameHolder)

  override protected def generateClusterName(): String = nameHolder.generateClusterName()
}
