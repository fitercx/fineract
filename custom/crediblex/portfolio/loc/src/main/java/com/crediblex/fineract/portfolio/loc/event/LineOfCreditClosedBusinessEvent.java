package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditClosedBusinessEvent extends LineOfCreditBusinessEvent {
    private static final String type = "LineOfCreditClosedBusinessEvent";

    public LineOfCreditClosedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}
