package com.crediblex.fineract.portfolio.loc.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidBy;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargeRepository;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidByRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocChargeAllocationServiceTest {

    private LineOfCreditRepository locRepo;
    private LineOfCreditChargeRepository chargeRepo;
    private LineOfCreditChargePaidByRepository paidByRepo;
    private LineOfCreditChargeDomainService domainService;
    private LocChargeAllocationService service;

    @BeforeEach
    void init() {
        locRepo = mock(LineOfCreditRepository.class);
        chargeRepo = mock(LineOfCreditChargeRepository.class);
        paidByRepo = mock(LineOfCreditChargePaidByRepository.class);
        domainService = spy(new LineOfCreditChargeDomainService());
        service = new LocChargeAllocationService(locRepo, chargeRepo, paidByRepo, domainService);
    }

    @Test
    void allocatesAcrossMultipleCharges() {
        Long savingsId = 10L;
        LineOfCredit loc = new LineOfCredit();
        loc.setId(55L);
        when(locRepo.findBySettlementSavingsAccount_Id(savingsId)).thenReturn(Optional.of(loc));

        LineOfCreditCharge c1 = new LineOfCreditCharge();
        c1.setLineOfCredit(loc); c1.setAmount(new BigDecimal("50")); c1.setAmountOutstanding(new BigDecimal("50")); c1.setActive(true);
        c1.setChargeTime(2); c1.setChargeCalculation(1); // specified due date / flat
        LineOfCreditCharge c2 = new LineOfCreditCharge();
        c2.setLineOfCredit(loc); c2.setAmount(new BigDecimal("80")); c2.setAmountOutstanding(new BigDecimal("80")); c2.setActive(true);
        c2.setChargeTime(2); c2.setChargeCalculation(1);

        when(chargeRepo.findUnpaidOrdered(loc.getId())).thenReturn(List.of(c1, c2));

        SavingsAccount sa = mock(SavingsAccount.class);
        when(sa.getId()).thenReturn(savingsId);
        SavingsAccountTransaction txn = mock(SavingsAccountTransaction.class);
        when(txn.getId()).thenReturn(999L);
        when(txn.getSavingsAccount()).thenReturn(sa);
        when(txn.getAmount()).thenReturn(new BigDecimal("100"));
        when(txn.isWithdrawal()).thenReturn(true);
        when(txn.isChargeTransactionAndNotReversed()).thenReturn(false);
        when(txn.isPayCharge()).thenReturn(false);

        service.allocateForSavingsWithdrawal(txn);

        // Verify payments applied
        assertThat(c1.getAmountOutstanding()).isZero();
        assertThat(c2.getAmountOutstanding()).isEqualByComparingTo("30");

        ArgumentCaptor<LineOfCreditChargePaidBy> captor = ArgumentCaptor.forClass(LineOfCreditChargePaidBy.class);
        verify(paidByRepo, times(2)).save(captor.capture());
        List<LineOfCreditChargePaidBy> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        BigDecimal totalApplied = saved.stream().map(LineOfCreditChargePaidBy::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalApplied).isEqualByComparingTo("100");
    }
}
