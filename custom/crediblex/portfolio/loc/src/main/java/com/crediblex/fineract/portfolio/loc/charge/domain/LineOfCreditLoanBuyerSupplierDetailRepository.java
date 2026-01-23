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
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineOfCreditLoanBuyerSupplierDetailRepository extends JpaRepository<LineOfCreditLoanBuyerSupplierDetail, Long> {

    List<LineOfCreditLoanBuyerSupplierDetail> findByLoan(Loan loan);

    List<LineOfCreditLoanBuyerSupplierDetail> findByApprovedBuyers(LineOfCreditApprovedBuyers approvedBuyers);

    @Query("SELECT lbsd FROM LineOfCreditLoanBuyerSupplierDetail lbsd WHERE lbsd.approvedBuyers.id IN :buyerIds")
    List<LineOfCreditLoanBuyerSupplierDetail> findByApprovedBuyersIds(@Param("buyerIds") List<Long> buyerIds);

    @Query("SELECT lbsd FROM LineOfCreditLoanBuyerSupplierDetail lbsd " + "WHERE lbsd.approvedBuyers.id IN :buyerIds "
            + "AND lbsd.loan.loanStatus IN :activeStatuses")
    List<LineOfCreditLoanBuyerSupplierDetail> findByApprovedBuyersIdsWithActiveLoans(@Param("buyerIds") List<Long> buyerIds,
            @Param("activeStatuses") List<LoanStatus> activeStatuses);
}
