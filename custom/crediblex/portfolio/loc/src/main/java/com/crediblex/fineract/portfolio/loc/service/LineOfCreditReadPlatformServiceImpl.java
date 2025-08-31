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
import com.crediblex.fineract.portfolio.loc.data.ProductType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
            
            // Handle OffsetDateTime fields
            final LocalDate createdDate = rs.getTimestamp("createdDate") != null ? 
                rs.getTimestamp("createdDate").toLocalDateTime().toLocalDate() : null;
            final String createdBy = rs.getString("createdBy");
            final LocalDate lastModifiedDate = rs.getTimestamp("lastModifiedDate") != null ? 
                rs.getTimestamp("lastModifiedDate").toLocalDateTime().toLocalDate() : null;
            final String lastModifiedBy = rs.getString("lastModifiedBy");

            return LineOfCreditData.instance(id, clientId, null, name, productType, maximumAmount, availableBalance, consumedAmount,
                    getActivationStatusEnumOptionData(activationStatus), startDate, endDate, createdDate, createdBy, lastModifiedDate, lastModifiedBy);
        }

        private EnumOptionData getActivationStatusEnumOptionData(String status) {
            if (status == null) {
                return null;
            }
            try {
                com.crediblex.fineract.portfolio.loc.domain.LineOfCredit.ActivationStatus activationStatus = 
                    com.crediblex.fineract.portfolio.loc.domain.LineOfCredit.ActivationStatus.valueOf(status);
                return new EnumOptionData((long) activationStatus.ordinal(), activationStatus.name(), activationStatus.name());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown activation status: {}", status);
                return new EnumOptionData(0L, status, status);
            }
        }
    }

    @Override
    public Collection<LineOfCreditData> retrieveAllLineOfCredits() {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        final String sql = "select " + mapper.schema() + " order by loc.id";
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(sql, mapper); // NOSONAR
        
        // Enrich with client data
        return enrichWithClientData(lineOfCredits);
    }

    @Override
    public Page<LineOfCreditData> retrieveAllLineOfCredits(Pageable pageable) {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        
        // Count total records
        final String countSql = "select count(*) from m_line_of_credit";
        final Integer totalElements = this.jdbcTemplate.queryForObject(countSql, Integer.class); // NOSONAR
        
        if (totalElements == null || totalElements == 0) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
        
        // Build pagination SQL
        final String sql = "select " + mapper.schema() + " order by loc.id";
        final String paginatedSql = sql + " limit " + pageable.getPageSize() + " offset " + pageable.getOffset();
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(paginatedSql, mapper); // NOSONAR
        
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
        final Collection<String> productTypeOptions = getProductTypeOptions();

        return LineOfCreditData.template(activationStatusOptions, productTypeOptions);
    }

    @Override
    public Collection<LineOfCreditData> retrieveAllLineOfCreditsForClient(Long clientId) {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        final String sql = "select " + mapper.schema() + " where loc.client_id = ? order by loc.id";
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(sql, mapper, new Object[] { clientId }); // NOSONAR
        
        return enrichWithClientData(lineOfCredits);
    }

    @Override
    public Collection<LineOfCreditData> retrieveActiveLineOfCreditsForClient(Long clientId) {
        this.context.authenticatedUser();
        
        final LineOfCreditMapper mapper = new LineOfCreditMapper();
        final String sql = "select " + mapper.schema() + " where loc.client_id = ? and loc.activation_status = ? order by loc.id";
        
        final List<LineOfCreditData> lineOfCredits = this.jdbcTemplate.query(sql, mapper, 
            new Object[] { clientId, "ACTIVE" }); // NOSONAR
        
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
                    loc.getCreatedDate(), loc.getCreatedByUsername(), loc.getLastModifiedDate(), loc.getLastModifiedByUsername()
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
                lineOfCredit.getCreatedDate(), lineOfCredit.getCreatedByUsername(), lineOfCredit.getLastModifiedDate(), lineOfCredit.getLastModifiedByUsername()
            );
        }
        return lineOfCredit;
    }

    private Collection<EnumOptionData> getActivationStatusOptions() {
        final List<EnumOptionData> options = new ArrayList<>();
        for (com.crediblex.fineract.portfolio.loc.domain.LineOfCredit.ActivationStatus status : 
             com.crediblex.fineract.portfolio.loc.domain.LineOfCredit.ActivationStatus.values()) {
            options.add(new EnumOptionData((long) status.ordinal(), status.name(), status.name()));
        }
        return options;
    }

    private Collection<String> getProductTypeOptions() {
        final List<String> options = new ArrayList<>();
        for (ProductType productType : ProductType.values()) {
            options.add(productType.name());
        }
        return options;
    }
}
