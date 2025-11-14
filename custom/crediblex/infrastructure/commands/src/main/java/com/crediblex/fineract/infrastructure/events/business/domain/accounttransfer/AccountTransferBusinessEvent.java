package com.crediblex.fineract.infrastructure.events.business.domain.accounttransfer;

import org.apache.fineract.infrastructure.event.business.domain.AbstractBusinessEvent;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;

public abstract class AccountTransferBusinessEvent extends AbstractBusinessEvent<AccountTransferDetails> {

    private static final String CATEGORY = "AccountTransfer";

    public AccountTransferBusinessEvent(AccountTransferDetails value) {
        super(value);
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
