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

import { TestScheduler } from 'rxjs/testing';

import clearNotificationsEpic from '../../eventLog/clearNotificationsEpic';
import * as actions from 'store/actions';

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

it('clear notifications should be executed correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a------|';
    const expected = '--(ab)---|';
    const subs = '    ^--------!';

    const action$ = hot(input, {
      a: {
        type: actions.clearNotifications.TRIGGER,
      },
    });
    const output$ = clearNotificationsEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.clearNotifications.SUCCESS,
      },
      b: {
        type: actions.updateNotifications.TRIGGER,
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});
