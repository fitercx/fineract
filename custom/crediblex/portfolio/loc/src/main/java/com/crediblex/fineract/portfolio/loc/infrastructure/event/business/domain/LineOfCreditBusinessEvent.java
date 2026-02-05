// Base event class
package com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import org.apache.fineract.infrastructure.event.business.domain.AbstractBusinessEvent;

public abstract class LineOfCreditBusinessEvent extends AbstractBusinessEvent<LineOfCredit> {

    private static final String CATEGORY = "LineOfCredit";

    public LineOfCreditBusinessEvent(LineOfCredit lineOfCredit) {
        super(lineOfCredit);
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Long getAggregateRootId() {
        return get().getId();
    }
}
