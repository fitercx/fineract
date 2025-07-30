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

package com.crediblex.fineract.loc.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Immutable data object representing Line of Credit Transaction data.
 */
@NoArgsConstructor
@Getter
@Setter
public final class LineOfCreditTransactionData implements Serializable {

    private Long id;
    private Long lineOfCreditId;
    private EnumOptionData transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private LocalDateTime transactionDate;
    private String referenceNumber;
    private String description;
    
    // Audit fields
    private LocalDateTime createdDate;
    private String createdByUsername;
    private LocalDateTime lastModifiedDate;
    private String lastModifiedByUsername;

    // Template fields
    private Collection<EnumOptionData> transactionTypeOptions;

    public static LineOfCreditTransactionData template(Collection<EnumOptionData> transactionTypeOptions) {
        return new LineOfCreditTransactionData(transactionTypeOptions);
    }

    public static LineOfCreditTransactionData instance(Long id, Long lineOfCreditId, EnumOptionData transactionType,
                                                      BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                                      LocalDateTime transactionDate, String referenceNumber, String description,
                                                      LocalDateTime createdDate, String createdByUsername,
                                                      LocalDateTime lastModifiedDate, String lastModifiedByUsername) {
        return new LineOfCreditTransactionData(id, lineOfCreditId, transactionType, amount, balanceBefore, balanceAfter,
                                              transactionDate, referenceNumber, description, createdDate, createdByUsername,
                                              lastModifiedDate, lastModifiedByUsername);
    }

    private LineOfCreditTransactionData(Collection<EnumOptionData> transactionTypeOptions) {
        this.transactionTypeOptions = transactionTypeOptions;
    }

    private LineOfCreditTransactionData(Long id, Long lineOfCreditId, EnumOptionData transactionType,
                                       BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                       LocalDateTime transactionDate, String referenceNumber, String description,
                                       LocalDateTime createdDate, String createdByUsername,
                                       LocalDateTime lastModifiedDate, String lastModifiedByUsername) {
        this.id = id;
        this.lineOfCreditId = lineOfCreditId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.transactionDate = transactionDate;
        this.referenceNumber = referenceNumber;
        this.description = description;
        this.createdDate = createdDate;
        this.createdByUsername = createdByUsername;
        this.lastModifiedDate = lastModifiedDate;
        this.lastModifiedByUsername = lastModifiedByUsername;
    }
} 