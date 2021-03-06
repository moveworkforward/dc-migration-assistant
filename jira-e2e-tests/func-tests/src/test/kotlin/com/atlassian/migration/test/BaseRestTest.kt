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

package com.atlassian.migration.test

import io.restassured.authentication.PreemptiveAuthProvider
import io.restassured.builder.RequestSpecBuilder
import io.restassured.filter.log.LogDetail
import org.junit.jupiter.api.Tag

@Tag("rest")
open class BaseRestTest {
    val username: String = System.getenv("JIRA_USERNAME") ?: "admin"
    val password: String = System.getenv("JIRA_PASSWORD") ?: "admin"

    val baseURI = System.getenv("JIRA_BASE_URL") ?: "http://jira:8080/jira"
    val basePath = System.getenv("JIRA_BASE_PATH") ?: "/rest/dc-migration/1.0"

    val requestSpec = RequestSpecBuilder()
        .log(LogDetail.ALL)
        .setBaseUri(baseURI)
        .setBasePath(basePath)
        .setAuth(PreemptiveAuthProvider().basic(username, password))
        .addParam("os_authType", "basic")
        .build()
}
