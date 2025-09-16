package com.crediblex.fineract.portfolio.loc.charge.listener;

import com.crediblex.fineract.portfolio.loc.charge.service.LocChargeAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.event.business.BusinessEventListener;
import org.apache.fineract.infrastructure.event.business.domain.savings.transaction.SavingsWithdrawalBusinessEvent;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocChargeSavingsWithdrawalListener implements BusinessEventListener<SavingsWithdrawalBusinessEvent> {

    private final LocChargeAllocationService allocationService;

    @Override
    public void onBusinessEvent(SavingsWithdrawalBusinessEvent event) {
        try {
            SavingsAccountTransaction txn = event.get();
            if (txn != null && txn.isReversalTransaction()) {
                allocationService.reverseForSavingsReversal(txn);
            } else {
                allocationService.allocateForSavingsWithdrawal(txn);
            }
        } catch (Exception e) {
            log.error("LOC charge allocation listener error", e);
        }
    }
}
