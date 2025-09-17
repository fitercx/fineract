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
package com.crediblex.fineract.portfolio.tax.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.common.AccountingDropdownReadPlatformService;
import org.apache.fineract.accounting.common.AccountingEnumerations;
import org.apache.fineract.accounting.glaccount.data.GLAccountData;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.portfolio.tax.data.TaxComponentData;
import org.apache.fineract.portfolio.tax.data.TaxComponentHistoryData;
import org.apache.fineract.portfolio.tax.data.TaxGroupData;
import org.apache.fineract.portfolio.tax.data.TaxGroupMappingsData;
import org.apache.fineract.portfolio.tax.service.TaxReadPlatformServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomTaxReadPlatformServiceImpl extends TaxReadPlatformServiceImpl {

    private static final CustomTaxComponentMapper TAX_COMPONENT_MAPPER = new CustomTaxComponentMapper();
    private static final CustomTaxGroupMapper TAX_GROUP_MAPPER = new CustomTaxGroupMapper();
    private final JdbcTemplate jdbcTemplate;

    public CustomTaxReadPlatformServiceImpl(JdbcTemplate jdbcTemplate,
            AccountingDropdownReadPlatformService accountingDropdownReadPlatformService) {
        super(jdbcTemplate, accountingDropdownReadPlatformService);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TaxComponentData> retrieveAllTaxComponents() {
        String sql = "select " + TAX_COMPONENT_MAPPER.getSchema();
        return this.jdbcTemplate.query(sql, new TaxComponentResultSetExtractor()); // NOSONAR
    }

    @Override
    public TaxComponentData retrieveTaxComponentData(final Long id) {
        String sql = "select " + TAX_COMPONENT_MAPPER.getSchema() + " where tc.id=?";
        return this.jdbcTemplate.query(sql, new TaxComponentResultSetExtractor(), id).stream().findFirst().orElse(null); // NOSONAR
    }

    @Override
    public List<TaxGroupData> retrieveAllTaxGroups() {
        String sql = "select " + TAX_GROUP_MAPPER.getSchema();
        return this.jdbcTemplate.query(sql, new TaxGroupResultSetExtractor());
    }

    @Override
    public TaxGroupData retrieveTaxGroupData(final Long id) {
        String sql = "select " + TAX_GROUP_MAPPER.getSchema() + " where tg.id=?";
        return this.jdbcTemplate.query(sql, new TaxGroupResultSetExtractor(), id).stream().findFirst().orElse(null);
    }

    private static final class TaxComponentResultSetExtractor implements ResultSetExtractor<List<TaxComponentData>> {

        private final CustomTaxComponentMapper customTaxComponentMapper = new CustomTaxComponentMapper();
        private final CustomTaxComponentHistoryDataMapper historyMapper = new CustomTaxComponentHistoryDataMapper();

        @Override
        public List<TaxComponentData> extractData(ResultSet rs) throws SQLException {
            Map<Long, TaxComponentDataBuilder> componentMap = new LinkedHashMap<>();
            while (rs.next()) {
                Long id = rs.getLong("id");
                TaxComponentDataBuilder builder = componentMap.get(id);
                if (builder == null) {
                    builder = new TaxComponentDataBuilder();
                    customTaxComponentMapper.mapRowToBuilder(rs, builder);
                    componentMap.put(id, builder);
                }
                TaxComponentHistoryData history = historyMapper.mapRow(rs, rs.getRow());
                builder.addHistory(history);
            }
            List<TaxComponentData> result = new ArrayList<>();
            for (TaxComponentDataBuilder builder : componentMap.values()) {
                result.add(builder.build());
            }
            return result;
        }

    }

    private static final class CustomTaxComponentMapper implements RowMapper<TaxComponentData> {

        private final String schema;

        CustomTaxComponentMapper() {
            StringBuilder sb = new StringBuilder();
            sb.append("tc.id as id, tc.name as name,");
            sb.append("tc.percentage as percentage, tc.start_date as startDate,");
            sb.append("tc.debit_account_type_enum as debitAccountTypeEnum,");
            sb.append("dgl.id as debitAccountId, dgl.name as debitAccountName,  dgl.gl_code as debitAccountGlCode,");
            sb.append("tc.credit_account_type_enum as creditAccountTypeEnum,");
            sb.append("cgl.id as creditAccountId, cgl.name as creditAccountName,  cgl.gl_code as creditAccountGlCode,");
            sb.append("history.percentage as historyPercentage, history.start_date as historyStartDate,");
            sb.append("history.end_date as historyEndDate");
            sb.append(" from m_tax_component tc ");
            sb.append(" left join acc_gl_account dgl on dgl.id = tc.debit_account_id");
            sb.append(" left join acc_gl_account cgl on cgl.id = tc.credit_account_id");
            sb.append(" left join m_tax_component_history history on history.tax_component_id = tc.id");

            this.schema = sb.toString();
        }

        @Override
        public TaxComponentData mapRow(ResultSet rs, int rowNum) throws SQLException {
            // Not used for aggregation, but required by interface
            TaxComponentDataBuilder builder = new TaxComponentDataBuilder();
            mapRowToBuilder(rs, builder);
            return builder.build();
        }

        public void mapRowToBuilder(ResultSet rs, TaxComponentDataBuilder builder) throws SQLException {
            builder.id = rs.getLong("id");
            builder.name = rs.getString("name");
            builder.percentage = rs.getBigDecimal("percentage");
            final Integer debitAccountTypeEnum = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "debitAccountTypeEnum");
            builder.debitAccountType = (debitAccountTypeEnum != null) ? AccountingEnumerations.gLAccountType(debitAccountTypeEnum) : null;
            if (debitAccountTypeEnum != null && debitAccountTypeEnum > 0) {
                final Long debitAccountId = rs.getLong("debitAccountId");
                final String debitAccountName = rs.getString("debitAccountName");
                final String debitAccountGlCode = rs.getString("debitAccountGlCode");
                builder.debitAccount = new GLAccountData().setId(debitAccountId).setName(debitAccountName).setGlCode(debitAccountGlCode);
            }
            final Integer creditAccountTypeEnum = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "creditAccountTypeEnum");
            builder.creditAccountType = (creditAccountTypeEnum != null) ? AccountingEnumerations.gLAccountType(creditAccountTypeEnum)
                    : null;
            if (creditAccountTypeEnum != null && creditAccountTypeEnum > 0) {
                final Long creditAccountId = rs.getLong("creditAccountId");
                final String creditAccountName = rs.getString("creditAccountName");
                final String creditAccountGlCode = rs.getString("creditAccountGlCode");
                builder.creditAccount = new GLAccountData().setId(creditAccountId).setName(creditAccountName)
                        .setGlCode(creditAccountGlCode);
            }
            builder.startDate = JdbcSupport.getLocalDate(rs, "startDate");
        }

        public String getSchema() {
            return this.schema;
        }

    }

    private static final class CustomTaxComponentHistoryDataMapper implements RowMapper<TaxComponentHistoryData> {

        @Override
        public TaxComponentHistoryData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final BigDecimal percentage = rs.getBigDecimal("historyPercentage");
            final LocalDate startDate = JdbcSupport.getLocalDate(rs, "historyStartDate");
            final LocalDate endDate = JdbcSupport.getLocalDate(rs, "historyEndDate");
            // Only return if at least one field is not null
            if (percentage == null && startDate == null && endDate == null) {
                return null;
            }
            return new TaxComponentHistoryData(percentage, startDate, endDate);
        }
    }

    private static final class TaxComponentDataBuilder {

        private Long id;
        private String name;
        private BigDecimal percentage;
        private EnumOptionData debitAccountType;
        private GLAccountData debitAccount;
        private EnumOptionData creditAccountType;
        private GLAccountData creditAccount;
        private LocalDate startDate;
        private final Collection<TaxComponentHistoryData> taxComponentHistories = new ArrayList<>();

        void addHistory(TaxComponentHistoryData history) {
            if (history != null && history.getPercentage() != null) {
                taxComponentHistories.add(history);
            }
        }

        TaxComponentData build() {
            return TaxComponentData.instance(id, name, percentage, debitAccountType, debitAccount, creditAccountType, creditAccount,
                    startDate, taxComponentHistories.isEmpty() ? null : taxComponentHistories);
        }
    }

    private static final class TaxGroupResultSetExtractor implements ResultSetExtractor<List<TaxGroupData>> {

        private final CustomTaxGroupMappingsDataMapper taxGroupMappingsDataMapper = new CustomTaxGroupMappingsDataMapper();

        @Override
        public List<TaxGroupData> extractData(ResultSet rs) throws SQLException {
            Map<Long, TaxGroupDataBuilder> groupMap = new LinkedHashMap<>();
            while (rs.next()) {
                Long groupId = rs.getLong("id");
                String groupName = rs.getString("name");
                TaxGroupDataBuilder builder = groupMap.get(groupId);
                if (builder == null) {
                    builder = new TaxGroupDataBuilder();
                    builder.id = groupId;
                    builder.name = groupName;
                    groupMap.put(groupId, builder);
                }
                TaxGroupMappingsData mapping = taxGroupMappingsDataMapper.mapRow(rs, rs.getRow());
                builder.addMapping(mapping);
            }
            List<TaxGroupData> result = new ArrayList<>();
            for (TaxGroupDataBuilder builder : groupMap.values()) {
                result.add(builder.build());
            }
            return result;
        }
    }

    private static final class CustomTaxGroupMapper implements RowMapper<TaxGroupData> {

        private final String schema;
        private final CustomTaxGroupMappingsDataMapper taxGroupMappingsDataMapper = new CustomTaxGroupMappingsDataMapper();

        CustomTaxGroupMapper() {
            StringBuilder sb = new StringBuilder();
            sb.append("tg.id as id, tg.name as name,");
            sb.append("tgm.id as mappingId,");
            sb.append("tc.id as taxComponentId, tc.name as taxComponentName,");
            sb.append("tgm.start_date as startDate, tgm.end_date as endDate ");
            sb.append(" from m_tax_group tg ");
            sb.append(" inner join m_tax_group_mappings tgm on tgm.tax_group_id = tg.id ");
            sb.append(" inner join m_tax_component tc on tc.id = tgm.tax_component_id ");
            this.schema = sb.toString();
        }

        @Override
        public TaxGroupData mapRow(ResultSet rs, int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final String name = rs.getString("name");
            final Collection<TaxGroupMappingsData> taxAssociations = new ArrayList<>();
            taxAssociations.add(this.taxGroupMappingsDataMapper.mapRow(rs, rowNum));
            while (rs.next()) {
                if (id.equals(rs.getLong("id"))) {
                    taxAssociations.add(this.taxGroupMappingsDataMapper.mapRow(rs, rowNum));
                } else {
                    rs.previous();
                    break;
                }
            }
            return TaxGroupData.instance(id, name, taxAssociations);
        }

        public String getSchema() {
            return this.schema;
        }

    }

    private static final class CustomTaxGroupMappingsDataMapper implements RowMapper<TaxGroupMappingsData> {

        @Override
        public TaxGroupMappingsData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final Long mappingId = rs.getLong("mappingId");
            final Long id = rs.getLong("taxComponentId");
            final String name = rs.getString("taxComponentName");
            TaxComponentData componentData = TaxComponentData.lookup(id, name);

            final LocalDate startDate = JdbcSupport.getLocalDate(rs, "startDate");
            final LocalDate endDate = JdbcSupport.getLocalDate(rs, "endDate");
            return new TaxGroupMappingsData(mappingId, componentData, startDate, endDate);
        }

    }

    private static final class TaxGroupDataBuilder {

        private Long id;
        private String name;
        private final Collection<TaxGroupMappingsData> taxAssociations = new ArrayList<>();

        void addMapping(TaxGroupMappingsData mapping) {
            if (mapping != null) {
                taxAssociations.add(mapping);
            }
        }

        TaxGroupData build() {
            return TaxGroupData.instance(id, name, taxAssociations.isEmpty() ? null : taxAssociations);
        }
    }

}
