package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeDataDTO;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeLoanTransactionDTO;

@Getter
@Setter
public class CustomAccountingBridgeDataDTO extends AccountingBridgeDataDTO {

    private BigDecimal principalPortion;
    private BigDecimal feesPortion;
    private BigDecimal vatPortion;
    private BigDecimal netDisbursalAmount;

    public CustomAccountingBridgeDataDTO() {
        super();
    }

    public CustomAccountingBridgeDataDTO(Long loanId, Long loanProductId, Long officeId, String currencyCode, BigDecimal calculatedInterest,
            Boolean cashBasedAccountingEnabled, Boolean upfrontAccrualBasedAccountingEnabled, Boolean periodicAccrualBasedAccountingEnabled,
            boolean isAccountTransfer, boolean isChargeOff, boolean isFraud, Long chargeOffReasonCodeValue,
            List<AccountingBridgeLoanTransactionDTO> newLoanTransactions, BigDecimal principalPortion, BigDecimal feesPortion,
            BigDecimal vatPortion, BigDecimal netDisbursalAmount) {
        super(loanId, loanProductId, officeId, currencyCode, calculatedInterest, cashBasedAccountingEnabled,
                upfrontAccrualBasedAccountingEnabled, periodicAccrualBasedAccountingEnabled, isAccountTransfer, isChargeOff, isFraud,
                chargeOffReasonCodeValue, newLoanTransactions);
        this.principalPortion = principalPortion;
        this.feesPortion = feesPortion;
        this.vatPortion = vatPortion;
        this.netDisbursalAmount = netDisbursalAmount;
    }

}
