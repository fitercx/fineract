package com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditCreatedBusinessEvent extends LineOfCreditBusinessEvent {

    private static final String type = "LineOfCreditCreatedBusinessEvent";

    public LineOfCreditCreatedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}
