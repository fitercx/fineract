package com.crediblex.fineract.portfolio.loanaccount.domain;

import java.util.Collection;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;
import org.springframework.stereotype.Service;

@Service
public class OverdueChargeHelper {

    private final LoanChargeWritePlatformService loanChargeWritePlatformService;

    public OverdueChargeHelper(LoanChargeWritePlatformService loanChargeWritePlatformService) {
        this.loanChargeWritePlatformService = loanChargeWritePlatformService;
    }

    public void applyOverdueChargesForSingleLoan(Long loanId, Long penaltyWaitPeriodValue, Boolean backdatePenalties,
            Collection<OverdueLoanScheduleData> allOverdueInstallments) {

        List<OverdueLoanScheduleData> installmentsForLoan = allOverdueInstallments.stream()
                .filter(installment -> loanId.equals(installment.getLoanId())).toList();

        if (!installmentsForLoan.isEmpty()) {
            loanChargeWritePlatformService.applyOverdueChargesForLoan(loanId, installmentsForLoan);
        }
    }
}
