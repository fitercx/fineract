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
package com.crediblex.fineract.test.factory;

import org.apache.fineract.client.models.PostSavingsAccountsRequest;
import org.apache.fineract.client.models.PostSavingsProductsResponse;
import org.apache.fineract.test.factory.SavingsAccountRequestFactory;
import org.apache.fineract.test.support.TestContext;
import org.apache.fineract.test.support.TestContextKey;
import retrofit2.Response;

/**
 * Custom factory that extends the base factory to use dynamically created product IDs
 * instead of hardcoded ones
 */
public class CustomSavingsAccountRequestFactory {

    private CustomSavingsAccountRequestFactory() {}

    public static PostSavingsAccountsRequest defaultEURSavingsAccountRequest() {
        // Get the dynamically created EUR product ID from test context
        Response<PostSavingsProductsResponse> productResponse = TestContext.INSTANCE.get(
            TestContextKey.DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_EUR);
        
        if (productResponse == null || productResponse.body() == null) {
            throw new IllegalStateException("EUR savings product must be created before creating savings account. " +
                "Ensure 'Given A EUR savings product exists' step is executed first.");
        }
        
        Long productId = productResponse.body().getResourceId();
        
        // Use the base factory method and override the product ID
        return SavingsAccountRequestFactory.defaultEURSavingsAccountRequest()
                .productId(productId);
    }

    public static PostSavingsAccountsRequest defaultUSDSavingsAccountRequest() {
        // Get the dynamically created USD product ID from test context
        Response<PostSavingsProductsResponse> productResponse = TestContext.INSTANCE.get(
            TestContextKey.DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_USD);
        
        if (productResponse == null || productResponse.body() == null) {
            throw new IllegalStateException("USD savings product must be created before creating savings account. " +
                "Ensure 'Given A USD savings product exists' step is executed first.");
        }
        
        Long productId = productResponse.body().getResourceId();
        
        // Use the base factory method and override the product ID
        return SavingsAccountRequestFactory.defaultUSDSavingsAccountRequest()
                .productId(productId);
    }
}
