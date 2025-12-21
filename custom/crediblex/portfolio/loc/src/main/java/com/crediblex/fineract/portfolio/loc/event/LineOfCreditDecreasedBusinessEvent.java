package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditDecreasedBusinessEvent extends LineOfCreditBusinessEvent {
    private static final String type = "LineOfCreditDecreasedBusinessEvent";

    public LineOfCreditDecreasedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}
