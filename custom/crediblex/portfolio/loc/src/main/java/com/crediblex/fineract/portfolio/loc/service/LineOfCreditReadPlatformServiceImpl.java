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

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.crediblex.fineract.portfolio.loc.data.LocActivationStatus;
import com.crediblex.fineract.portfolio.loc.data.LocReviewPeriods;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LineOfCreditReadPlatformServiceImpl implements LineOfCreditReadPlatformService {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;
    private final ClientReadPlatformService clientReadPlatformService;

    @Autowired
    public LineOfCreditReadPlatformServiceImpl(PlatformSecurityContext context, JdbcTemplate jdbcTemplate,
            ClientReadPlatformService clientReadPlatformService) {
        this.context = context;
        this.jdbcTemplate = jdbcTemplate;
        this.clientReadPlatformService = clientReadPlatformService;
    }

    private static final class LineOfCreditMapper implements RowMapper<LineOfCreditData> {

        public String schema() {
            return " loc.id as id, loc.client_id as clientId, loc.name as name, loc.product_type as productType, "
                    + "loc.maximum_amount as maximumAmount, loc.available_balance as availableBalance, "
                    + "loc.consumed_amount as consumedAmount, loc.activation_status as activationStatus, "
                    + "loc.start_date as startDate, loc.end_date as endDate, "
                    + "loc.approved_credit_facility_amount as approvedCreditFacilityAmount, "
                    + "loc.external_id as externalId, loc.activation_date as activationDate, "
                    + "loc.currency as currency, loc.advance_percentage as advancePercentage, "
                    + "loc.tenor_days as tenorDays, loc.approved_buyers as approvedBuyers, "
                    + "loc.processing_fee_pct_loc as processingFeePctLoc, loc.cash_margin_type as cashMarginType, "
                    + "loc.cash_margin_value as cashMarginValue, loc.inv_handling_fee_basis as invHandlingFeeBasis, "
                    + "loc.inv_handling_fee_pct as invHandlingFeePct, loc.inv_handling_fee_min_amount as invHandlingFeeMinAmount, "
                    + "loc.inv_handling_fee_currency as invHandlingFeeCurrency, loc.interim_review_date as interimReviewDate, "
                    + "loc.rate_type as rateType, loc.annual_interest_rate as annualInterestRate, "
                    + "loc.is_interest_upfront_or_post_disbursal as isInterestUpfrontOrPostDisbursal, "
                    + "loc.client_company_name as clientCompanyName, loc.client_contact_person_name as clientContactPersonName, "
                    + "loc.client_contact_person_phone as clientContactPersonPhone, loc.client_contact_person_email as clientContactPersonEmail, "
                    + "loc.authorized_signatory_name as authorizedSignatoryName, loc.authorized_signatory_phone as authorizedSignatoryPhone, "
                    + "loc.authorized_signatory_email as authorizedSignatoryEmail, loc.va as va, "
                    + "loc.distribution_partner as distributionPartner, loc.bank_transfer_fee as bankTransferFee, "
                    + "loc.special_conditions as specialConditions, loc.late_payment_fee as latePaymentFee, "
                    + "loc.created_on_utc as createdDate, loc.created_by as createdBy, "
                    + "loc.last_modified_on_utc as lastModifiedDate, loc.last_modified_by as lastModifiedBy "
                    + "from m_line_of_credit loc ";
        }

        @Override
        public LineOfCreditData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long clientId = rs.getLong("clientId");
            final String name = rs.getString("name");
            final String productType = rs.getString("productType");
            final BigDecimal maximumAmount = rs.getBigDecimal("maximumAmount");
            final BigDecimal availableBalance = rs.getBigDecimal("availableBalance");
            final BigDecimal consumedAmount = rs.getBigDecimal("consumedAmount");
            final String activationStatus = rs.getString("activationStatus");
            final LocalDate startDate = rs.getDate("startDate") != null ? rs.getDate("startDate").toLocalDate() : null;
            final LocalDate endDate = rs.getDate("endDate") != null ? rs.getDate("endDate").toLocalDate() : null;
            final BigDecimal approvedCreditFacilityAmount = rs.getBigDecimal("approvedCreditFacilityAmount");
            final String externalId = rs.getString("externalId");
            final LocalDate activationDate = rs.getDate("activationDate") != null ? rs.getDate("activationDate").toLocalDate() : null;
            final String currency = rs.getString("currency");
            final BigDecimal advancePercentage = rs.getBigDecimal("advancePercentage");
            final Integer tenorDays = rs.getObject("tenorDays") != null ? rs.getInt("tenorDays") : null;
            final String approvedBuyers = rs.getString("approvedBuyers");
            final BigDecimal processingFeePctLoc = rs.getBigDecimal("processingFeePctLoc");
            final String cashMarginType = rs.getString("cashMarginType");
            final BigDecimal cashMarginValue = rs.getBigDecimal("cashMarginValue");
            final String invHandlingFeeBasis = rs.getString("invHandlingFeeBasis");
            final BigDecimal invHandlingFeePct = rs.getBigDecimal("invHandlingFeePct");
            final BigDecimal invHandlingFeeMinAmount = rs.getBigDecimal("invHandlingFeeMinAmount");
            final String invHandlingFeeCurrency = rs.getString("invHandlingFeeCurrency");
            final LocalDate interimReviewDate = rs.getDate("interimReviewDate") != null ? rs.getDate("interimReviewDate").toLocalDate() : null;
            final String rateType = rs.getString("rateType");
            final BigDecimal annualInterestRate = rs.getBigDecimal("annualInterestRate");
            final String isInterestUpfrontOrPostDisbursal = rs.getString("isInterestUpfrontOrPostDisbursal");
            final String clientCompanyName = rs.getString("clientCompanyName");
            final String clientContactPersonName = rs.getString("clientContactPersonName");
            final String clientContactPersonPhone = rs.getString("clientContactPersonPhone");
            final String clientContactPersonEmail = rs.getString("clientContactPersonEmail");
            final String authorizedSignatoryName = rs.getString("authorizedSignatoryName");
            final String authorizedSignatoryPhone = rs.getString("authorizedSignatoryPhone");
            final String authorizedSignatoryEmail = rs.getString("authorizedSignatoryEmail");
            final String va = rs.getString("va");
            final String distributionPartner = rs.getString("distributionPartner");
            final BigDecimal bankTransferFee = rs.getBigDecimal("bankTransferFee");
            final String specialConditions = rs.getString("specialConditions");
            final BigDecimal latePaymentFee = rs.getBigDecimal("latePaymentFee");
            
            // Handle OffsetDateTime fields
            final LocalDate createdDate = rs.getTimestamp("createdDate") != null ? 
                rs.getTimestamp("createdDate").toLocalDateTime().toLocalDate() : null;
            final String createdBy = rs.getString("createdBy");
            final LocalDate lastModifiedDate = rs.getTimestamp("lastModifiedDate") != null ? 
                rs.getTimestamp("lastModifiedDate").toLocalDateTime().toLocalDate() : null;
            final String lastModifiedBy = rs.getString("lastModifiedBy");

            return LineOfCreditData.instance(id, clientId, null, name, productType, maximumAmount, availableBalance, consumedAmount,
                    getActivationStatusEnumOptionData(activationStatus), startDate, endDate, approvedCreditFacilityAmount, externalId, activationDate,
                    currency, advancePercentage, tenorDays, approvedBuyers, processingFeePctLoc, cashMarginType, cashMarginValue,
                    invHandlingFeeBasis, invHandlingFeePct, invHandlingFeeMinAmount, invHandlingFeeCurrency, interimReviewDate,
                    rateType, annualInterestRate, isInterestUpfrontOrPostDisbursal, clientCompanyName, clientContactPersonName,
                    clientContactPersonPhone, clientContactPersonEmail, authorizedSignatoryName, authorizedSignatoryPhone,
                    authorizedSignatoryEmail, va, distributionPartner, bankTransferFee, specialConditions, latePaymentFee,
                    createdDate, createdBy, lastModifiedDate, lastModifiedBy);
        }

        private EnumOptionData getActivationStatusEnumOptionData(String status) {
            if (status == null) {
                return null;
            }
            return LocActivationStatus.valueOf(status).getEnumOptionData();
        }
    }

    @Override
    public Collection<LineOfCreditData> retrieveAllLineOfCredits() {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        final String sql = "select " + mapper.schema() + " order by loc.id";
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(sql, mapper);
        
        // Enrich with client data
        return enrichWithClientData(lineOfCredits);
    }

    @Override
    public Page<LineOfCreditData> retrieveAllLineOfCredits(Pageable pageable) {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        
        // Count total records
        final String countSql = "select count(*) from m_line_of_credit";
        final Integer totalElements = this.jdbcTemplate.queryForObject(countSql, Integer.class);
        
        if (totalElements == null || totalElements == 0) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
        
        // Build pagination SQL
        final String sql = "select " + mapper.schema() + " order by loc.id";
        final String paginatedSql = sql + " limit " + pageable.getPageSize() + " offset " + pageable.getOffset();
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(paginatedSql, mapper);
        
        // Enrich with client data
        final List<LineOfCreditData> enrichedData = enrichWithClientData(lineOfCredits);
        
        return new PageImpl<>(enrichedData, pageable, totalElements);
    }

    @Override
    public LineOfCreditData retrieveOne(Long lineOfCreditId) {
        this.context.authenticatedUser();
        try {
            final LineOfCreditMapper mapper = new LineOfCreditMapper();
            final String sql = "select " + mapper.schema() + " where loc.id = ?";
            
            final LineOfCreditData lineOfCredit = this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { lineOfCreditId }); // NOSONAR
            
            return enrichWithClientData(lineOfCredit);
        } catch (final EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException("error.msg.line.of.credit.not.found", 
                "Line of credit not found with id: " + lineOfCreditId, new Object[]{lineOfCreditId});
        }
    }

    @Override
    public LineOfCreditData retrieveTemplate() {
        this.context.authenticatedUser();
        final Collection<EnumOptionData> activationStatusOptions = getActivationStatusOptions();
        final Collection<EnumOptionData> productTypeOptions = getProductTypeOptions();
        final Collection<EnumOptionData> reviewPeriodsOptions = getReviewPeriodsOptions();

        return LineOfCreditData.template(activationStatusOptions, productTypeOptions,reviewPeriodsOptions);
    }

    @Override
    public Collection<LineOfCreditData> retrieveAllLineOfCreditsForClient(Long clientId) {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        final String sql = "select " + mapper.schema() + " where loc.client_id = ? order by loc.id";
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(sql, mapper, new Object[] { clientId });
        
        return enrichWithClientData(lineOfCredits);
    }

    @Override
    public Collection<LineOfCreditData> retrieveActiveLineOfCreditsForClient(Long clientId) {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        final String sql = "select " + mapper.schema() + " where loc.client_id = ? and loc.activation_status = ? order by loc.id";
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(sql, mapper,
                clientId, "ACTIVE");
        
        return enrichWithClientData(lineOfCredits);
    }

    private List<LineOfCreditData> enrichWithClientData(List<LineOfCreditData> lineOfCredits) {
        for (LineOfCreditData loc : lineOfCredits) {
            if (loc.getClientId() != null) {
                final ClientData clientData = this.clientReadPlatformService.retrieveOne(loc.getClientId());
                // Create a new instance with client data
                final LineOfCreditData enrichedLoc = LineOfCreditData.instance(
                    loc.getId(), loc.getClientId(), clientData, loc.getName(), loc.getProductType(),
                    loc.getMaximumAmount(), loc.getAvailableBalance(), loc.getConsumedAmount(),
                    loc.getActivationStatus(), loc.getStartDate(), loc.getEndDate(),
                    loc.getApprovedCreditFacilityAmount(), loc.getExternalId(), loc.getActivationDate(),
                    loc.getCurrency(), loc.getAdvancePercentage(), loc.getTenorDays(), loc.getApprovedBuyers(),
                    loc.getProcessingFeePctLoc(), loc.getCashMarginType(), loc.getCashMarginValue(),
                    loc.getInvHandlingFeeBasis(), loc.getInvHandlingFeePct(), loc.getInvHandlingFeeMinAmount(),
                    loc.getInvHandlingFeeCurrency(), loc.getInterimReviewDate(), loc.getRateType(),
                    loc.getAnnualInterestRate(), loc.getIsInterestUpfrontOrPostDisbursal(), loc.getClientCompanyName(),
                    loc.getClientContactPersonName(), loc.getClientContactPersonPhone(), loc.getClientContactPersonEmail(),
                    loc.getAuthorizedSignatoryName(), loc.getAuthorizedSignatoryPhone(), loc.getAuthorizedSignatoryEmail(),
                    loc.getVa(), loc.getDistributionPartner(), loc.getBankTransferFee(), loc.getSpecialConditions(),
                    loc.getLatePaymentFee(), loc.getCreatedDate(), loc.getCreatedByUsername(), loc.getLastModifiedDate(), loc.getLastModifiedByUsername()
                );
                // Replace the original object in the list
                int index = lineOfCredits.indexOf(loc);
                if (index != -1) {
                    lineOfCredits.set(index, enrichedLoc);
                }
            }
        }
        return lineOfCredits;
    }

    private LineOfCreditData enrichWithClientData(LineOfCreditData lineOfCredit) {
        if (lineOfCredit.getClientId() != null) {
            final ClientData clientData = this.clientReadPlatformService.retrieveOne(lineOfCredit.getClientId());
            return LineOfCreditData.instance(
                lineOfCredit.getId(), lineOfCredit.getClientId(), clientData, lineOfCredit.getName(), lineOfCredit.getProductType(),
                lineOfCredit.getMaximumAmount(), lineOfCredit.getAvailableBalance(), lineOfCredit.getConsumedAmount(),
                lineOfCredit.getActivationStatus(), lineOfCredit.getStartDate(), lineOfCredit.getEndDate(),
                lineOfCredit.getApprovedCreditFacilityAmount(), lineOfCredit.getExternalId(), lineOfCredit.getActivationDate(),
                lineOfCredit.getCurrency(), lineOfCredit.getAdvancePercentage(), lineOfCredit.getTenorDays(), lineOfCredit.getApprovedBuyers(),
                lineOfCredit.getProcessingFeePctLoc(), lineOfCredit.getCashMarginType(), lineOfCredit.getCashMarginValue(),
                lineOfCredit.getInvHandlingFeeBasis(), lineOfCredit.getInvHandlingFeePct(), lineOfCredit.getInvHandlingFeeMinAmount(),
                lineOfCredit.getInvHandlingFeeCurrency(), lineOfCredit.getInterimReviewDate(), lineOfCredit.getRateType(),
                lineOfCredit.getAnnualInterestRate(), lineOfCredit.getIsInterestUpfrontOrPostDisbursal(), lineOfCredit.getClientCompanyName(),
                lineOfCredit.getClientContactPersonName(), lineOfCredit.getClientContactPersonPhone(), lineOfCredit.getClientContactPersonEmail(),
                lineOfCredit.getAuthorizedSignatoryName(), lineOfCredit.getAuthorizedSignatoryPhone(), lineOfCredit.getAuthorizedSignatoryEmail(),
                lineOfCredit.getVa(), lineOfCredit.getDistributionPartner(), lineOfCredit.getBankTransferFee(), lineOfCredit.getSpecialConditions(),
                lineOfCredit.getLatePaymentFee(), lineOfCredit.getCreatedDate(), lineOfCredit.getCreatedByUsername(), lineOfCredit.getLastModifiedDate(), lineOfCredit.getLastModifiedByUsername()
            );
        }
        return lineOfCredit;
    }

    private Collection<EnumOptionData> getActivationStatusOptions() {
        return Arrays.stream(LocActivationStatus.values())
                .map(LocActivationStatus::getEnumOptionData)
                .toList();
    }

    private Collection<EnumOptionData> getProductTypeOptions() {
        return Arrays.stream(LocProductType.values())
                .map(LocProductType::getEnumOptionsData)
                .collect(Collectors.toList());
    }

    private Collection<EnumOptionData> getReviewPeriodsOptions() {
        return Arrays.stream(LocReviewPeriods.values())
                .map(LocReviewPeriods::getEnumOptionsData)
                .collect(Collectors.toList());
    }
}
