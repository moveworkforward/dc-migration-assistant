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

import io.restassured.RestAssured
import org.junit.jupiter.api.Tag

@Tag("rest")
open class BaseRestTest {
    private val username: String = System.getProperty("JIRA_USERNAME", "admin")
    private val password: String = System.getProperty("JIRA_PASSWORD", "admin")

    init {
        RestAssured.authentication = RestAssured.basic(username, password)
        RestAssured.baseURI = System.getProperty("JIRA_BASE_URL", "http://localhost:2990/jira")
        RestAssured.basePath = System.getProperty("JIRA_BASE_PATH", "/rest/dc-migration/1.0")
    }
}