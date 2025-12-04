package com.crediblex.fineract.infrastructure.events.business.domain.accounttransfer;

import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;

public class SavingsToLoanAccountTransferBusinessEvent extends AccountTransferBusinessEvent {

    private static final String TYPE = "LoanDisbursalBusinessEvent";

    public SavingsToLoanAccountTransferBusinessEvent(AccountTransferDetails value) {
        super(value);
    }

    @Override
    public String getType() {
        return TYPE;
    }

}
