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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanBuilder;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanChargeWrapper;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanWrapper;
import java.math.BigDecimal;
import java.util.*;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class CustomLoanWritePlatformServiceJpaRepositoryImplTest {

    @Mock
    private CustomLoanJournalEntryPoster journalEntryPoster;

    @InjectMocks
    private CustomLoanWritePlatformServiceJpaRepositoryImpl customLoanWritePlatformService;

    private AppUser appUser;
    private static final Long LOAN_ID = 1L;
    private static final BigDecimal PRINCIPAL_AMOUNT = BigDecimal.valueOf(12000);
    private static final BigDecimal FEE_AMOUNT = BigDecimal.valueOf(1200);
    private static final BigDecimal VAT_AMOUNT = BigDecimal.valueOf(120);
    private static final BigDecimal NET_AMOUNT = PRINCIPAL_AMOUNT.subtract(FEE_AMOUNT).subtract(VAT_AMOUNT);

    @BeforeEach
    public void setUp() {
        appUser = mock(AppUser.class);

        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DateUtils.parseLocalDate("2025-05-20"))));
    }

    private void setupMoneyHelper() {
        ConfigurationDomainService cds = Mockito.mock(ConfigurationDomainService.class);
        lenient().when(cds.getRoundingMode()).thenReturn(6);

        MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cds);
        moneyHelper.initialize();
    }

    private Loan createLoanWithFeesAndVAT(LoanCharge loanCharge) {
        setupMoneyHelper();

        LoanProductRelatedDetail loanProductDetail = mock(LoanProductRelatedDetail.class);
        doNothing().when(loanProductDetail).setEnableAccrualActivityPosting(anyBoolean());
        Money principal = Money.of(CurrencyData.blank(), PRINCIPAL_AMOUNT);
        when(loanProductDetail.getPrincipal()).thenReturn(principal);

        LoanProduct loanProduct = mock(LoanProduct.class);

        when(loanProduct.getLoanProductRelatedDetail()).thenReturn(loanProductDetail);

        Set<LoanCharge> loanCharges = Set.of(loanCharge);
        Client client = mock(Client.class);
        LoanSummary summary = LoanSummary.create(FEE_AMOUNT.add(VAT_AMOUNT));
        summary.zeroFields();

        Loan loan = new LoanBuilder(loanProduct).withId(LOAN_ID).withLoanStatus(LoanStatus.ACTIVE).withCharges(loanCharges)
                .withSummary(summary).withClient(client).withProposedPrincipal(PRINCIPAL_AMOUNT).withApprovedPrincipal(PRINCIPAL_AMOUNT)
                .withNetDisbursalAmount(NET_AMOUNT).build();
        return loan;
    }

    public LoanCharge createMockLoanChargeToCalculatesCorrectVATAmount(BigDecimal feeAmount) {
        LoanCharge loanCharge = mock(LoanCharge.class);
        Charge charge = mock(Charge.class);
        TaxGroup taxGroup = mock(TaxGroup.class);
        TaxGroupMappings taxGroupMapping = mock(TaxGroupMappings.class);
        TaxComponent taxComponent = mock(TaxComponent.class);

        when(taxComponent.getPercentage()).thenReturn(BigDecimal.valueOf(10)); // 10% VAT
        when(taxGroupMapping.getTaxComponent()).thenReturn(taxComponent);
        when(taxGroup.getTaxGroupMappings()).thenReturn(Set.of(taxGroupMapping));
        when(charge.getTaxGroup()).thenReturn(taxGroup);
        when(loanCharge.getCharge()).thenReturn(charge);
        when(loanCharge.amount()).thenReturn(feeAmount);
        when(loanCharge.amountOutstanding()).thenReturn(feeAmount);
        when(loanCharge.isActive()).thenReturn(true);
        when(loanCharge.isDueAtDisbursement()).thenReturn(true);

        return loanCharge;
    }

    public LoanCharge createMockLoanChargeForFeeChargesDueAtDisbursement(BigDecimal feeAmount) {
        LoanCharge loanCharge = mock(LoanCharge.class);
        Charge charge = mock(Charge.class);
        TaxGroup taxGroup = mock(TaxGroup.class);
        TaxGroupMappings taxGroupMapping = mock(TaxGroupMappings.class);
        TaxComponent taxComponent = mock(TaxComponent.class);

        when(taxComponent.getPercentage()).thenReturn(BigDecimal.valueOf(10)); // 10% VAT
        when(taxGroupMapping.getTaxComponent()).thenReturn(taxComponent);
        when(taxGroup.getTaxGroupMappings()).thenReturn(Set.of(taxGroupMapping));
        when(charge.getTaxGroup()).thenReturn(taxGroup);
        when(loanCharge.getCharge()).thenReturn(charge);
        when(loanCharge.amount()).thenReturn(feeAmount);
        when(loanCharge.amountOutstanding()).thenReturn(feeAmount);
        when(loanCharge.isActive()).thenReturn(true);
        when(loanCharge.isDueAtDisbursement()).thenReturn(true);
        when(loanCharge.isChargePending()).thenReturn(true);
        return loanCharge;
    }

    public LoanCharge createMockLoanChargeForNetDisbursalCalculation(BigDecimal feeAmount) {
        LoanCharge loanCharge = mock(LoanCharge.class);
        when(loanCharge.amount()).thenReturn(feeAmount);
        when(loanCharge.isActive()).thenReturn(true);
        when(loanCharge.isDueAtDisbursement()).thenReturn(true);
        return loanCharge;
    }

    public LoanCharge createMockLoanChargeToCalculatesCorrectFeePortionExcludingVAT(BigDecimal feeAmount) {
        LoanCharge loanCharge = mock(LoanCharge.class);
        Charge charge = mock(Charge.class);
        TaxGroup taxGroup = mock(TaxGroup.class);
        TaxGroupMappings taxGroupMapping = mock(TaxGroupMappings.class);
        TaxComponent taxComponent = mock(TaxComponent.class);

        when(taxComponent.getPercentage()).thenReturn(BigDecimal.valueOf(10)); // 10% VAT
        when(taxGroupMapping.getTaxComponent()).thenReturn(taxComponent);
        when(taxGroup.getTaxGroupMappings()).thenReturn(Set.of(taxGroupMapping));
        when(charge.getTaxGroup()).thenReturn(taxGroup);
        when(loanCharge.getCharge()).thenReturn(charge);
        when(loanCharge.amountOutstanding()).thenReturn(feeAmount);
        return loanCharge;
    }

    @Test
    public void testLoanWrapperCalculatesCorrectFeeChargesDueAtDisbursement() {
        // Given
        Loan testLoan = createLoanWithFeesAndVAT(createMockLoanChargeForFeeChargesDueAtDisbursement(FEE_AMOUNT));
        // System.out.println(testLoan.getCharges().iterator().next().amountOutstanding());
        LoanWrapper loanWrapper = new LoanWrapper(testLoan);

        // When
        BigDecimal totalFees = loanWrapper.deriveTotalFeeChargesDueAtDisbursement();

        // Then
        assertEquals(FEE_AMOUNT.subtract(VAT_AMOUNT), totalFees, "Total fees should exclude VAT");
    }

    @Test
    public void testLoanWrapperCalculatesCorrectVATChargesDueAtDisbursement() {
        // Given
        Loan testLoan = createLoanWithFeesAndVAT(createMockLoanChargeForFeeChargesDueAtDisbursement(FEE_AMOUNT));
        LoanWrapper loanWrapper = new LoanWrapper(testLoan);

        // When
        BigDecimal totalVAT = loanWrapper.deriveTotalVATChargesDueAtDisbursement();

        // Then
        assertEquals(VAT_AMOUNT, totalVAT, "Total VAT should be calculated correctly");
    }

    @Test
    public void testLoanChargeWrapperCalculatesCorrectVATAmount() {
        Loan testLoan = createLoanWithFeesAndVAT(createMockLoanChargeToCalculatesCorrectVATAmount(FEE_AMOUNT));
        LoanCharge loanCharge = testLoan.getActiveCharges().iterator().next();
        LoanChargeWrapper chargeWrapper = new LoanChargeWrapper(loanCharge);
        BigDecimal vatAmount = chargeWrapper.getTaxesAmount();
        assertEquals(VAT_AMOUNT, vatAmount, "VAT amount should be calculated correctly");
    }

    @Test
    public void testLoanChargeWrapperCalculatesCorrectFeePortionExcludingVAT() {
        LoanChargeWrapper chargeWrapper = new LoanChargeWrapper(createMockLoanChargeToCalculatesCorrectFeePortionExcludingVAT(FEE_AMOUNT));
        BigDecimal feePortion = chargeWrapper.getFeePortionExcludingTax();
        assertEquals(FEE_AMOUNT.subtract(VAT_AMOUNT), feePortion, "Fee portion should exclude VAT");
    }

    @Test
    public void testNetDisbursalCalculation() {
        Loan testLoan = createLoanWithFeesAndVAT(createMockLoanChargeForNetDisbursalCalculation(FEE_AMOUNT));
        BigDecimal netDisbursal = testLoan.getNetDisbursalAmount();
        assertEquals(NET_AMOUNT, netDisbursal, "Net disbursal should be principal minus fees minus VAT");
    }

}
