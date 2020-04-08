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

import _ from 'lodash';
import { normalize } from 'normalizr';
import { ofType } from 'redux-observable';
import { from, of } from 'rxjs';
import { catchError, map, startWith, switchMap } from 'rxjs/operators';

import * as streamApi from 'api/streamApi';
import * as actions from 'store/actions';
import * as schema from 'store/schema';

export default action$ =>
  action$.pipe(
    ofType(actions.updateStream.TRIGGER),
    map(action => action.payload),
    switchMap(({ values, options }) => {
      return from(streamApi.update(values)).pipe(
        map(res => normalize(res.data, schema.stream)),
        map(normalizedData => {
          handleSuccess(values, options);
          return actions.updateStream.success(normalizedData);
        }),
        startWith(actions.updateStream.request()),
        catchError(err => of(actions.updateStream.failure(err))),
      );
    }),
  );

function handleSuccess(values, options) {
  const { paperApi, cell, topics, currentStreams } = options;
  const cells = paperApi.getCells();

  const currentStream = currentStreams.find(
    stream => stream.name === values.name,
  );

  const hasTo = _.get(values, 'to', []).length > 0;
  const hasFrom = _.get(values, 'from', []).length > 0;
  const currentHasTo = _.get(currentStream, 'to', []).length > 0;
  const currentHasFrom = _.get(currentStream, 'from', []).length > 0;

  if (currentHasTo) {
    const streamId = paperApi.getCell(values.name).id;
    const topicId = paperApi.getCell(currentStream.to[0].name).id;
    const linkId = cells
      .filter(cell => cell.cellType === 'standard.Link')
      .find(cell => cell.sourceId === streamId && cell.targetId === topicId).id;
    paperApi.removeLink(linkId);
  }

  if (currentHasFrom) {
    const streamId = paperApi.getCell(values.name).id;
    const topicId = paperApi.getCell(currentStream.from[0].name).id;
    const linkId = cells
      .filter(cell => cell.cellType === 'standard.Link')
      .find(cell => cell.sourceId === topicId && cell.targetId === streamId).id;

    paperApi.removeLink(linkId);
  }

  if (hasTo) {
    paperApi.addLink(cell.id, topics.find(topic => topic.key === 'to').data.id);
  }

  if (hasFrom) {
    paperApi.addLink(
      topics.find(topic => topic.key === 'from').data.id,
      cell.id,
    );
  }
}