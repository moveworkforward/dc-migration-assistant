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

package com.atlassian.migration.datacenter.core.aws.db;

import com.atlassian.migration.datacenter.core.aws.db.restore.SsmPsqlDatabaseRestoreService;
import com.atlassian.migration.datacenter.core.aws.infrastructure.AWSMigrationHelperDeploymentService;
import com.atlassian.migration.datacenter.core.db.DatabaseMigrationJobRunner;
import com.atlassian.migration.datacenter.core.fs.reporting.DefaultFileSystemMigrationReport;
import com.atlassian.migration.datacenter.core.util.MigrationRunner;
import com.atlassian.migration.datacenter.spi.MigrationService;
import com.atlassian.migration.datacenter.spi.MigrationStage;
import com.atlassian.scheduler.config.JobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatabaseMigrationServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    MigrationService migrationService;

    @Mock
    MigrationRunner migrationRunner;

    @Mock
    AWSMigrationHelperDeploymentService awsMigrationHelperDeploymentService;

    @Mock
    SsmPsqlDatabaseRestoreService restoreService;

    @Mock
    DatabaseArchivalService databaseArchivalService;

    @Mock
    DatabaseArtifactS3UploadService s3UploadService;

    DatabaseMigrationService sut;

    @BeforeEach
    void setUp() {
        sut = new DatabaseMigrationService(
                tempDir,
                migrationService,
                migrationRunner,
                databaseArchivalService,
                s3UploadService,
                restoreService,
                awsMigrationHelperDeploymentService);
    }

    @Test
    @Disabled("Everything that calls the transition callbacks is mocked")
    void databaseMigrationShouldExecuteCorrectTransitions() throws Exception {
        final String s3bucket = "s3bucket";
        final Path filePath = Paths.get("path");
        final DefaultFileSystemMigrationReport report = new DefaultFileSystemMigrationReport();

        InOrder inOrder = inOrder(migrationService);
        when(awsMigrationHelperDeploymentService.getMigrationS3BucketName()).thenReturn(s3bucket);
        when(databaseArchivalService.archiveDatabase(eq(tempDir))).thenReturn(filePath);
        when(s3UploadService.upload(eq(filePath), eq(s3bucket))).thenReturn(report);
        sut.performMigration();

        inOrder.verify(migrationService).transition(MigrationStage.DB_MIGRATION_EXPORT);
        inOrder.verify(migrationService).transition(MigrationStage.DB_MIGRATION_EXPORT_WAIT);
        inOrder.verify(migrationService).transition(MigrationStage.DB_MIGRATION_UPLOAD);
        inOrder.verify(migrationService).transition(MigrationStage.DB_MIGRATION_UPLOAD_WAIT);
        inOrder.verify(migrationService).transition(MigrationStage.DATA_MIGRATION_IMPORT);
        inOrder.verify(migrationService).transition(MigrationStage.VALIDATE);
        verifyNoMoreInteractions(migrationService);
    }

    @Test
    public void shouldUnscheduleMigrationGivenMigrationId(){
        int migrationId = 42;

        sut.unscheduleMigration(migrationId);

        verify(migrationRunner).abortJobIfPresent(argThat(argument -> argument.equals(JobId.of(DatabaseMigrationJobRunner.KEY + migrationId))));

    }

}
