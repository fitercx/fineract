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

package com.crediblex.fineract.integrationtests.client;

import com.crediblex.fineract.integrationtests.common.ApiTestBase;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

/**
 * Test client for Client Account Details API endpoints
 */
@Component
public class ClientAccountDetailsTestClient extends ApiTestBase {

    private static final String CLIENT_ACCOUNTS_URL = "/clients/{clientId}/accounts";

    /**
     * Get client account details
     *
     * @param clientId
     *            The client ID
     * @return Response containing client account details
     */
    public Response getClientAccountDetails(Long clientId) {
        return getRequestSpec().pathParam("clientId", clientId).when().get(CLIENT_ACCOUNTS_URL).then().extract().response();
    }

    /**
     * Get client account details with specific fields
     *
     * @param clientId
     *            The client ID
     * @param fields
     *            Comma-separated list of fields to include
     * @return Response containing client account details
     */
    public Response getClientAccountDetailsWithFields(Long clientId, String fields) {
        return getRequestSpec().pathParam("clientId", clientId).queryParam("fields", fields).when().get(CLIENT_ACCOUNTS_URL).then()
                .extract().response();
    }
}
