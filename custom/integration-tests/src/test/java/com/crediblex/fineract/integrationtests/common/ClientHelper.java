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
import java.util.List;
import java.util.Map;

/**
 * Helper class for Client-related API operations
 */
public class ClientHelper extends ApiTestBase {

    private static final String CLIENTS_URL = "/clients";

    /**
     * Create a new client
     */
    public static Response createClient(Map<String, Object> clientData) {
        return getRequestSpec().body(clientData).when().post(CLIENTS_URL).then().spec(getResponseSpec()).extract().response();
    }

    /**
     * Create a basic individual client
     */
    public static Response createBasicClient(String firstname, String lastname, Long officeId) {
        Map<String, Object> clientData = new HashMap<>();
        clientData.put("officeId", officeId);
        clientData.put("firstname", firstname);
        clientData.put("lastname", lastname);
        clientData.put("dateFormat", "dd MMMM yyyy");
        clientData.put("locale", "en");
        clientData.put("active", true);
        clientData.put("activationDate", "01 January 2023");

        return createClient(clientData);
    }

    /**
     * Get client by ID
     */
    public static Response getClient(Long clientId) {
        return getRequestSpec().when().get(CLIENTS_URL + "/" + clientId).then().extract().response();
    }

    /**
     * Get client accounts
     */
    public static Response getClientAccounts(Long clientId) {
        return getRequestSpec().when().get(CLIENTS_URL + "/" + clientId + "/accounts").then().extract().response();
    }

    /**
     * Verify if client exists
     */
    public static boolean clientExists(Long clientId) {
        Response response = getClient(clientId);
        return response.getStatusCode() == 200;
    }

    /**
     * Extract client ID from create response
     */
    public static Long extractClientId(Response createResponse) {
        return createResponse.jsonPath().getLong("clientId");
    }

    /**
     * Get active loan accounts for a client
     */
    public static List<Map<String, Object>> getActiveLoanAccounts(Long clientId) {
        Response response = getClientAccounts(clientId);
        if (response.getStatusCode() != 200) {
            return null;
        }

        List<Map<String, Object>> loanAccounts = response.jsonPath().getList("loanAccounts");
        return loanAccounts != null ? loanAccounts : List.of();
    }

    /**
     * Get savings accounts for a client
     */
    public static List<Map<String, Object>> getSavingsAccounts(Long clientId) {
        Response response = getClientAccounts(clientId);
        if (response.getStatusCode() != 200) {
            return null;
        }

        List<Map<String, Object>> savingsAccounts = response.jsonPath().getList("savingsAccounts");
        return savingsAccounts != null ? savingsAccounts : List.of();
    }
}
