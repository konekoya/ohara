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

import { axiosInstance } from './apiUtils';

export const fetchPipelines = async group => {
  try {
    const response = await axiosInstance.get(`/api/pipelines?group=${group}`);
    const { isSuccess } = response.data;

    if (!isSuccess) throw new Error(response.data.errorMessage.message);

    return response;
  } catch (error) {
    throw new Error(error);
  }
};

export const createPipeline = async params => {
  try {
    const response = await axiosInstance.post('/api/pipelines', params);
    const { isSuccess } = response.data;

    if (!isSuccess) throw new Error(response.data.errorMessage.message);

    return response;
  } catch (error) {
    throw new Error(error);
  }
};
