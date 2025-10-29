package com.crediblex.fineract.portfolio.loc.domain;

public enum LineOfCreditTransactionType {

    DISBURSEMENT, UNDO_DISBURSEMENT, REPAYMENT, REFUND, REVERSAL, INCREMENT, DECREMENT, FORECLOSURE, WRITE_OFF;

    public boolean isDisbursement() {
        return this == DISBURSEMENT;
    }

    public boolean isRepayment() {
        return this == REPAYMENT;
    }

    public boolean isRefund() {
        return this == REFUND;
    }

    public boolean isIncrement() {
        return this == INCREMENT;
    }

    public boolean isDecrement() {
        return this == DECREMENT;
    }

    public boolean isForeclosure() {
        return this == FORECLOSURE;
    }

    public boolean isWriteOff() {
        return this == WRITE_OFF;
    }

    public boolean isReversal() {
        return this == REVERSAL;
    }

    public boolean isUndoDisbursement() {
        return this == UNDO_DISBURSEMENT;
    }

    public boolean isBalanceIncrement() {
        return isIncrement();
    }

    public boolean isBalanceDecrement() {
        return isDecrement();
    }

    public boolean isDecrementTransaction() {
        return isDisbursement() || isDecrement() || isReversal();
    }

    public boolean isIncrementTransaction() {
        return isRepayment() || isRefund() || isIncrement() || isForeclosure() || isWriteOff() || isUndoDisbursement();
    }
}
