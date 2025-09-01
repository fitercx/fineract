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


import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;

import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom service that provides Line of Credit functionality for loan application submission.
 * This service can be used to enhance loan applications with line of credit associations.
 */
@Service
@Slf4j
public class LocLoanApplicationWritePlatformServiceJpaRepositoryImpl{


    private final FromJsonHelper fromApiJsonHelper;
    private final LocLoanAssociationService locLoanAssociationService;

    @Autowired
    public LocLoanApplicationWritePlatformServiceJpaRepositoryImpl(
            FromJsonHelper fromApiJsonHelper,
            LocLoanAssociationService locLoanAssociationService) {

        this.fromApiJsonHelper = fromApiJsonHelper;
        this.locLoanAssociationService = locLoanAssociationService;
    }

    /**
     * Process loan application with Line of Credit functionality.
     * This method can be called after a loan application is submitted to associate it with a line of credit.
     * 
     * @param command the JSON command containing loan application data
     * @param loanId the ID of the created loan
     * @return true if line of credit association was successful or not needed, false if it failed
     */
    @Transactional
    public boolean processLoanApplicationWithLineOfCredit(final JsonCommand command, final Long loanId) {
        log.info("Processing loan application {} with Line of Credit support", loanId);
        
        // Extract line of credit information from the command
        Long lineOfCreditId = extractLineOfCreditIdFromCommand(command);
        
        // If we have a line of credit ID, associate it with the loan
        if (lineOfCreditId != null) {
            try {
                boolean success = locLoanAssociationService.associateLoanWithLineOfCredit(loanId, lineOfCreditId);
                if (success) {
                    log.info("Successfully associated loan {} with line of credit {}", loanId, lineOfCreditId);
                    return true;
                } else {
                    log.warn("Failed to associate loan {} with line of credit {} - association returned false", 
                             loanId, lineOfCreditId);
                    return false;
                }
            } catch (Exception e) {
                log.error("Failed to associate loan {} with line of credit {}: {}", 
                         loanId, lineOfCreditId, e.getMessage());
                return false;
            }
        } else {
            log.debug("No line of credit ID found in loan application command for loan {}", loanId);
            return true;
        }
    }

    /**
     * Extract line of credit ID from the JSON command.
     * 
     * @param command the JSON command containing loan application data
     * @return the line of credit ID if present, null otherwise
     */
    private Long extractLineOfCreditIdFromCommand(JsonCommand command) {
        try {
            return fromApiJsonHelper.extractLongNamed("lineOfCreditId", command.parsedJson());
        } catch (Exception e) {
            log.debug("No lineOfCreditId found in loan application command: {}", e.getMessage());
            return null;
        }
    }
}
