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

package oharastream.ohara.kafka.connector;

import oharastream.ohara.common.rule.OharaTest;
import oharastream.ohara.common.setting.TopicKey;
import oharastream.ohara.common.util.CommonUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestTopicPartition extends OharaTest {

  @Test(expected = NullPointerException.class)
  public void nullTopic() {
    new TopicPartition(null, 1);
  }

  @Test
  public void testGetter() {
    TopicKey topicKey = TopicKey.of("g", "n");
    int partition = (int) CommonUtils.current();
    TopicPartition topicPartition = new TopicPartition(topicKey, partition);
    Assert.assertEquals(topicKey, topicPartition.topicKey());
    Assert.assertEquals(partition, topicPartition.partition());
  }

  @Test
  public void testEquals() {
    TopicPartition topicPartition =
        new TopicPartition(TopicKey.of("g", "n"), (int) CommonUtils.current());
    Assert.assertEquals(topicPartition, topicPartition);
    Assert.assertEquals(
        topicPartition, new TopicPartition(topicPartition.topicKey(), topicPartition.partition()));
  }

  @Test
  public void testHashCode() {
    TopicPartition topicPartition =
        new TopicPartition(TopicKey.of("g", "n"), (int) CommonUtils.current());
    Assert.assertEquals(topicPartition.hashCode(), topicPartition.hashCode());
    Assert.assertEquals(
        topicPartition.hashCode(),
        new TopicPartition(topicPartition.topicKey(), topicPartition.partition()).hashCode());
  }
}
