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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.client.data.ClientData;

/**
 * Immutable data object representing Line of Credit data.
 */
@NoArgsConstructor
@Getter
@Setter
public final class LineOfCreditData implements Serializable {

    private Long id;
    private Long clientId;
    private ClientData client;
    private String name;
    private String productType;
    private BigDecimal maximumAmount;
    private BigDecimal availableBalance;
    private BigDecimal consumedAmount;
    private EnumOptionData activationStatus;
    private LocalDate startDate;
    private LocalDate endDate;

    // Audit fields
    private LocalDate createdDate;
    private String createdByUsername;
    private LocalDate lastModifiedDate;
    private String lastModifiedByUsername;

    // Template fields
    private Collection<EnumOptionData> activationStatusOptions;
    private Collection<String> productTypeOptions;

    // Import fields
    private transient Integer rowIndex;
    private String dateFormat;
    private String locale;

    public static LineOfCreditData importInstance(Long clientId, String name, String productType, BigDecimal maximumAmount,
            LocalDate startDate, LocalDate endDate, Integer rowIndex, String dateFormat, String locale) {
        return new LineOfCreditData(clientId, name, productType, maximumAmount, startDate, endDate, rowIndex, dateFormat, locale);
    }

    public static LineOfCreditData template(Collection<EnumOptionData> activationStatusOptions, Collection<String> productTypeOptions) {
        return new LineOfCreditData(activationStatusOptions, productTypeOptions);
    }

    public static LineOfCreditData instance(Long id, Long clientId, ClientData client, String name, String productType,
            BigDecimal maximumAmount, BigDecimal availableBalance, BigDecimal consumedAmount, EnumOptionData activationStatus,
            LocalDate startDate, LocalDate endDate, LocalDate createdDate, String createdByUsername, LocalDate lastModifiedDate,
            String lastModifiedByUsername) {
        return new LineOfCreditData(id, clientId, client, name, productType, maximumAmount, availableBalance, consumedAmount,
                activationStatus, startDate, endDate, createdDate, createdByUsername, lastModifiedDate, lastModifiedByUsername);
    }

    private LineOfCreditData(Long clientId, String name, String productType, BigDecimal maximumAmount, LocalDate startDate,
            LocalDate endDate, Integer rowIndex, String dateFormat, String locale) {
        this.clientId = clientId;
        this.name = name;
        this.productType = productType;
        this.maximumAmount = maximumAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.rowIndex = rowIndex;
        this.dateFormat = dateFormat;
        this.locale = locale;
    }

    private LineOfCreditData(Collection<EnumOptionData> activationStatusOptions, Collection<String> productTypeOptions) {
        this.activationStatusOptions = activationStatusOptions;
        this.productTypeOptions = productTypeOptions;
    }

    private LineOfCreditData(Long id, Long clientId, ClientData client, String name, String productType, BigDecimal maximumAmount,
            BigDecimal availableBalance, BigDecimal consumedAmount, EnumOptionData activationStatus, LocalDate startDate, LocalDate endDate,
            LocalDate createdDate, String createdByUsername, LocalDate lastModifiedDate, String lastModifiedByUsername) {
        this.id = id;
        this.clientId = clientId;
        this.client = client;
        this.name = name;
        this.productType = productType;
        this.maximumAmount = maximumAmount;
        this.availableBalance = availableBalance;
        this.consumedAmount = consumedAmount;
        this.activationStatus = activationStatus;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdDate = createdDate;
        this.createdByUsername = createdByUsername;
        this.lastModifiedDate = lastModifiedDate;
        this.lastModifiedByUsername = lastModifiedByUsername;
    }
}
