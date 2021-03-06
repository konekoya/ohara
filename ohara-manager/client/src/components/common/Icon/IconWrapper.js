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

import { cloneElement, forwardRef } from 'react';
import PropTypes from 'prop-types';
import styled, { css } from 'styled-components';

const StyledIcon = styled(({ children, ...props }) =>
  cloneElement(children, props),
)(
  ({ theme, severity }) => css`
    color: ${theme.palette[severity].main};
  `,
);

export const IconWrapper = forwardRef(({ children, severity }, ref) => {
  if (severity) {
    return <StyledIcon children={children} severity={severity} />;
  }
  return cloneElement(children, { ref });
});

IconWrapper.propTypes = {
  children: PropTypes.element.isRequired,
  // The severity of the icon. If provided, the color of the icon will be overwritten.
  severity: PropTypes.oneOf(['info', 'success', 'warning', 'error']),
};
