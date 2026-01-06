package com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditReactivatedBusinessEvent extends LineOfCreditBusinessEvent {

    private static final String type = "LineOfCreditReactivatedBusinessEvent";

    public LineOfCreditReactivatedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}
