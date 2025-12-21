package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditApprovedBusinessEvent extends LineOfCreditBusinessEvent {

    private static final String type = "LineOfCreditApprovedBusinessEvent";

    public LineOfCreditApprovedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}