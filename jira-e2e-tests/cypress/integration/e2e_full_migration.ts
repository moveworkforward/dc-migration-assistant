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

/// <reference types="Cypress" />

import type { AWSCredentials } from '../support/common'

import { waitForProvisioning } from '../support/pages/ProvisioningPage';
import { getContext, validate_issue } from '../support/jira';
import {
    configureQuickStartFormWithoutVPC,
    submitQuickstartForm,
} from '../support/pages/QuickstartForm';
import { startMigration } from '../support/pages/LandingPage';
import { selectPrefixOnASIPage } from '../support/pages/SelectAsiPage';
import { fillCrendetialsOnAuthPage } from '../support/pages/AwsAuthPage';
import {
    startFileSystemInitialMigration,
    monitorFileSystemMigration,
} from '../support/pages/FileSystemMigration';
import { showsBlockUserWarning, continueWithMigration } from '../support/pages/BlockUsersPage';
import { runFinalSync, monitorFinalSync } from '../support/pages/FinalSync';
import { showsValidationPage } from '../support/pages/ValidationPage';

const shouldReset = true;

const getAwsTokens = (): AWSCredentials => {
    return {
        keyId: Cypress.env('AWS_ACCESS_KEY_ID'),
        secretKey: Cypress.env('AWS_SECRET_ACCESS_KEY'),
    };
};

describe('Migration plugin', () => {
    const ctx = getContext();
    const region = 'ap-southeast-2';
    const testId = Math.random().toString(36).substring(2, 8);
    const credentials = getAwsTokens();

    before(() => {
        cy.on('uncaught:exception', (err, runnable) => false);
        expect(credentials.keyId, 'Set AWS_ACCESS_KEY_ID, see README.md').to.not.be.undefined;

        Cypress.Cookies.defaults({ whitelist: ['JSESSIONID', 'atlassian.xsrf.token'] });
        cy.jira_login(ctx);

        if (shouldReset) {
            cy.reset_migration(ctx);
        }
    });

    beforeEach(() => {
        // cy.visit(ctx.pluginFullUrl);
    });

    afterEach(function () {
        if (this.currentTest.state === 'failed') {
            Cypress.runner.stop();
        }
    });

    it.skip('runs full migration', () => {
        startMigration(ctx);

        fillCrendetialsOnAuthPage(ctx, region, credentials);

        selectPrefixOnASIPage(ctx);

        configureQuickStartFormWithoutVPC(ctx, {
            stackName: `teststack-${testId}`,
            dbPassword: `XadD54^${testId}`,
            dbMasterPassword: `YadD54^${testId}`,
        });

        submitQuickstartForm();

        waitForProvisioning(ctx);

        startFileSystemInitialMigration(ctx);

        monitorFileSystemMigration(ctx);

        showsBlockUserWarning();
        continueWithMigration();

        runFinalSync();
        monitorFinalSync(ctx);

        showsValidationPage();
    });

    it('starts migration', () => {
        startMigration(ctx);
    });

    it('fills credentials', () => {
        fillCrendetialsOnAuthPage(ctx, region, credentials);
        selectPrefixOnASIPage(ctx);
    });

    it('configures AWS Quickstart', () => {
        configureQuickStartFormWithoutVPC(ctx, {
            stackName: `teststack-${testId}`,
            dbPassword: `XadD54^${testId}`,
            dbMasterPassword: `YadD54^${testId}`,
        });
        submitQuickstartForm();
    });

    it('waits for provisioning', () => {
        waitForProvisioning(ctx);
    });

    it.skip('starts and monitor filesystem', () => {
        startFileSystemInitialMigration(ctx);
        monitorFileSystemMigration(ctx);
    });

    it('show warning to block access access', () => {
        showsBlockUserWarning();
        continueWithMigration();
    });

    it('runs final sync', () => {
        runFinalSync();
        monitorFinalSync(ctx);
    });

    let serviceURL: string
    it('shows validation page after migration finishes', () => {
        let serviceURL = showsValidationPage();
    });

    it('Validate issues with inline attachment', () => {
        validate_issue("TEST-17", ctx, serviceURL, "vbackground.png", "3393ce5431bcb31aea66541c3a1c6a56", "43ef675cf099a8d5108b1de45e221dac")
    });

    it('Validate issues with large attachment', () => {
        validate_issue("TEST-18", ctx, serviceURL, "random.bin", "cdb8239c10b894beef502af29eaa3cf1", null)
    });

});
