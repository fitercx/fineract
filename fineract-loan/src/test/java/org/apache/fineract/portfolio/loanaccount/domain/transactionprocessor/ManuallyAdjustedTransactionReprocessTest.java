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
package org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManuallyAdjustedTransactionReprocessTest {

    private static final MonetaryCurrency CURRENCY = new MonetaryCurrency("USD", 2, 1);
    private static final MockedStatic<MoneyHelper> MONEY_HELPER = Mockito.mockStatic(MoneyHelper.class);

    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate EMI1_DUE = LocalDate.of(2026, 2, 1);
    private static final LocalDate REPAYMENT_DATE = LocalDate.of(2026, 2, 15);

    private InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor processor;
    private Loan loan;
    private Office office;

    @BeforeAll
    static void initMoneyHelper() {
        MONEY_HELPER.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        MONEY_HELPER.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
    }

    @AfterAll
    static void closeMoneyHelper() {
        MONEY_HELPER.close();
    }

    @BeforeEach
    void setUp() {
        processor = new InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(mock(ExternalIdFactory.class));
        office = mock(Office.class);
        loan = mock(Loan.class);
        when(loan.isReceivableLocLoan()).thenReturn(false);

        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, REPAYMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void reprocess_doesNotReverseManuallyAdjustedRepaymentWhenStrategyWouldSplitDifferently() throws Exception {
        LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, DISBURSEMENT_DATE, EMI1_DUE,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null,
                BigDecimal.ZERO);
        installment.setId(10L);
        List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(List.of(installment));

        Money repaymentAmount = Money.of(CURRENCY, BigDecimal.valueOf(100));
        LoanTransaction repayment = LoanTransaction.repayment(office, repaymentAmount, null, REPAYMENT_DATE, ExternalId.empty());
        repayment.updateLoan(loan);
        repayment.updateComponents(Money.of(CURRENCY, BigDecimal.valueOf(100)), Money.zero(CURRENCY), Money.zero(CURRENCY),
                Money.zero(CURRENCY));
        repayment.setManuallyAdjustedOrReversed();
        setEntityId(repayment, 100L);

        LoanTransactionToRepaymentScheduleMapping mapping = LoanTransactionToRepaymentScheduleMapping.createFrom(repayment, installment,
                Money.of(CURRENCY, BigDecimal.valueOf(100)), Money.zero(CURRENCY), Money.zero(CURRENCY), Money.zero(CURRENCY));
        setEntityId(mapping, 200L);
        repayment.getLoanTransactionToRepaymentScheduleMappings().add(mapping);

        List<LoanTransaction> transactions = List.of(repayment);
        Set<org.apache.fineract.portfolio.loanaccount.domain.LoanCharge> charges = new HashSet<>();

        ChangedTransactionDetail result = processor.reprocessLoanTransactions(DISBURSEMENT_DATE, transactions, CURRENCY, installments,
                charges);

        assertTrue(repayment.isNotReversed(), "Manually adjusted repayment must survive LPI-style reprocess");
        assertTrue(result.getTransactionChanges().isEmpty(), "Reprocess must not create replay transactions");
        assertEquals(0, BigDecimal.valueOf(100).compareTo(installment.getPrincipalCompleted(CURRENCY).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(installment.getInterestPaid(CURRENCY).getAmount()));
    }

    @Test
    void reprocess_reversesUnmarkedRepaymentWhenStrategySplitDiffers() throws Exception {
        LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, DISBURSEMENT_DATE, EMI1_DUE,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null,
                BigDecimal.ZERO);
        installment.setId(11L);
        List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(List.of(installment));

        Money repaymentAmount = Money.of(CURRENCY, BigDecimal.valueOf(100));
        LoanTransaction repayment = LoanTransaction.repayment(office, repaymentAmount, null, REPAYMENT_DATE, ExternalId.empty());
        repayment.updateLoan(loan);
        repayment.updateComponents(Money.of(CURRENCY, BigDecimal.valueOf(100)), Money.zero(CURRENCY), Money.zero(CURRENCY),
                Money.zero(CURRENCY));
        setEntityId(repayment, 101L);

        LoanTransactionToRepaymentScheduleMapping mapping = LoanTransactionToRepaymentScheduleMapping.createFrom(repayment, installment,
                Money.of(CURRENCY, BigDecimal.valueOf(100)), Money.zero(CURRENCY), Money.zero(CURRENCY), Money.zero(CURRENCY));
        setEntityId(mapping, 201L);
        repayment.getLoanTransactionToRepaymentScheduleMappings().add(mapping);

        List<LoanTransaction> transactions = List.of(repayment);
        Set<org.apache.fineract.portfolio.loanaccount.domain.LoanCharge> charges = Collections.emptySet();

        ChangedTransactionDetail result = processor.reprocessLoanTransactions(DISBURSEMENT_DATE, transactions, CURRENCY, installments,
                charges);

        assertTrue(repayment.isReversed(), "Unmarked repayment with non-PIPF split should be reversed on reprocess");
        assertFalse(result.getTransactionChanges().isEmpty(), "Reprocess should emit a replay transaction");
    }

    private static void setEntityId(final Object entity, final Long id) throws Exception {
        java.lang.reflect.Field idField = org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom.class
                .getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
