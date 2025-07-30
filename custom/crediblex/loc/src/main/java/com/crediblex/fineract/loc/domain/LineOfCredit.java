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

package com.crediblex.fineract.loc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.client.domain.Client;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Line of Credit entity representing a credit line for a client.
 */
@Entity
@Table(name = "m_line_of_credit")
@Getter
@Setter
public class LineOfCredit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "product_type", length = 50, nullable = false)
    private String productType;

    @Column(name = "maximum_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal maximumAmount;

    @Column(name = "available_balance", precision = 19, scale = 6, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "consumed_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal consumedAmount;

    @Column(name = "activation_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivationStatus activationStatus;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Default constructor.
     */
    protected LineOfCredit() {
        // Required by JPA
    }

    /**
     * Constructor for creating a new Line of Credit.
     */
    public LineOfCredit(Client client, String name, String productType, BigDecimal maximumAmount,
                       LocalDate startDate, LocalDate endDate) {
        this.client = client;
        this.name = name;
        this.productType = productType;
        this.maximumAmount = maximumAmount;
        this.availableBalance = maximumAmount;
        this.consumedAmount = BigDecimal.ZERO;
        this.activationStatus = ActivationStatus.INACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Activation status enum for Line of Credit.
     */
    public enum ActivationStatus {
        ACTIVE, INACTIVE, SUSPENDED, CLOSED
    }
} 