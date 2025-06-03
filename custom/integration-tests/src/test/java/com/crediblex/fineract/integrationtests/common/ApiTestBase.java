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

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpStatus;

/**
 * Base helper class for all API tests. Provides common functionality for API testing.
 */
public class ApiTestBase {

    private static final String DEFAULT_TENANT = "default";
    private static RequestSpecification requestSpec;
    private static ResponseSpecification responseSpec;
    private static String authToken;

    public static void initializeRestAssured(String baseUrl, String tenant) {
        RestAssured.baseURI = baseUrl;
        RestAssured.basePath = "/fineract-provider/api/v1";

        // Disable SSL verification for local testing (NOT for production!)
        if (baseUrl.startsWith("https://localhost") || baseUrl.startsWith("https://127.0.0.1")) {
            RestAssured.useRelaxedHTTPSValidation();
        }

        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("Fineract-Platform-TenantId", tenant).build();

        responseSpec = new ResponseSpecBuilder().expectContentType(ContentType.JSON).build();
    }

    public static void initializeRestAssured() {
        String baseUrl = System.getProperty("test.server.url", "https://localhost:8443");
        initializeRestAssured(baseUrl, DEFAULT_TENANT);
    }

    public static RequestSpecification getRequestSpec() {
        if (authToken != null) {
            return RestAssured.given().spec(requestSpec).header("Authorization", "Bearer " + authToken);
        }
        return RestAssured.given().spec(requestSpec);
    }

    public static ResponseSpecification getResponseSpec() {
        return responseSpec;
    }

    public static ResponseSpecification getResponseSpec(int statusCode) {
        return new ResponseSpecBuilder().expectStatusCode(statusCode).expectContentType(ContentType.JSON).build();
    }

    /**
     * Authenticate and store the auth token
     */
    public static void authenticate(String username, String password) {
        Map<String, Object> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        Response response = RestAssured.given().spec(requestSpec).body(loginRequest).when().post("/authentication").then()
                .statusCode(HttpStatus.SC_OK).extract().response();

        JsonPath jsonPath = response.jsonPath();
        authToken = jsonPath.getString("base64EncodedAuthenticationKey");

        // Update request spec with auth token
        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("Fineract-Platform-TenantId", DEFAULT_TENANT)
                .addHeader("Authorization", "Basic " + authToken).build();
    }

    /**
     * Authenticate with default credentials
     */
    public static void authenticate() {
        String username = System.getProperty("test.server.username", "mifos");
        String password = System.getProperty("test.server.password", "password");
        authenticate(username, password);
    }

    /**
     * Extract value from response using JsonPath
     */
    public static <T> T extractFromResponse(Response response, String jsonPath) {
        return response.jsonPath().get(jsonPath);
    }

    /**
     * Check if a field exists in the response
     */
    public static boolean fieldExists(Response response, String jsonPath) {
        try {
            Object value = response.jsonPath().get(jsonPath);
            return value != null;
        } catch (Exception e) {
            return false;
        }
    }
}
