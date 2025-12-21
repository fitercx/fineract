package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditUpdatedBusinessEvent extends LineOfCreditBusinessEvent {

    private static final String type = "LineOfCreditUpdatedBusinessEvent";

    public LineOfCreditUpdatedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}
