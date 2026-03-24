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
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementRequest.SingleLoanDisbursementRequest;
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementResponse;
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementResponse.BulkDisbursementStatus;
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementResponse.SingleLoanDisbursementResult;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.commands.service.SynchronousCommandProcessingService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.IdempotentCommandProcessSucceedException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of bulk loan disbursement service for Line of Credit loans. This service allows disbursing multiple
 * approved loans under a single LOC in one operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineOfCreditBulkDisbursementServiceImpl implements LineOfCreditBulkDisbursementService {

    private final PlatformSecurityContext securityContext;
    private final LineOfCreditRepositoryWrapper lineOfCreditRepositoryWrapper;
    private final JdbcTemplate jdbcTemplate;
    private final FineractRequestContextHolder fineractRequestContextHolder;

    @Qualifier("portfolioCommandSourceWritePlatformServiceImpl")
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Override
    @Transactional
    public BulkLoanDisbursementResponse bulkDisburseLoansByLineOfCredit(Long lineOfCreditId, Long clientId,
            BulkLoanDisbursementRequest request) {

        securityContext.authenticatedUser();

        // Validate the request first
        validateBulkDisbursementRequest(lineOfCreditId, clientId, request);

        // Get the Line of Credit
        LineOfCredit lineOfCredit = lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(lineOfCreditId);

        // Process each loan disbursement
        List<SingleLoanDisbursementResult> results = new ArrayList<>();
        BigDecimal totalAmountDisbursed = BigDecimal.ZERO;
        int successCount = 0;
        int failureCount = 0;

        for (SingleLoanDisbursementRequest loanRequest : request.getLoans()) {
            SingleLoanDisbursementResult result = disburseSingleLoan(lineOfCreditId, clientId, loanRequest, request);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
                if (result.getAmountDisbursed() != null) {
                    totalAmountDisbursed = totalAmountDisbursed.add(result.getAmountDisbursed());
                }
            } else {
                failureCount++;
            }
        }

        // Determine overall status
        BulkDisbursementStatus status;
        if (failureCount == 0) {
            status = BulkDisbursementStatus.COMPLETE;
        } else if (successCount == 0) {
            status = BulkDisbursementStatus.FAILED;
        } else {
            status = BulkDisbursementStatus.PARTIAL;
        }

        log.info("Bulk disbursement completed for LOC {}: {} successful, {} failed out of {} total", lineOfCreditId, successCount,
                failureCount, request.getLoans().size());

        return BulkLoanDisbursementResponse.builder().lineOfCreditId(lineOfCreditId).totalRequested(request.getLoans().size())
                .totalSuccessful(successCount).totalFailed(failureCount).totalAmountDisbursed(totalAmountDisbursed).status(status)
                .loanResults(results).build();
    }

    @Override
    public void validateBulkDisbursementRequest(Long lineOfCreditId, Long clientId, BulkLoanDisbursementRequest request) {

        List<ApiParameterError> errors = new ArrayList<>();

        // Validate request is not null and has loans
        if (request == null) {
            errors.add(ApiParameterError.parameterError("error.msg.bulk.disbursement.request.null",
                    "Bulk disbursement request cannot be null", "request"));
            throw new PlatformApiDataValidationException(errors);
        }

        if (request.getLoans() == null || request.getLoans().isEmpty()) {
            errors.add(ApiParameterError.parameterError("error.msg.bulk.disbursement.loans.empty",
                    "At least one loan must be specified for bulk disbursement", "loans"));
            throw new PlatformApiDataValidationException(errors);
        }

        // Validate Line of Credit exists and is active
        LineOfCredit lineOfCredit = lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(lineOfCreditId);

        if (lineOfCredit.getStatus() != LocStatus.ACTIVE) {
            errors.add(ApiParameterError.parameterError("error.msg.loc.not.active",
                    "Line of Credit must be in active status for bulk disbursement", "lineOfCreditId", lineOfCreditId));
        }

        // Verify LOC belongs to the specified client
        if (!lineOfCredit.getClient().getId().equals(clientId)) {
            errors.add(ApiParameterError.parameterError("error.msg.loc.client.mismatch",
                    "Line of Credit does not belong to the specified client", "clientId", clientId));
        }

        // Get all loan IDs from request and validate for duplicates
        Set<Long> loanIds = new HashSet<>();
        for (SingleLoanDisbursementRequest loanRequest : request.getLoans()) {
            if (loanRequest.getLoanId() == null) {
                errors.add(
                        ApiParameterError.parameterError("error.msg.bulk.disbursement.loan.id.null", "Loan ID cannot be null", "loanId"));
                continue;
            }

            if (!loanIds.add(loanRequest.getLoanId())) {
                errors.add(ApiParameterError.parameterError("error.msg.bulk.disbursement.duplicate.loan",
                        "Duplicate loan ID in request: " + loanRequest.getLoanId(), "loanId", loanRequest.getLoanId()));
            }
        }

        // Validate each loan belongs to the LOC and is in approved status using JDBC
        Set<Long> validLocLoanIds = getValidLoanIdsForLoc(lineOfCreditId);

        for (Long loanId : loanIds) {
            // Check loan belongs to this LOC
            if (!validLocLoanIds.contains(loanId)) {
                errors.add(ApiParameterError.parameterError("error.msg.loan.not.under.loc",
                        "Loan " + loanId + " does not belong to Line of Credit " + lineOfCreditId, "loanId", loanId));
                continue;
            }

            // Check loan status is approved using JDBC
            LoanBasicInfo loanInfo = getLoanBasicInfo(loanId);
            if (loanInfo == null) {
                errors.add(ApiParameterError.parameterError("error.msg.loan.not.found", "Loan not found: " + loanId, "loanId", loanId));
                continue;
            }

            // Status 200 = APPROVED in Fineract
            if (loanInfo.loanStatus() != 200) {
                errors.add(ApiParameterError.parameterError("error.msg.loan.not.approved",
                        "Loan " + loanId + " is not in approved status. Current status code: " + loanInfo.loanStatus(), "loanId", loanId));
            }
        }

        // Validate disbursement date if provided at bulk level
        if (request.getActualDisbursementDate() != null) {
            LocalDate businessDate = DateUtils.getBusinessLocalDate();
            if (request.getActualDisbursementDate().isAfter(businessDate)) {
                errors.add(ApiParameterError.parameterError("error.msg.disbursement.date.future",
                        "Disbursement date cannot be in the future", "actualDisbursementDate", request.getActualDisbursementDate()));
            }
        }

        // Validate auto-withdraw parameters
        if (Boolean.TRUE.equals(request.getAutoWithdrawFromSavings()) && request.getWithdrawalPaymentTypeId() == null) {
            errors.add(ApiParameterError.parameterError("error.msg.withdrawal.payment.type.required",
                    "Withdrawal payment type ID is required when autoWithdrawFromSavings is true", "withdrawalPaymentTypeId"));
        }

        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    /**
     * Disburses a single loan as part of the bulk operation. Errors are caught and returned as a failed result rather
     * than throwing.
     */
    private SingleLoanDisbursementResult disburseSingleLoan(Long lineOfCreditId, Long clientId, SingleLoanDisbursementRequest loanRequest,
            BulkLoanDisbursementRequest bulkRequest) {

        Long loanId = loanRequest.getLoanId();
        String loanAccountNo = null;
        String invoiceNo = null;

        try {
            // Get loan details for response using JDBC
            LoanBasicInfo loanInfo = getLoanBasicInfo(loanId);
            if (loanInfo == null) {
                throw new LoanNotFoundException(loanId);
            }
            loanAccountNo = loanInfo.accountNo();

            // Get invoice number from LOC params using JDBC
            invoiceNo = getInvoiceNoForLoan(loanId);

            // Build the JSON command for disbursement
            String disbursementJson = buildDisbursementJson(loanRequest, bulkRequest);

            // Generate a unique idempotency key for each loan to avoid conflicts within the same bulk request
            String idempotencyKey = "bulk-disburse-loc-" + lineOfCreditId + "-loan-" + loanId + "-" + System.nanoTime();
            log.info("Processing loan {} with idempotency key: {}", loanId, idempotencyKey);

            // IMPORTANT: Clear the command source ID from request context to prevent
            // the command processor from treating this as a retry of the previous loan's command
            fineractRequestContextHolder.setAttribute(SynchronousCommandProcessingService.COMMAND_SOURCE_ID, null);

            // Create the command wrapper for disbursement to savings
            CommandWrapper commandRequest = new CommandWrapperBuilder().disburseLoanToSavingsApplication(loanId).withJson(disbursementJson)
                    .build(idempotencyKey);

            log.info("Executing disbursement command for loan {} with wrapper idempotency key: {}", loanId,
                    commandRequest.getIdempotencyKey());

            // Execute the disbursement command
            CommandProcessingResult result = commandsSourceWritePlatformService.logCommandSource(commandRequest);

            log.info("Successfully disbursed loan {} under LOC {}", loanId, lineOfCreditId);

            // Verify the loan is now in Active status (300) - this confirms disbursement worked
            LoanBasicInfo updatedLoanInfo = getLoanBasicInfo(loanId);
            if (updatedLoanInfo == null || updatedLoanInfo.loanStatus() != 300) {
                log.error("Loan {} disbursement command succeeded but loan is not in Active status. Current status: {}", loanId,
                        updatedLoanInfo != null ? updatedLoanInfo.loanStatus() : "unknown");
                return SingleLoanDisbursementResult.failure(loanId, clientId, "error.msg.disbursement.verification.failed",
                        "Disbursement command succeeded but loan status did not change to Active", loanAccountNo, invoiceNo);
            }

            // Extract disbursement details - query the loan to get actual disbursed amounts
            BigDecimal amountDisbursed = getLoanDisbursedAmount(loanId);
            BigDecimal netAmountDisbursed = getLoanNetDisbursedAmount(loanId);
            Long transactionId = result.getSubResourceId();

            return SingleLoanDisbursementResult.success(loanId, clientId, result.getLoanId(), amountDisbursed, netAmountDisbursed,
                    transactionId, loanAccountNo, invoiceNo);

        } catch (IdempotentCommandProcessSucceedException e) {
            // Idempotent check - verify if the loan was actually disbursed previously
            log.info("Idempotent check triggered for loan {}, verifying actual disbursement status", loanId);

            // Verify the loan is in Active status (300) - confirms it was truly disbursed
            LoanBasicInfo updatedLoanInfo = getLoanBasicInfo(loanId);
            if (updatedLoanInfo != null && updatedLoanInfo.loanStatus() == 300) {
                // Loan is Active - it was truly disbursed before
                BigDecimal amountDisbursed = getLoanDisbursedAmount(loanId);
                BigDecimal netAmountDisbursed = getLoanNetDisbursedAmount(loanId);
                log.info("Loan {} confirmed as already disbursed (Active status), amount: {}", loanId, amountDisbursed);
                return SingleLoanDisbursementResult.success(loanId, clientId, loanId, amountDisbursed, netAmountDisbursed, null,
                        loanAccountNo, invoiceNo);
            } else {
                // Loan is NOT Active - idempotency returned but loan wasn't actually disbursed
                // This can happen if a previous command failed but was cached
                log.warn("Idempotent check returned for loan {} but loan is not in Active status. Current status: {}", loanId,
                        updatedLoanInfo != null ? updatedLoanInfo.loanStatus() : "unknown");
                return SingleLoanDisbursementResult.failure(loanId, clientId, "error.msg.loan.disbursement.idempotent.but.not.active",
                        "Previous disbursement attempt was cached but loan is not disbursed. Please retry with a new request.",
                        loanAccountNo, invoiceNo);
            }
        } catch (AbstractPlatformDomainRuleException e) {
            log.error("Domain rule error disbursing loan {}: {}", loanId, e.getMessage());
            return SingleLoanDisbursementResult.failure(loanId, clientId, e.getGlobalisationMessageCode(), e.getDefaultUserMessage(),
                    loanAccountNo, invoiceNo);
        } catch (PlatformApiDataValidationException e) {
            log.error("Validation error disbursing loan {}: {}", loanId, e.getMessage());
            String errorMessage = e.getErrors().stream().map(ApiParameterError::getDefaultUserMessage).collect(Collectors.joining("; "));
            return SingleLoanDisbursementResult.failure(loanId, clientId, "validation.error", errorMessage, loanAccountNo, invoiceNo);
        } catch (Exception e) {
            log.error("Unexpected error disbursing loan {}: {}", loanId, e.getMessage(), e);
            return SingleLoanDisbursementResult.failure(loanId, clientId, "error.msg.disbursement.failed",
                    "Failed to disburse loan: " + e.getMessage(), loanAccountNo, invoiceNo);
        }
    }

    /**
     * Builds the JSON command payload for a single loan disbursement.
     */
    private String buildDisbursementJson(SingleLoanDisbursementRequest loanRequest, BulkLoanDisbursementRequest bulkRequest) {

        JsonObject json = new JsonObject();

        // Date format and locale
        String dateFormat = bulkRequest.getDateFormat() != null ? bulkRequest.getDateFormat() : "yyyy-MM-dd";
        String locale = bulkRequest.getLocale() != null ? bulkRequest.getLocale() : "en";

        json.addProperty("dateFormat", dateFormat);
        json.addProperty("locale", locale);

        // Disbursement date (loan-specific overrides bulk)
        LocalDate disbursementDate = loanRequest.getActualDisbursementDate() != null ? loanRequest.getActualDisbursementDate()
                : bulkRequest.getActualDisbursementDate();

        if (disbursementDate == null) {
            disbursementDate = DateUtils.getBusinessLocalDate();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
        json.addProperty("actualDisbursementDate", disbursementDate.format(formatter));

        // Principal disbursed amount if specified (used to override the approved loan amount)
        if (loanRequest.getTransactionAmount() != null) {
            json.addProperty("principalDisbursed", loanRequest.getTransactionAmount());
        }

        // Note: paymentTypeId is NOT supported for account transfer disbursements (disburseLoanToSavings)
        // The paymentTypeId from the request is used for the auto-withdraw step, not the disbursement itself

        // Disburse in invoice currency
        if (loanRequest.getDisburseInInvoiceCurrency() != null) {
            json.addProperty("disburseInInvoiceCurrency", loanRequest.getDisburseInInvoiceCurrency());
        }

        // Auto-withdraw from savings
        if (Boolean.TRUE.equals(bulkRequest.getAutoWithdrawFromSavings())) {
            json.addProperty("autoWithdrawFromSavings", true);
            json.addProperty("withdrawalPaymentTypeId", bulkRequest.getWithdrawalPaymentTypeId());

            if (loanRequest.getWithdrawalAmount() != null) {
                json.addProperty("withdrawalAmount", loanRequest.getWithdrawalAmount());
            }
        }

        // External ID
        if (loanRequest.getExternalId() != null) {
            json.addProperty("externalId", loanRequest.getExternalId());
        }

        // Note (loan-specific overrides bulk)
        String note = loanRequest.getNote() != null ? loanRequest.getNote() : bulkRequest.getNote();
        if (note != null) {
            json.addProperty("note", note);
        }

        return json.toString();
    }

    // ================== JDBC Helper Methods ==================

    /**
     * Gets all valid loan IDs that belong to the specified Line of Credit.
     */
    private Set<Long> getValidLoanIdsForLoc(Long lineOfCreditId) {
        String sql = "SELECT loan_id FROM m_loan_line_of_credit_params WHERE line_of_credit_id = ?";
        List<Long> loanIds = jdbcTemplate.queryForList(sql, Long.class, lineOfCreditId);
        return new HashSet<>(loanIds);
    }

    /**
     * Gets basic loan information using JDBC to avoid JPA dependency.
     */
    private LoanBasicInfo getLoanBasicInfo(Long loanId) {
        String sql = "SELECT id, account_no, loan_status_id FROM m_loan WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new LoanBasicInfoRowMapper(), loanId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Gets invoice number for a loan from LOC params.
     */
    private String getInvoiceNoForLoan(Long loanId) {
        String sql = "SELECT invoice_no FROM m_loan_line_of_credit_params WHERE loan_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, loanId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Gets the total disbursed principal amount for a loan.
     */
    private BigDecimal getLoanDisbursedAmount(Long loanId) {
        String sql = "SELECT principal_disbursed_derived FROM m_loan WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, loanId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Gets the net disbursement amount for a loan (total disbursed minus charges deducted at disbursement).
     */
    private BigDecimal getLoanNetDisbursedAmount(Long loanId) {
        String sql = "SELECT net_disbursal_amount FROM m_loan WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, loanId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Record to hold basic loan information.
     */
    private record LoanBasicInfo(Long id, String accountNo, Integer loanStatus) {
    }

    /**
     * Row mapper for LoanBasicInfo.
     */
    private static class LoanBasicInfoRowMapper implements RowMapper<LoanBasicInfo> {

        @Override
        public LoanBasicInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new LoanBasicInfo(rs.getLong("id"), rs.getString("account_no"), rs.getInt("loan_status_id"));
        }
    }
}
