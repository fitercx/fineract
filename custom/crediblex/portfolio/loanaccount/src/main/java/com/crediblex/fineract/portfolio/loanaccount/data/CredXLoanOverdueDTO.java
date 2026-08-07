package com.crediblex.fineract.portfolio.loanaccount.data;

import java.time.LocalDate;
import lombok.Getter;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanproduct.data.LoanOverdueDTO;

/**
 * CredX extension of {@link LoanOverdueDTO} that records whether any overdue penalty charge was actually posted in this
 * apply pass. Used to skip expensive reprocess / journal posting when the LPI job ran but had nothing new to accrue.
 */
@Getter
public class CredXLoanOverdueDTO extends LoanOverdueDTO {

    private final boolean chargesApplied;

    public CredXLoanOverdueDTO(final Loan loan, final boolean runInterestRecalculation, final LocalDate recalculateFrom,
            final LocalDate lastChargeAppliedDate, final boolean chargesApplied) {
        super(loan, runInterestRecalculation, recalculateFrom, lastChargeAppliedDate);
        this.chargesApplied = chargesApplied;
    }
}
