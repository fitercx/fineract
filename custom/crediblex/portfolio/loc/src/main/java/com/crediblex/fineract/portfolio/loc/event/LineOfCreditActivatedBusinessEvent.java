package com.crediblex.fineract.portfolio.loc.event;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;

public class LineOfCreditActivatedBusinessEvent extends LineOfCreditBusinessEvent {

    private static final String type = "LineOfCreditActivatedBusinessEvent";

    public LineOfCreditActivatedBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getType() {
        return type;
    }
}