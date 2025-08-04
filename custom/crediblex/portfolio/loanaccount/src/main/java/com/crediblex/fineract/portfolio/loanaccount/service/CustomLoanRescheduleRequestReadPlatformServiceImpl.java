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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestData;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestEnumerations;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestStatusEnumData;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.service.LoanRescheduleRequestReadPlatformServiceImpl;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Primary
public class CustomLoanRescheduleRequestReadPlatformServiceImpl extends LoanRescheduleRequestReadPlatformServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    public CustomLoanRescheduleRequestReadPlatformServiceImpl(final JdbcTemplate jdbcTemplate, 
            LoanRepositoryWrapper loanRepositoryWrapper,
            final CodeValueReadPlatformService codeValueReadPlatformService) {
        super(jdbcTemplate, loanRepositoryWrapper, codeValueReadPlatformService);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LoanRescheduleRequestData> retrieveAllRescheduleRequests(String command, Long loanId) {
        // First, get the basic reschedule request data without term variations
        LoanRescheduleRequestRowMapperForBulkApprovalBasic basicMapper = new LoanRescheduleRequestRowMapperForBulkApprovalBasic();
        String sql = "select " + basicMapper.schema();
        List<String> extraFilters = new ArrayList<>();
        List<Object> extraParams = new ArrayList<>();

        if (!StringUtils.isEmpty(command) && !command.equalsIgnoreCase(RescheduleLoansApiConstants.allCommandParamName)) {
            int statusParam = 100;
            if (command.equalsIgnoreCase(RescheduleLoansApiConstants.approveCommandParamName)) {
                statusParam = 200;
            } else if (command.equalsIgnoreCase(RescheduleLoansApiConstants.rejectCommandParamName)) {
                statusParam = 300;
            }

            extraFilters.add("lrr.status_enum = ?");
            extraParams.add(statusParam);
        }

        if (loanId != null) {
            extraFilters.add("loan.id = ?");
            extraParams.add(loanId);
        }

        if (!CollectionUtils.isEmpty(extraFilters)) {
            sql += " where " + String.join(" AND ", extraFilters);
        }
        
        
        // Execute the query and get basic reschedule request data
        List<LoanRescheduleRequestData> basicResults = jdbcTemplate.query(sql, basicMapper, extraParams.toArray()); // NOSONAR
        
        // Now get loan term variations for each reschedule request
        List<LoanRescheduleRequestData> finalResults = new ArrayList<>();
        for (LoanRescheduleRequestData basicData : basicResults) {
            Collection<LoanTermVariationsData> loanTermVariations = getLoanTermVariationsForRescheduleRequest(basicData.getId());
            
            // Create the final data object with loan term variations
            LoanRescheduleRequestData finalData = LoanRescheduleRequestData.instance(
                basicData.getId(), 
                basicData.getLoanId(), 
                basicData.getStatusEnum(), 
                basicData.getClientName(), 
                basicData.getLoanAccountNumber(), 
                basicData.getClientId(), 
                basicData.getRescheduleFromDate(),
                basicData.getRescheduleReasonCodeValueId(), 
                loanTermVariations
            );
            finalResults.add(finalData);
        }
        return finalResults;
    }
    
    private Collection<LoanTermVariationsData> getLoanTermVariationsForRescheduleRequest(Long rescheduleRequestId) {
        String sql = "select tv.id as termId, tv.term_type as termType, tv.applicable_date as variationApplicableFrom, " +
                     "tv.decimal_value as decimalValue, tv.date_value as dateValue, tv.is_specific_to_installment as isSpecificToInstallment " +
                     "from m_loan_term_variations tv " +
                     "join m_loan_reschedule_request_term_variations_mapping rrtvm on tv.id = rrtvm.loan_term_variations_id " +
                     "where rrtvm.loan_reschedule_request_id = ? and tv.parent_id is null";
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            final Long id = rs.getLong("termId");
            final LocalDate variationApplicableFrom = JdbcSupport.getLocalDate(rs, "variationApplicableFrom");
            final BigDecimal decimalValue = rs.getBigDecimal("decimalValue");
            final LocalDate dateValue = JdbcSupport.getLocalDate(rs, "dateValue");
            final boolean isSpecificToInstallment = rs.getBoolean("isSpecificToInstallment");
            final int termType = rs.getInt("termType");

            return new LoanTermVariationsData(id, LoanEnumerations.loanVariationType(termType), variationApplicableFrom, decimalValue,
                    dateValue, isSpecificToInstallment);
        }, rescheduleRequestId);
    }

    private static final class LoanRescheduleRequestRowMapperForBulkApprovalBasic implements RowMapper<LoanRescheduleRequestData> {
        public String schema() {
            final StringBuilder sqlBuilder = new StringBuilder(200);

            sqlBuilder.append("lrr.id as id, lrr.status_enum as statusEnum, lrr.reschedule_from_date as rescheduleFromDate, ");
            sqlBuilder.append("cv.id as rescheduleReasonCvId, cv.code_value as rescheduleReasonCvValue, ");
            sqlBuilder.append("loan.id as loanId, loan.account_no as loanAccountNumber, client.id as clientId, client.display_name as clientName, ");
            
            sqlBuilder.append("lrr.reschedule_from_installment as rescheduleFromInstallment, ");
            sqlBuilder.append("lrr.recalculate_interest as recalculateInterest, ");
            sqlBuilder.append("lrr.reschedule_reason_comment as rescheduleReasonComment, ");

            sqlBuilder.append("lrr.submitted_on_date as submittedOnDate, ");
            sqlBuilder.append("sbu.username as submittedByUsername, ");
            sqlBuilder.append("sbu.firstname as submittedByFirstname, ");
            sqlBuilder.append("sbu.lastname as submittedByLastname, ");

            sqlBuilder.append("lrr.approved_on_date as approvedOnDate, ");
            sqlBuilder.append("abu.username as approvedByUsername, ");
            sqlBuilder.append("abu.firstname as approvedByFirstname, ");
            sqlBuilder.append("abu.lastname as approvedByLastname, ");

            sqlBuilder.append("lrr.rejected_on_date as rejectedOnDate, ");
            sqlBuilder.append("rbu.username as rejectedByUsername, ");
            sqlBuilder.append("rbu.firstname as rejectedByFirstname, ");
            sqlBuilder.append("rbu.lastname as rejectedByLastname ");

            sqlBuilder.append("from m_loan_reschedule_request lrr ");
            sqlBuilder.append("left join m_code_value cv on cv.id = lrr.reschedule_reason_cv_id ");
            sqlBuilder.append("left join m_appuser sbu on sbu.id = lrr.submitted_by_user_id ");
            sqlBuilder.append("left join m_appuser abu on abu.id = lrr.approved_by_user_id ");
            sqlBuilder.append("left join m_appuser rbu on rbu.id = lrr.rejected_by_user_id ");
            sqlBuilder.append("left join m_loan loan on loan.id = lrr.loan_id ");
            sqlBuilder.append("left join m_client client on client.id = loan.client_id ");
            
            return sqlBuilder.toString();
        }

        @Override
        public LoanRescheduleRequestData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long loanId = rs.getLong("loanId");
            final Integer statusEnumId = JdbcSupport.getInteger(rs, "statusEnum");
            final LoanRescheduleRequestStatusEnumData statusEnum = LoanRescheduleRequestEnumerations.status(statusEnumId);
            final String clientName = rs.getString("clientName");
            final String loanAccountNumber = rs.getString("loanAccountNumber");
            final Long clientId = rs.getLong("clientId");
            final LocalDate rescheduleFromDate = JdbcSupport.getLocalDate(rs, "rescheduleFromDate");
            final Long rescheduleReasonCvId = JdbcSupport.getLong(rs, "rescheduleReasonCvId");
            final String rescheduleReasonCvValue = rs.getString("rescheduleReasonCvValue");
            final CodeValueData rescheduleReasonCodeValue = CodeValueData.instance(rescheduleReasonCvId, rescheduleReasonCvValue);
            final String rescheduleReasonComment = rs.getString("rescheduleReasonComment");
            final Boolean recalculateInterest = rs.getBoolean("recalculateInterest");
            final Integer rescheduleFromInstallment = JdbcSupport.getInteger(rs, "rescheduleFromInstallment");

            return LoanRescheduleRequestData.instance(id, loanId, statusEnum, rescheduleFromInstallment, rescheduleFromDate,
                    rescheduleReasonCodeValue, rescheduleReasonComment, null, clientName, loanAccountNumber, clientId, recalculateInterest,
                    null, null);
        }
    }
} 