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
package com.crediblex.fineract.portfolio.loanaccount.service;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleAssembler;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationTransitionValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanApplicationWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.service.CalendarReadPlatformService;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.GSIMReadPlatformService;
import org.apache.fineract.portfolio.account.domain.AccountAssociationsRepository;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom implementation of LoanApplicationWritePlatformService that handles CredibleX-specific fields.
 */
@Service
@Primary
public class CustomLoanApplicationWritePlatformServiceJpaRepositoryImpl extends LoanApplicationWritePlatformServiceJpaRepositoryImpl {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public CustomLoanApplicationWritePlatformServiceJpaRepositoryImpl(
            PlatformSecurityContext context,
            LoanApplicationTransitionValidator loanApplicationTransitionValidator,
            LoanApplicationValidator loanApplicationValidator,
            LoanRepositoryWrapper loanRepositoryWrapper,
            NoteRepository noteRepository,
            LoanAssembler loanAssembler,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            CalendarRepository calendarRepository,
            CalendarInstanceRepository calendarInstanceRepository,
            SavingsAccountRepositoryWrapper savingsAccountRepository,
            AccountAssociationsRepository accountAssociationsRepository,
            BusinessEventNotifierService businessEventNotifierService,
            LoanScheduleAssembler loanScheduleAssembler,
            LoanUtilService loanUtilService,
            CalendarReadPlatformService calendarReadPlatformService,
            EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService,
            GLIMAccountInfoRepository glimRepository,
            LoanRepository loanRepository,
            GSIMReadPlatformService gsimReadPlatformService,
            LoanLifecycleStateMachine defaultLoanLifecycleStateMachine,
            LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator,
            LoanScheduleService loanScheduleService) {
        super(context, loanApplicationTransitionValidator, loanApplicationValidator, loanRepositoryWrapper, noteRepository, loanAssembler,
                loanRepaymentScheduleTransactionProcessorFactory, calendarRepository, calendarInstanceRepository, savingsAccountRepository,
                accountAssociationsRepository, businessEventNotifierService, loanScheduleAssembler, loanUtilService, calendarReadPlatformService,
                entityDatatableChecksWritePlatformService, glimRepository, loanRepository, gsimReadPlatformService, defaultLoanLifecycleStateMachine,
                loanAccrualsProcessingService, loanDownPaymentTransactionValidator, loanScheduleService);
    }

    @Override
    @Transactional
    public CommandProcessingResult submitApplication(final JsonCommand command) {
        // Call the parent implementation first
        CommandProcessingResult result = super.submitApplication(command);
        
        // If creation was successful, update the line_of_credit_id field
        if (result.getResourceId() != null) {
            updateLineOfCreditField(result.getResourceId(), command);
        }
        
        return result;
    }

    @Override
    @Transactional
    public CommandProcessingResult modifyApplication(final Long loanId, final JsonCommand command) {
        // Call the parent implementation first
        CommandProcessingResult result = super.modifyApplication(loanId, command);
        
        // Update the line_of_credit_id field
        updateLineOfCreditField(loanId, command);
        
        return result;
    }

    private void updateLineOfCreditField(Long loanId, JsonCommand command) {
        if (command.parameterExists("lineOfCreditId")) {
            Long lineOfCreditId = command.longValueOfParameterNamed("lineOfCreditId");
            
            // Validate that the line of credit exists if provided
            if (lineOfCreditId != null && !lineOfCreditExists(lineOfCreditId)) {
                throw new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                    "Line of Credit with ID " + lineOfCreditId + " does not exist", "lineOfCreditId", lineOfCreditId);
            }
            
            String sql = "UPDATE m_loan SET line_of_credit_id = ? WHERE id = ?";
            jdbcTemplate.update(sql, lineOfCreditId, loanId);
        }
    }

    private boolean lineOfCreditExists(Long lineOfCreditId) {
        String sql = "SELECT COUNT(*) FROM m_line_of_credit WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, lineOfCreditId);
        return count != null && count > 0;
    }
} 