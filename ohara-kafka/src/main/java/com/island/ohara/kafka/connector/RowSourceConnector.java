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

package com.island.ohara.kafka.connector;

import com.island.ohara.common.annotations.VisibleForTesting;
import com.island.ohara.common.setting.SettingDef;
import com.island.ohara.common.setting.WithDefinitions;
import com.island.ohara.common.util.VersionUtils;
import com.island.ohara.kafka.connector.json.ConnectorDefUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.Config;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectorContext;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

/** A wrap to SourceConnector. Currently, only Task is replaced by ohara object - RowSourceTask */
public abstract class RowSourceConnector extends SourceConnector implements WithDefinitions {

  /**
   * Returns the RowSourceTask implementation for this Connector.
   *
   * @return a RowSourceTask class
   */
  protected abstract Class<? extends RowSourceTask> _taskClass();

  /**
   * Return the settings for source task.
   *
   * @param maxTasks number of tasks for this connector
   * @return a seq from settings
   */
  protected abstract List<TaskSetting> _taskSettings(int maxTasks);

  /**
   * Start this Connector. This method will only be called on a clean Connector, i.e. it has either
   * just been instantiated and initialized or _stop() has been invoked.
   *
   * @param config configuration settings
   */
  protected abstract void _start(TaskSetting config);

  /** stop this connector */
  protected abstract void _stop();

  /**
   * Define the configuration for the connector.
   *
   * @return The ConfigDef for this connector.
   */
  protected Map<String, SettingDef> customSettingDefinitions() {
    return Collections.emptyMap();
  }

  /**
   * create counter builder. This is a helper method for custom connector which want to expose some
   * number via ohara's metrics. NOTED: THIS METHOD MUST BE USED AFTER STARTING THIS CONNECTOR.
   * otherwise, an IllegalArgumentException will be thrown.
   *
   * @return counter
   */
  protected CounterBuilder counterBuilder() {
    if (taskSetting == null)
      throw new IllegalArgumentException("you can't create a counter before starting connector");
    return CounterBuilder.of().group(taskSetting.name());
  }

  /**
   * the "column" describes the serialization ruleS of input/output data. However, not all
   * connectors count on it. Keeping a complicated but useless definition is weird to users. Hence,
   * we offer a way to "remove" it from connectors.
   *
   * @return true if your connector counts on column. Otherwise, false.
   */
  protected boolean needColumnDefinition() {
    return true;
  }

  @Override
  public final String version() {
    return java.util.Optional.ofNullable(settingDefinitions().get(VERSION_KEY))
        .map(SettingDef::defaultString)
        .orElse(VersionUtils.VERSION);
  }

  // -------------------------------------------------[WRAPPED]-------------------------------------------------//

  @Override
  public List<Map<String, String>> taskConfigs(int maxTasks) {
    return _taskSettings(maxTasks).stream().map(TaskSetting::raw).collect(Collectors.toList());
  }

  @Override
  public final Class<? extends Task> taskClass() {
    return _taskClass();
  }

  @VisibleForTesting TaskSetting taskSetting = null;

  @Override
  public final void start(Map<String, String> props) {
    taskSetting = TaskSetting.of(Collections.unmodifiableMap(props));
    _start(taskSetting);
  }

  @Override
  public final void stop() {
    _stop();
  }

  /** @return custom definitions + core definitions */
  @Override
  public final Map<String, SettingDef> settingDefinitions() {
    return WithDefinitions.merge(
        this,
        ConnectorDefUtils.DEFAULT.entrySet().stream()
            .filter(
                entry ->
                    needColumnDefinition()
                        || entry.getValue() != ConnectorDefUtils.COLUMNS_DEFINITION)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
        customSettingDefinitions());
  }

  @Override
  public final ConfigDef config() {
    return ConnectorUtils.toConfigDef(settingDefinitions().values());
  }
  // -------------------------------------------------[UN-OVERRIDE]-------------------------------------------------//
  @Override
  public final void initialize(ConnectorContext ctx) {
    super.initialize(ctx);
  }

  @Override
  public final void initialize(ConnectorContext ctx, List<Map<String, String>> taskConfigs) {
    super.initialize(ctx, taskConfigs);
  }

  @Override
  public final void reconfigure(Map<String, String> props) {
    super.reconfigure(props);
  }

  @Override
  public final Config validate(Map<String, String> connectorConfigs) {
    return super.validate(connectorConfigs);
  }
}
