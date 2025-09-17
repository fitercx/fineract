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
}
