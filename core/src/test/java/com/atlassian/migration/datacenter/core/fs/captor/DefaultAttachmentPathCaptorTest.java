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

package com.atlassian.migration.datacenter.core.fs.captor;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.test.TestActiveObjects;
import com.atlassian.migration.datacenter.dto.Migration;
import com.atlassian.migration.datacenter.spi.MigrationService;
import net.java.ao.EntityManager;
import net.java.ao.test.junit.ActiveObjectsJUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.file.Paths;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(ActiveObjectsJUnitRunner.class)
public class DefaultAttachmentPathCaptorTest {

    private ActiveObjects ao;
    private EntityManager entityManager;

    private DefaultAttachmentPathCaptor sut;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    MigrationService migrationService;

    @Before
    public void setup() {
        assertNotNull(entityManager);
        ao = new TestActiveObjects(entityManager);
        sut = new DefaultAttachmentPathCaptor(ao, migrationService);
        setupEntities();
    }

    private void setupEntities() {
        ao.migrate(FileSyncRecord.class);
    }

    @Test
    public void shouldStorePathInActiveObjects() {
        final String testPath = "hello-path";
        sut.captureAttachmentPath(Paths.get(testPath));

        FileSyncRecord record = ao.find(FileSyncRecord.class)[0];

        assertEquals(testPath, record.getFilePath());
    }

    @Test
    public void shouldStoreRecordAgainstCurrentMigration() {
        Migration migration = ao.create(Migration.class);
        migration.save();

        when(migrationService.getCurrentMigration()).thenReturn(migration);

        sut.captureAttachmentPath(Paths.get("test"));

        FileSyncRecord record = ao.find(FileSyncRecord.class)[0];

        assertEquals(migration, record.getMigration());
    }

    @Test
    public void shouldRetrieveAllCapturedFilePaths() {
        FileSyncRecord record = ao.create(FileSyncRecord.class);
        final String pathOne = "pathOne";
        record.setFilePath(pathOne);
        record.save();

        FileSyncRecord anotherRecord = ao.create(FileSyncRecord.class);
        final String pathTwo = "pathTwo";
        anotherRecord.setFilePath(pathTwo);
        anotherRecord.save();

        Set<FileSyncRecord> records = sut.getCapturedAttachments();

        assertThat(records, contains(
                record,
                anotherRecord
        ));
    }
}