package com.atlassian.migration.datacenter.core.fs.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultFileSystemMigrationProgressTest {

    private DefaultFilesystemMigrationProgress sut;

    @BeforeEach
    void setUp() {
        sut = new DefaultFilesystemMigrationProgress();
    }

    @Test
    void shouldBeInitialisedWithNoCompleteFiles() {
        assertTrue(sut.getMigratedFiles().isEmpty(), "expected migrated files list of new progress to be empty");
    }

    @Test
    void shouldAddMigratedFileToMigratedFiles() {
        final Path testFile = Paths.get("file");
        sut.reportFileMigrated(testFile);

        assertEquals(1, sut.getMigratedFiles().size());

        assertTrue(sut.getMigratedFiles().contains(testFile));
    }

    @Test
    void shouldCountMigratingFile() {
        sut.reportFileFound();

        assertEquals(1, sut.getNumberOfFilesFound());
    }

    @Test
    void shouldCountInFlightFile() {
        sut.reportFileInFlight();

        assertEquals(1, sut.getNumberOfFilesInFlight());
    }

    @Test
    void ShouldRemoveFileFromInFlightWhenItIsMigrated() {
        sut.reportFileInFlight();
        sut.reportFileInFlight();

        assertEquals(2, sut.getNumberOfFilesInFlight());

        sut.reportFileMigrated(Paths.get("test"));

        assertEquals(1, sut.getNumberOfFilesInFlight());
    }
}
