package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditDeactivatedBusinessEvent extends LineOfCreditBusinessEvent{
    private static final String type = "LineOfCreditDeactivatedBusinessEvent";

    public LineOfCreditDeactivatedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}