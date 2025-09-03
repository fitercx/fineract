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

package com.crediblex.fineract.portfolio.loc.data;

import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * Mapper service for converting LoanAccountData to LocLoanAccountData with line of credit information.
 * This service extends the base loan account data with line of credit specific fields.
 */
@Component
public class CustomLoanAccountDataMapper {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CustomLoanAccountDataMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Enhance LoanAccountData with line of credit information.
     *
     * @param baseData the base loan account data
     * @return LocLoanAccountData with line of credit information
     */
    public CustomLoanAccountData enhanceWithLineOfCreditData(LoanAccountData baseData) {
        if (baseData == null || baseData.getId() == null) {
            return null;
        }

        // Get line of credit information for this loan
        String sql = "SELECT l.line_of_credit_id as lineOfCreditId, " +
                    "loc.name as lineOfCreditName, " +
                    "loc.external_id as lineOfCreditExternalId " +
                    "FROM m_loan l " +
                    "LEFT JOIN m_line_of_credit loc ON loc.id = l.line_of_credit_id " +
                    "WHERE l.id = ?";

        try {
            LineOfCreditInfo locInfo = jdbcTemplate.queryForObject(sql, new LineOfCreditInfoRowMapper(), baseData.getId());
            return new CustomLoanAccountData(baseData, locInfo.lineOfCreditId, locInfo.lineOfCreditName, locInfo.lineOfCreditExternalId);
        } catch (EmptyResultDataAccessException e) {
            // If no line of credit association, return data without line of credit info
            return new CustomLoanAccountData(baseData, null, null, null);
        }
    }

    /**
     * Enhance multiple LoanAccountData objects with line of credit information.
     *
     * @param baseDataList collection of base loan account data
     * @return list of LocLoanAccountData with line of credit information
     */
    public List<CustomLoanAccountData> enhanceWithLineOfCreditData(Collection<LoanAccountData> baseDataList) {
        if (baseDataList == null || baseDataList.isEmpty()) {
            return List.of();
        }

        // Extract loan IDs
        List<Long> loanIds = baseDataList.stream()
                .map(LoanAccountData::getId)
                .filter(id -> id != null)
                .toList();

        if (loanIds.isEmpty()) {
            return baseDataList.stream()
                    .map(data -> new CustomLoanAccountData(data, null, null, null))
                    .toList();
        }

        // Get line of credit information for all loans
        String placeholders = String.join(",", loanIds.stream().map(id -> "?").toArray(String[]::new));
        String sql = "SELECT l.id as loanId, l.line_of_credit_id as lineOfCreditId, " +
                    "loc.name as lineOfCreditName, loc.external_id as lineOfCreditExternalId " +
                    "FROM m_loan l " +
                    "LEFT JOIN m_line_of_credit loc ON loc.id = l.line_of_credit_id " +
                    "WHERE l.id IN (" + placeholders + ")";

        List<LineOfCreditInfo> locInfoList = jdbcTemplate.query(sql, new LineOfCreditInfoRowMapper(), loanIds.toArray());

        // Create a map for quick lookup
        java.util.Map<Long, LineOfCreditInfo> locInfoMap = locInfoList.stream()
                .collect(java.util.stream.Collectors.toMap(info -> info.loanId, info -> info));

        // Enhance each loan account data
        return baseDataList.stream()
                .map(data -> {
                    LineOfCreditInfo locInfo = locInfoMap.get(data.getId());
                    if (locInfo != null) {
                        return new CustomLoanAccountData(data, locInfo.lineOfCreditId, locInfo.lineOfCreditName, locInfo.lineOfCreditExternalId);
                    } else {
                        return new CustomLoanAccountData(data, null, null, null);
                    }
                })
                .toList();
    }

    /**
     * Get line of credit information for a specific loan.
     *
     * @param loanId the loan ID
     * @return line of credit information or null if not found
     */
    public LineOfCreditInfo getLineOfCreditInfo(Long loanId) {
        String sql = "SELECT l.id as loanId, l.line_of_credit_id as lineOfCreditId, " +
                    "loc.name as lineOfCreditName, loc.external_id as lineOfCreditExternalId " +
                    "FROM m_loan l " +
                    "LEFT JOIN m_line_of_credit loc ON loc.id = l.line_of_credit_id " +
                    "WHERE l.id = ?";

        try {
            return jdbcTemplate.queryForObject(sql, new LineOfCreditInfoRowMapper(), loanId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Data class to hold line of credit information.
     */
    private static class LineOfCreditInfo {
        Long loanId;
        Long lineOfCreditId;
        String lineOfCreditName;
        String lineOfCreditExternalId;
    }

    /**
     * Row mapper for line of credit information.
     */
    private static final class LineOfCreditInfoRowMapper implements RowMapper<LineOfCreditInfo> {
        @Override
        public LineOfCreditInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            LineOfCreditInfo info = new LineOfCreditInfo();
            info.loanId = rs.getLong("loanId");
            
            Long lineOfCreditId = rs.getLong("lineOfCreditId");
            if (rs.wasNull()) {
                info.lineOfCreditId = null;
            } else {
                info.lineOfCreditId = lineOfCreditId;
            }
            
            info.lineOfCreditName = rs.getString("lineOfCreditName");
            info.lineOfCreditExternalId = rs.getString("lineOfCreditExternalId");
            
            return info;
        }
    }
}
