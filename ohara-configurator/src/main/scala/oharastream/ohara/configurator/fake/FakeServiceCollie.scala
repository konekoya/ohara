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

package oharastream.ohara.configurator.fake

import oharastream.ohara.agent.container.ContainerName
import oharastream.ohara.agent.{DataCollie, ServiceCollie}
import oharastream.ohara.client.configurator.v0.NodeApi.{Node, Resource}
import oharastream.ohara.client.configurator.v0.{BrokerApi, ShabondiApi, StreamApi, WorkerApi, ZookeeperApi}
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.configurator.store.DataStore

import scala.concurrent.{ExecutionContext, Future}

/**
  * It doesn't involve any running cluster but save all description in memory
  */
private[configurator] class FakeServiceCollie(
  dataCollie: DataCollie,
  store: DataStore,
  bkConnectionProps: String,
  wkConnectionProps: String
) extends ServiceCollie {
  def this(dataCollie: DataCollie, store: DataStore) {
    this(dataCollie, store, null, null)
  }

  override val zookeeperCollie: FakeZookeeperCollie = new FakeZookeeperCollie(dataCollie)

  override val brokerCollie: FakeBrokerCollie = new FakeBrokerCollie(dataCollie, bkConnectionProps)

  override val workerCollie: FakeWorkerCollie = new FakeWorkerCollie(dataCollie, wkConnectionProps)

  override val streamCollie: FakeStreamCollie = new FakeStreamCollie(dataCollie)

  override val shabondiCollie: FakeShabondiCollie = new FakeShabondiCollie(dataCollie)

  override def close(): Unit = {
    // do nothing
  }

  override def imageNames()(implicit executionContext: ExecutionContext): Future[Map[Node, Seq[String]]] =
    dataCollie.values[Node]().map { nodes =>
      nodes
        .map(
          _ -> Seq(
            ZookeeperApi.IMAGE_NAME_DEFAULT,
            BrokerApi.IMAGE_NAME_DEFAULT,
            WorkerApi.IMAGE_NAME_DEFAULT,
            StreamApi.IMAGE_NAME_DEFAULT,
            ShabondiApi.IMAGE_NAME_DEFAULT
          )
        )
        .toMap
    }

  override def verifyNode(node: Node)(implicit executionContext: ExecutionContext): Future[String] =
    Future.successful(s"This is fake mode so we didn't test connection actually...")

  override def containerNames()(implicit executionContext: ExecutionContext): Future[Seq[ContainerName]] =
    Future.successful(Seq.empty)

  override def resources()(implicit executionContext: ExecutionContext): Future[Map[String, Seq[Resource]]] =
    dataCollie
      .values[Node]()
      .map(
        nodes =>
          nodes
            .map(node => {
              val cpuResource    = Resource.cpu(32, Option(positiveValue(CommonUtils.randomDouble())))
              val memoryResource = Resource.memory(137438953472L, Option(positiveValue(CommonUtils.randomDouble())))
              (node.hostname, Seq(cpuResource, memoryResource))
            })
            .toMap
      )

  private[this] def positiveValue(value: Double): Double = Math.abs(value)

  override def log(containerName: String, sinceSeconds: Option[Long])(
    implicit executionContext: ExecutionContext
  ): Future[Map[ContainerName, String]] =
    Future.failed(new NoSuchElementException)
}
