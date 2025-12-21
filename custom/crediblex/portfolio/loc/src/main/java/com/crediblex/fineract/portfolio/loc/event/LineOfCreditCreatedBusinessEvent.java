package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanBusinessEvent;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

public class LineOfCreditCreatedBusinessEvent extends LineOfCreditBusinessEvent{

    private static final String type = "LineOfCreditCreatedBusinessEvent";

    public LineOfCreditCreatedBusinessEvent (LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}