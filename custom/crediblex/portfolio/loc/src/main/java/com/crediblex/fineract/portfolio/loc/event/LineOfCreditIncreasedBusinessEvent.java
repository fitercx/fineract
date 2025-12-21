package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditIncreasedBusinessEvent extends LineOfCreditBusinessEvent{
    private static final String type = "LineOfCreditIncreasedBusinessEvent";

    public LineOfCreditIncreasedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}
