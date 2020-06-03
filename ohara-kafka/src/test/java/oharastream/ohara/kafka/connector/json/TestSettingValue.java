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

package oharastream.ohara.kafka.connector.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import oharastream.ohara.common.json.JsonUtils;
import oharastream.ohara.common.rule.OharaTest;
import oharastream.ohara.common.util.CommonUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestSettingValue extends OharaTest {
  @Test
  public void testEqual() throws IOException {
    SettingValue value =
        SettingValue.of(CommonUtils.randomString(), CommonUtils.randomString(), List.of());
    ObjectMapper mapper = JsonUtils.objectMapper();
    Assert.assertEquals(
        value,
        mapper.readValue(mapper.writeValueAsString(value), new TypeReference<SettingValue>() {}));
  }

  @Test
  public void testGetter() {
    String name = CommonUtils.randomString(5);
    String value = CommonUtils.randomString(5);
    String error = CommonUtils.randomString(5);
    SettingValue settingValue = SettingValue.of(name, value, List.of(error));
    Assert.assertEquals(name, settingValue.key());
    Assert.assertEquals(value, settingValue.value());
    Assert.assertEquals(1, settingValue.errors().size());
    Assert.assertEquals(error, settingValue.errors().get(0));
  }

  @Test(expected = NullPointerException.class)
  public void nullName() {
    SettingValue.of(null, CommonUtils.randomString(), List.of());
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyName() {
    SettingValue.of("", CommonUtils.randomString(), List.of());
  }

  @Test
  public void nullValue() {
    SettingValue value = SettingValue.of(CommonUtils.randomString(), null, List.of());
    Assert.assertNull(value.value());
  }

  @Test
  public void emptyValue() {
    SettingValue value = SettingValue.of(CommonUtils.randomString(), "", List.of());
    Assert.assertEquals("", value.value());
  }

  @Test(expected = NullPointerException.class)
  public void nullErrors() {
    SettingValue.of(CommonUtils.randomString(), "", null);
  }

  @Test
  public void emptyError() {
    SettingValue.of(CommonUtils.randomString(), "", List.of());
  }

  @Test
  public void testToString() {
    String name = CommonUtils.randomString(5);
    String value = CommonUtils.randomString(5);
    String error = CommonUtils.randomString(5);
    SettingValue validatedValue = SettingValue.of(name, value, List.of(error));
    Assert.assertTrue(validatedValue.toString().contains(name));
    Assert.assertTrue(validatedValue.toString().contains(value));
    Assert.assertTrue(validatedValue.toString().contains(error));
  }

  @Test
  public void testToValidatedValue() {
    SettingValue value =
        SettingValue.ofJson(
            "{"
                + "\"key\": \"aaaa\","
                + "\"value\": "
                + "\"cccc\","
                + "\"errors\": "
                + "["
                + "\"errrrrrrr\""
                + "]"
                + "}");
    Assert.assertEquals("aaaa", value.key());
    Assert.assertEquals("cccc", value.value());
    Assert.assertEquals(1, value.errors().size());
    Assert.assertEquals("errrrrrrr", value.errors().get(0));
  }
}
