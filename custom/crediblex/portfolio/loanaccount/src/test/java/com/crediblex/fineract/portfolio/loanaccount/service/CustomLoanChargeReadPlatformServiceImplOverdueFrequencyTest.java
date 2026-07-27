package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.repository.CustomLoanChargeRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.service.ChargeDropdownReadPlatformService;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regression: active overdue/LPI charges with a missing {@code m_loan_overdue_installment_charge} join must not NPE the
 * apply-penalty job's frequency lookup after a repayment-schedule update.
 */
@ExtendWith(MockitoExtension.class)
class CustomLoanChargeReadPlatformServiceImplOverdueFrequencyTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ChargeDropdownReadPlatformService chargeDropdownReadPlatformService;
    @Mock
    private DropdownReadPlatformService dropdownReadPlatformService;
    @Mock
    private LoanChargeRepository loanChargeRepository;
    @Mock
    private CustomLoanChargeRepository customLoanChargeRepository;

    private CustomLoanChargeReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomLoanChargeReadPlatformServiceImpl(jdbcTemplate, chargeDropdownReadPlatformService, dropdownReadPlatformService,
                loanChargeRepository, customLoanChargeRepository);
    }

    @Test
    void skipsOrphanActiveOverdueChargesWithoutJoinRow() {
        final Charge chargeDef = mock(Charge.class);
        final Loan loan = mock(Loan.class);

        final LoanCharge orphan = mock(LoanCharge.class);
        when(orphan.isOverdueInstallmentCharge()).thenReturn(true);
        when(orphan.isActive()).thenReturn(true);
        when(orphan.getCharge()).thenReturn(chargeDef);
        when(orphan.getOverdueInstallmentCharge()).thenReturn(null);

        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getInstallmentNumber()).thenReturn(1);
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(link.getInstallment()).thenReturn(installment);
        when(link.getFrequencyNumber()).thenReturn(3);

        final LoanCharge linked = mock(LoanCharge.class);
        when(linked.isOverdueInstallmentCharge()).thenReturn(true);
        when(linked.isActive()).thenReturn(true);
        when(linked.getCharge()).thenReturn(chargeDef);
        when(linked.getOverdueInstallmentCharge()).thenReturn(link);

        when(loan.getLoanCharges()).thenReturn(Set.of(orphan, linked));

        final Collection<Integer> frequencies = service.retrieveOverdueInstallmentChargeFrequencyNumber(loan, chargeDef, 1);

        assertEquals(List.of(3), List.copyOf(frequencies));
    }

    @Test
    void returnsEmptyWhenAllActiveOverdueChargesAreOrphans() {
        final Charge chargeDef = mock(Charge.class);
        final Loan loan = mock(Loan.class);

        final LoanCharge orphan = mock(LoanCharge.class);
        when(orphan.isOverdueInstallmentCharge()).thenReturn(true);
        when(orphan.isActive()).thenReturn(true);
        when(orphan.getCharge()).thenReturn(chargeDef);
        when(orphan.getOverdueInstallmentCharge()).thenReturn(null);

        when(loan.getLoanCharges()).thenReturn(Set.of(orphan));

        final Collection<Integer> frequencies = service.retrieveOverdueInstallmentChargeFrequencyNumber(loan, chargeDef, 1);

        assertTrue(frequencies.isEmpty());
    }
}
