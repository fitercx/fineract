package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeDataDTO;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeLoanTransactionDTO;

@Getter
@Setter
@NoArgsConstructor
public class CustomAccountingBridgeDataDTO extends AccountingBridgeDataDTO {

    private BigDecimal netDisbursalAmount;

    public CustomAccountingBridgeDataDTO(Long loanId, Long loanProductId, Long officeId, String currencyCode, BigDecimal calculatedInterest, boolean cashBasedAccountingEnabled, boolean upfrontAccrualBasedAccountingEnabled, boolean periodicAccrualBasedAccountingEnabled, boolean isAccountTransfer, boolean isChargeOff, boolean isFraud, Long chargeOffReasonCodeValue, List<AccountingBridgeLoanTransactionDTO> newLoanTransactions,BigDecimal netDisbursalAmount) {
        super(loanId, loanProductId, officeId, currencyCode, calculatedInterest, cashBasedAccountingEnabled, upfrontAccrualBasedAccountingEnabled, periodicAccrualBasedAccountingEnabled, isAccountTransfer, isChargeOff, isFraud, chargeOffReasonCodeValue, newLoanTransactions);
        this.netDisbursalAmount = netDisbursalAmount;
    }

    @Override
    public void setNewLoanTransactions(List<AccountingBridgeLoanTransactionDTO> newLoanTransactions) {
        super.setNewLoanTransactions(newLoanTransactions);
    }

    @Override
    public void setChargeOffReasonCodeValue(Long chargeOffReasonCodeValue) {
        super.setChargeOffReasonCodeValue(chargeOffReasonCodeValue);
    }

    @Override
    public void setFraud(boolean isFraud) {
        super.setFraud(isFraud);
    }

    @Override
    public void setChargeOff(boolean isChargeOff) {
        super.setChargeOff(isChargeOff);
    }

    @Override
    public void setAccountTransfer(boolean isAccountTransfer) {
        super.setAccountTransfer(isAccountTransfer);
    }

    @Override
    public void setPeriodicAccrualBasedAccountingEnabled(boolean periodicAccrualBasedAccountingEnabled) {
        super.setPeriodicAccrualBasedAccountingEnabled(periodicAccrualBasedAccountingEnabled);
    }

    @Override
    public void setUpfrontAccrualBasedAccountingEnabled(boolean upfrontAccrualBasedAccountingEnabled) {
        super.setUpfrontAccrualBasedAccountingEnabled(upfrontAccrualBasedAccountingEnabled);
    }

    @Override
    public void setCashBasedAccountingEnabled(boolean cashBasedAccountingEnabled) {
        super.setCashBasedAccountingEnabled(cashBasedAccountingEnabled);
    }

    @Override
    public void setCalculatedInterest(BigDecimal calculatedInterest) {
        super.setCalculatedInterest(calculatedInterest);
    }

    @Override
    public void setCurrencyCode(String currencyCode) {
        super.setCurrencyCode(currencyCode);
    }

    @Override
    public void setOfficeId(Long officeId) {
        super.setOfficeId(officeId);
    }

    @Override
    public void setLoanProductId(Long loanProductId) {
        super.setLoanProductId(loanProductId);
    }

    @Override
    public void setLoanId(Long loanId) {
        super.setLoanId(loanId);
    }
}
