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

package com.island.ohara.it.client

import com.island.ohara.agent.DataCollie
import com.island.ohara.agent.docker.DockerClient
import com.island.ohara.client.configurator.v0.NodeApi
import com.island.ohara.common.util.{CommonUtils, Releasable, VersionUtils}
import com.island.ohara.it.{EnvTestingUtils, IntegrationTest, ServiceNameHolder}
import org.junit.{After, Before}
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * a basic setup offering a configurator running on remote node.
  * this stuff is also in charge of releasing the configurator after testing.
  */
abstract class WithRemoteConfigurator extends IntegrationTest {
  private[this] val nodes                    = EnvTestingUtils.dockerNodes()
  private[this] val dockerClient             = DockerClient(DataCollie(nodes))
  private[this] val node                     = nodes.head
  private[this] val clusterNameHolder        = ServiceNameHolder(DockerClient(DataCollie(nodes)))
  private[this] val configuratorContainerKey = clusterNameHolder.generateClusterKey()

  protected val configuratorHostname: String = node.hostname
  protected val configuratorPort: Int        = CommonUtils.availablePort()

  /**
    * we have to combine the group and name in order to make name holder to delete related container.
    */
  protected val configuratorContainerName: String =
    s"${configuratorContainerKey.group()}-${configuratorContainerKey.name()}"

  private[this] val imageName = s"oharastream/configurator:${VersionUtils.VERSION}"

  @Before
  def setupConfigurator(): Unit = {
    result(dockerClient.imageNames(node.hostname)) should contain(imageName)
    result(
      dockerClient.containerCreator
        .nodeName(node.hostname)
        .imageName(imageName)
        .portMappings(Map(configuratorPort -> configuratorPort))
        .command(s"--port $configuratorPort")
        .routes(Map(node.hostname -> CommonUtils.address(node.hostname)))
        .name(configuratorContainerName)
        .create()
    )
    await { () =>
      try {
        result(
          NodeApi.access
            .hostname(node.hostname)
            .port(configuratorPort)
            .request
            .hostname(node.hostname)
            .port(node.port.get)
            .user(node.user.get)
            .password(node.password.get)
            .create()
        )
        true
      } catch {
        // wait for the configurator container
        case _: Throwable => false
      }
    }
  }

  @After
  def releaseConfigurator(): Unit = {
    Releasable.close(dockerClient)
    Releasable.close(clusterNameHolder)
  }
}
