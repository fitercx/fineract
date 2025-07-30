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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom service to handle CredibleX-specific loan product fields.
 */
@Service
public class CustomLoanProductService {

    private final JdbcTemplate jdbcTemplate;

    public CustomLoanProductService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Updates the is_loc_enable field for a loan product.
     * This method should be called after the main loan product creation/update.
     */
    @Transactional
    public void updateLocEnableField(Long loanProductId, JsonCommand command) {
        if (command.parameterExists("isLocEnable")) {
            boolean isLocEnable = command.booleanPrimitiveValueOfParameterNamed("isLocEnable");
            
            String sql = "UPDATE m_product_loan SET is_loc_enable = ? WHERE id = ?";
            jdbcTemplate.update(sql, isLocEnable, loanProductId);
        }
    }

    /**
     * Retrieves the is_loc_enable field for a loan product.
     */
    public boolean getLocEnableField(Long loanProductId) {
        String sql = "SELECT is_loc_enable FROM m_product_loan WHERE id = ?";
        Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, loanProductId);
        return result != null ? result : false;
    }
} 