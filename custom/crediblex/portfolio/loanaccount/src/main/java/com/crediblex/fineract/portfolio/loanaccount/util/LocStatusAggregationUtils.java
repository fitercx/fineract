package com.crediblex.fineract.portfolio.loanaccount.util;

import com.crediblex.fineract.portfolio.loanaccount.data.LocStatusAggregationData;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loc.domain.CustomLocStatus;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.stereotype.Component;

/**
 * Utility functions for aggregating Line of Credit (LOC) custom statuses from underlying drawdown (loan) custom
 * statuses.
 *
 * Used by: - CustomLoanWritePlatformServiceJpaRepositoryImpl (repayment/disbursement flows) - Any future LOC status
 * evaluators/webhook publishers needing a single source of truth
 */

@Component
@RequiredArgsConstructor
public class LocStatusAggregationUtils {

    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    public LocStatusAggregationData computeLocStatusAggregationData(LineOfCredit loc, Loan updatedLoan) {
        boolean anyPastMaturity = false;
        boolean anyPastDue = false;

        for (LoanLineOfCreditParams invoice : loanLineOfCreditParamsRepository.findAllByLineOfCredit_Id(loc.getId())) {
            Loan drawdown = invoice.getLoan();
            if (drawdown == null) {
                continue;
            }

            CustomLoanStatus drawdownStatus = Objects.equals(updatedLoan.getId(), drawdown.getId()) ? updatedLoan.getCustomLoanStatus()
                    : drawdown.getCustomLoanStatus();

            if (drawdownStatus != null && drawdownStatus.isPastMaturity()) {
                anyPastMaturity = true;
                break;
            }
            if (drawdownStatus != null && drawdownStatus.isPastDue()) {
                anyPastDue = true;
            }
        }

        CustomLocStatus newLocCustomStatus;
        if (anyPastMaturity) {
            newLocCustomStatus = CustomLocStatus.PAST_MATURITY;
        } else if (anyPastDue) {
            newLocCustomStatus = CustomLocStatus.PAST_DUE;
        } else {
            newLocCustomStatus = CustomLocStatus.INVALID;
        }

        CustomLocStatus oldLocCustomStatus = loc.getCustomLocStatus();
        loc.setCustomLocStatus(newLocCustomStatus);

        return LocStatusAggregationData.build(loc.getStatus(), oldLocCustomStatus, newLocCustomStatus);
    }
}
