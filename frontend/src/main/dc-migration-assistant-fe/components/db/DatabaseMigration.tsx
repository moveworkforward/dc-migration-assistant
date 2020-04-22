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

import { I18n } from '@atlassian/wrm-react-i18n';
import {
    MigrationTransferProps,
    MigrationTransferPage,
    Progress,
} from '../shared/MigrationTransferPage';
import { callAppRest } from '../../utils/api';
import { dbStatusReportEndpoint, DatabaseMigrationStatus, toI18nProp } from '../../api/db';

const toProgress = (status: DatabaseMigrationStatus): Progress => {
    return {
        phase: toI18nProp(status.status),
        elapsedTimeSeconds: status.elapsedTime.seconds,
    };
};

const fetchDBMigrationStatus = (): Promise<DatabaseMigrationStatus> => {
    return callAppRest('GET', dbStatusReportEndpoint).then((result: any) => result.json());
};

const getProgressFromStatus = (): Promise<Progress> => {
    return fetchDBMigrationStatus().then(toProgress);
};

const props: MigrationTransferProps = {
    heading: I18n.getText('atlassian.migration.datacenter.db.title'),
    description: I18n.getText('atlassian.migration.datacenter.db.description'),
    nextText: I18n.getText('atlassian.migration.datacenter.fs.nextStep'),
    startMoment: moment(),
    hasStarted: true,
    startMigrationPhase: Promise.resolve,
    getProgress: getProgressFromStatus,
};

export const DatabaseTransferPage: FunctionComponent = () => {
    return <MigrationTransferPage {...props} />;
};
