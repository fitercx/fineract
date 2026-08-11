package com.crediblex.fineract.portfolio.dpdrepayment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.dpdrepayment.data.DpdRepaymentProductConfigData;
import com.crediblex.fineract.portfolio.dpdrepayment.domain.transactionprocessor.DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DpdRepaymentStrategyResolverTest {

    private static final String BASE_STRATEGY = "pro-rata-mifos-standard-strategy";

    @Mock
    private DpdRepaymentProductConfigService productConfigService;

    @Mock
    private DpdRepaymentGlobalConfigService globalConfigService;

    @Mock
    private DpdMaxDaysPastDueService maxDaysPastDueService;

    @Mock
    private LoanRepaymentScheduleTransactionProcessorFactory transactionProcessorFactory;

    @InjectMocks
    private DpdRepaymentStrategyResolver resolver;

    @BeforeEach
    void setBusinessDate() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 8, 10))));
        when(globalConfigService.getPrincipalOnlyThreshold()).thenReturn(60);
    }

    @Test
    void resolveStatus_usesPrincipalOnlyWhenMaxDpdAboveThreshold() {
        when(productConfigService.findByLoanProductId(10L)).thenReturn(Optional.of(config(true)));
        when(maxDaysPastDueService.calculateMaxDpd(eq(1L), any())).thenReturn(61);
        stubProcessor(DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE,
                DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor.STRATEGY_NAME);

        final var status = resolver.resolveStatus(1L, 10L, BASE_STRATEGY, LocalDate.of(2026, 8, 10));

        assertTrue(status.isDpdPrincipalOnlyActive());
        assertEquals(DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE, status.getEffectiveRepaymentStrategyCode());
        assertEquals(61, status.getMaxDpd());
    }

    @Test
    void resolveStatus_revertsToBaseStrategyWhenMaxDpdAtThreshold() {
        when(productConfigService.findByLoanProductId(10L)).thenReturn(Optional.of(config(true)));
        when(maxDaysPastDueService.calculateMaxDpd(eq(1L), any())).thenReturn(60);
        stubProcessor(BASE_STRATEGY, "Pro-Rata");

        final var status = resolver.resolveStatus(1L, 10L, BASE_STRATEGY, LocalDate.of(2026, 8, 10));

        assertFalse(status.isDpdPrincipalOnlyActive());
        assertEquals(BASE_STRATEGY, status.getEffectiveRepaymentStrategyCode());
    }

    @Test
    void resolveStatus_ignoresFeatureWhenProductDisabled() {
        when(productConfigService.findByLoanProductId(10L)).thenReturn(Optional.of(config(false)));
        when(maxDaysPastDueService.calculateMaxDpd(eq(1L), any())).thenReturn(120);
        stubProcessor(BASE_STRATEGY, "Pro-Rata");

        final var status = resolver.resolveStatus(1L, 10L, BASE_STRATEGY, LocalDate.of(2026, 8, 10));

        assertFalse(status.isDpdPrincipalOnlyActive());
        assertEquals(BASE_STRATEGY, status.getEffectiveRepaymentStrategyCode());
    }

    @Test
    void resolveEffectiveStrategyCodeFromLoanEntity() {
        final Loan loan = mock(Loan.class);
        final LoanProduct product = mock(LoanProduct.class);
        when(loan.getId()).thenReturn(5L);
        when(loan.getLoanProduct()).thenReturn(product);
        when(product.getId()).thenReturn(10L);
        when(loan.getTransactionProcessingStrategyCode()).thenReturn(BASE_STRATEGY);
        when(productConfigService.findByLoanProductId(10L)).thenReturn(Optional.of(config(true)));
        when(maxDaysPastDueService.calculateMaxDpd(eq(5L), any())).thenReturn(90);
        stubProcessor(DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE,
                DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor.STRATEGY_NAME);

        assertEquals(DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE,
                resolver.resolveEffectiveStrategyCode(loan, LocalDate.of(2026, 8, 10)));
    }

    private void stubProcessor(final String code, final String name) {
        final LoanRepaymentScheduleTransactionProcessor processor = mock(LoanRepaymentScheduleTransactionProcessor.class);
        when(processor.getName()).thenReturn(name);
        when(transactionProcessorFactory.determineProcessor(code)).thenReturn(processor);
    }

    private DpdRepaymentProductConfigData config(final boolean enabled) {
        return DpdRepaymentProductConfigData.builder().loanProductId(10L).enableDpdPrincipalOnlyRepayment(enabled)
                .dpdPrincipalOnlyThreshold(60).build();
    }
}
