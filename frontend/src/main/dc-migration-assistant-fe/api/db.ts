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

import { I18n } from '@atlassian/wrm-react-i18n';
import { MigrationDuration } from './common';

const dbAPIBase = 'migration/db';
export const dbStatusReportEndpoint = `${dbAPIBase}/report`;
export const dbStartEndpoint = `${dbAPIBase}/start`;

export enum DBMigrationStatus {
    NOT_STARTED,
    EXPORTING,
    UPLOADING,
    IMPORTING,
    DONE,
    FAILED,
}

export const statusToI18nString = (status: DBMigrationStatus): string => {
    const name = status.toString().toLowerCase();
    switch (name) {
        case 'failed':
            return I18n.getText('atlassian.migration.datacenter.db.status.failed');
        case 'exporting':
            return I18n.getText('atlassian.migration.datacenter.db.status.exporting');
        case 'uploading':
            return I18n.getText('atlassian.migration.datacenter.db.status.uploading');
        case 'done':
            return I18n.getText('atlassian.migration.datacenter.db.status.done');
        default:
            return I18n.getText('atlassian.migration.datacenter.db.status.unknown');
    }
};

// See DatabaseMigrationProgress.kt
export type DatabaseMigrationStatus = {
    status: DBMigrationStatus;
    elapsedTime: MigrationDuration;
};