/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.integrationtests.common;

import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for Office-related API operations
 */
public class OfficeHelper extends ApiTestBase {

    private static final String OFFICES_URL = "/offices";

    /**
     * Get all offices
     */
    public static Response getOffices() {
        return getRequestSpec().when().get(OFFICES_URL).then().extract().response();
    }

    /**
     * Get office by ID
     */
    public static Response getOffice(Long officeId) {
        return getRequestSpec().when().get(OFFICES_URL + "/" + officeId).then().extract().response();
    }

    /**
     * Create an office
     */
    public static Response createOffice(Map<String, Object> officeData) {
        return getRequestSpec().body(officeData).when().post(OFFICES_URL).then().extract().response();
    }

    /**
     * Create a basic office
     */
    public static Response createBasicOffice(String name, Long parentId) {
        Map<String, Object> officeData = new HashMap<>();
        officeData.put("name", name);
        officeData.put("parentId", parentId);
        officeData.put("openingDate", "01 January 2023");
        officeData.put("dateFormat", "dd MMMM yyyy");
        officeData.put("locale", "en");

        return createOffice(officeData);
    }

    /**
     * Get first available office ID (usually head office)
     */
    public static Long getHeadOfficeId() {
        Response response = getOffices();
        if (response.getStatusCode() == 200) {
            // Head office typically has parentId as null
            return response.jsonPath().getLong("find { it.parentId == null }.id");
        }
        return 1L; // Default head office ID
    }

    /**
     * Extract office ID from create response
     */
    public static Long extractOfficeId(Response createResponse) {
        return createResponse.jsonPath().getLong("officeId");
    }
}
