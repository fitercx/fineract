package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor;

import com.crediblex.fineract.portfolio.loanaccount.util.OverduePenaltyChargeOrder;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.OverduePenaltyChargeOrderHook;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.OverduePenaltyChargeOrderRegistry;
import org.springframework.stereotype.Component;

/**
 * Pays LPI charges installment by installment when several installments are overdue: the oldest installment's charges
 * first, earliest day first, and only then the next installment. Runs for every repayment strategy.
 */
@Component
public class OverduePenaltyChargeOrderHookImpl implements OverduePenaltyChargeOrderHook {

    @PostConstruct
    public void register() {
        OverduePenaltyChargeOrderRegistry.register(this);
    }

    @Override
    public LoanCharge nextUnpaid(final Set<LoanCharge> charges, final MonetaryCurrency currency) {
        return OverduePenaltyChargeOrder.nextUnpaid(charges, currency);
    }
}
