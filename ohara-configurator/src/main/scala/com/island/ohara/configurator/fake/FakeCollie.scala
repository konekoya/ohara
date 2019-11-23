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

package com.island.ohara.configurator.fake

import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.agent.{Collie, DataCollie, NoSuchClusterException, ServiceState}
import com.island.ohara.client.configurator.v0.ClusterStatus
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping}
import com.island.ohara.common.annotations.VisibleForTesting
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
private[configurator] abstract class FakeCollie(dataCollie: DataCollie) extends Collie {
  @VisibleForTesting
  protected[configurator] val clusterCache = new ConcurrentSkipListMap[ObjectKey, ClusterStatus]()

  /**
    * update the in-memory cluster status and container infos
    * @return cluster status
    */
  private[configurator] def addCluster(
    key: ObjectKey,
    kind: ClusterStatus.Kind,
    nodeNames: Set[String],
    imageName: String,
    ports: Set[Int]
  ): ClusterStatus =
    clusterCache.put(
      key,
      ClusterStatus(
        group = key.group(),
        name = key.name(),
        state = Some("RUNNING"),
        error = None,
        kind = kind,
        containers = nodeNames
          .map(
            nodeName =>
              ContainerInfo(
                nodeName = nodeName,
                id = CommonUtils.randomString(10),
                imageName = imageName,
                state = ContainerState.RUNNING.name,
                kind = "FAKE",
                name = CommonUtils.randomString(10),
                size = -1,
                portMappings = ports.map(p => PortMapping("fake", p, p)).toSeq,
                environments = Map.empty,
                hostname = CommonUtils.randomString(10)
              )
          )
          .toSeq
      )
    )

  override def exist(objectKey: ObjectKey)(implicit executionContext: ExecutionContext): Future[Boolean] =
    Future.successful(clusterCache.keySet.asScala.contains(objectKey))

  override protected def doRemove(clusterInfo: ClusterStatus, beRemovedContainer: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[Boolean] = {
    val previous = clusterCache.get(clusterInfo.key)
    if (previous == null) Future.successful(false)
    else {
      val newContainers =
        previous.containers.filterNot(container => beRemovedContainer.exists(_.name == container.name))
      if (newContainers.isEmpty) clusterCache.remove(clusterInfo.key)
      else clusterCache.put(previous.key, previous.copy(containers = newContainers))
      // return true if it does remove something
      Future.successful(newContainers.size != previous.containers.size)
    }
  }

  override def logs(objectKey: ObjectKey, sinceSeconds: Option[Long])(
    implicit executionContext: ExecutionContext
  ): Future[Map[ContainerInfo, String]] =
    exist(objectKey).flatMap(if (_) Future.successful {
      val containers = clusterCache.asScala.find(_._1 == objectKey).get._2.containers
      containers.map(_ -> "fake log").toMap
    } else Future.failed(new NoSuchClusterException(s"$objectKey doesn't exist")))

  override def clusters()(
    implicit executionContext: ExecutionContext
  ): Future[Seq[ClusterStatus]] =
    Future.successful(clusterCache.asScala.values.toSeq)

  private[this] val _forceRemoveCount = new AtomicInteger(0)
  override protected def doForceRemove(clusterInfo: ClusterStatus, containerInfos: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[Boolean] =
    try doRemove(clusterInfo, containerInfos)
    finally _forceRemoveCount.incrementAndGet()

  // In fake mode, the cluster state should be running since we add "running containers" always
  override protected def toClusterState(containers: Seq[ContainerInfo]): Option[ServiceState] =
    Some(ServiceState.RUNNING)

  def forceRemoveCount: Int = _forceRemoveCount.get()
}
