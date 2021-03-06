/*
 * Copyright 2020 Atlassian
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

import React, { FunctionComponent } from 'react';
import SectionMessage from '@atlaskit/section-message';
import styled from 'styled-components';

import { I18n } from '@atlassian/wrm-react-i18n';
import { CommandDetails as CommandResult } from '../../api/final-sync';

const ErrorFragment = styled.div`
    margin-top: 0px;
`;

type CommandResultProps = {
    result: CommandResult;
};

export const MigrationErrorSection: FunctionComponent<CommandResultProps> = ({
    result: commandResult,
}) => {
    return (
        <ErrorFragment>
            <SectionMessage
                appearance={commandResult?.criticalError ? 'error' : 'warning'}
                title={
                    commandResult?.criticalError
                        ? I18n.getText('atlassian.migration.datacenter.db.error.title')
                        : I18n.getText('atlassian.migration.datacenter.db.warning.title')
                }
            >
                <p>{commandResult.errorMessage}</p>
                <p>
                    {commandResult?.criticalError
                        ? I18n.getText('atlassian.migration.datacenter.db.retry.error')
                        : I18n.getText('atlassian.migration.datacenter.db.error.warning')}
                </p>
                <p>
                    <a href={commandResult.consoleUrl} target="_blank" rel="noopener noreferrer">
                        {I18n.getText('atlassian.migration.datacenter.db.error.s3link')}
                    </a>
                </p>
            </SectionMessage>
        </ErrorFragment>
    );
};
