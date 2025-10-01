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
package com.crediblex.fineract.portfolio.loc.charge.domain;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditApprovedBuyers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

@Entity
@Table(name = "m_loan_approver_buyers_suppliers")
@Getter
@Setter
@NoArgsConstructor
public class LineOfCreditLoanBuyerSupplierDetail extends AbstractPersistableCustom<Long> {

    @ManyToOne
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(name = "counterparty", nullable = false)
    private LineOfCreditCounterpartyType counterpartyType;

    @ManyToOne
    @JoinColumn(name = "buyer_supplier_id", nullable = false)
    private LineOfCreditApprovedBuyers approvedBuyers;

    public LineOfCreditLoanBuyerSupplierDetail(Loan loan, LineOfCreditCounterpartyType counterpartyType,
            LineOfCreditApprovedBuyers approvedBuyers) {
        this.loan = loan;
        this.counterpartyType = counterpartyType;
        this.approvedBuyers = approvedBuyers;
    }

}
