package com.crediblex.fineract.accounting.journalentry.journalentry;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;

@Getter
@Setter
public class CustomLoanDTO extends LoanDTO {

    public CustomLoanDTO(Long loanId, Long loanProductId, Long officeId, String currencyCode, boolean cashBasedAccountingEnabled,
            boolean upfrontAccrualBasedAccountingEnabled, boolean periodicAccrualBasedAccountingEnabled,
            List<LoanTransactionDTO> newLoanTransactions, boolean markedAsChargeOff, boolean markedAsFraud, Long chargeOffReasonCodeValue,
            BigDecimal netDisbursalAmount) {
        super(loanId, loanProductId, officeId, currencyCode, cashBasedAccountingEnabled, upfrontAccrualBasedAccountingEnabled,
                periodicAccrualBasedAccountingEnabled, newLoanTransactions, markedAsChargeOff, markedAsFraud, chargeOffReasonCodeValue);
        this.netDisbursalAmount = netDisbursalAmount;
    }

    private BigDecimal netDisbursalAmount;

    // LOC receivable specific fields for upfront accrual
    private boolean isLocReceivable = false;
    private BigDecimal totalContractualInterest = BigDecimal.ZERO;
    private BigDecimal totalDisbursementFees = BigDecimal.ZERO;
    private BigDecimal totalDisbursementFeesTax = BigDecimal.ZERO;

    // Track total accrued interest up to this point (sum of all accrual transactions) for unwinding deferred income on
    // early payments
    private BigDecimal totalAccruedInterest = BigDecimal.ZERO;

    // Track total interest charged on installments up to current transaction date for calculating unaccrued interest
    private BigDecimal totalInterestCharged = BigDecimal.ZERO;
}
