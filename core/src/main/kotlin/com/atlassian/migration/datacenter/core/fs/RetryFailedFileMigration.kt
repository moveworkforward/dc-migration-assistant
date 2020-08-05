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

package com.atlassian.migration.datacenter.core.fs

import com.atlassian.migration.datacenter.core.util.UploadQueue
import com.atlassian.migration.datacenter.spi.fs.FilesystemMigrationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

class RetryFailedFileMigration(private val reportManager: FileSystemMigrationReportManager, private val uploaderFactory: UploaderFactory, private val fsMigrationService: FilesystemMigrationService) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(RetryFailedFileMigration::class.java)
    }

    fun uploadFailedFiles() {
        log.debug("[Retry operation] Aborting current file system migration, if there is a migration in progress")

        fsMigrationService.abortMigration()

        val report = reportManager.getCurrentReport(ReportType.Filesystem) ?: throw Error("No report")
        val newReport = reportManager.resetReport(ReportType.Filesystem)

        val uploadQueue = UploadQueue<Path>(report.failedFiles.size)

        report.failedFiles.forEach {
            uploadQueue.put(it.filePath)
            newReport.reportFileFound()
        }

        val uploader = uploaderFactory.newUploader(report)

        uploader.upload(uploadQueue)
    }

}