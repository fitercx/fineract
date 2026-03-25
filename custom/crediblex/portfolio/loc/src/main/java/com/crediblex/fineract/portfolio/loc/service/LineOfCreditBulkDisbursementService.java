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

package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementRequest;
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementResponse;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

/**
 * Service interface for bulk loan disbursement operations under a Line of Credit.
 */
public interface LineOfCreditBulkDisbursementService {

    /**
     * Performs bulk disbursement of multiple loans under a Line of Credit.
     *
     * @param lineOfCreditId
     *            The ID of the Line of Credit
     * @param clientId
     *            The ID of the client
     * @param request
     *            The bulk disbursement request containing loan IDs and parameters
     * @return Response containing results for each loan disbursement
     */
    BulkLoanDisbursementResponse bulkDisburseLoansByLineOfCredit(Long lineOfCreditId, Long clientId, BulkLoanDisbursementRequest request);

    /**
     * Validates the bulk disbursement request before processing.
     *
     * @param lineOfCreditId
     *            The ID of the Line of Credit
     * @param clientId
     *            The ID of the client
     * @param request
     *            The bulk disbursement request to validate
     * @throws PlatformApiDataValidationException
     *             if validation fails
     */
    void validateBulkDisbursementRequest(Long lineOfCreditId, Long clientId, BulkLoanDisbursementRequest request);
}
