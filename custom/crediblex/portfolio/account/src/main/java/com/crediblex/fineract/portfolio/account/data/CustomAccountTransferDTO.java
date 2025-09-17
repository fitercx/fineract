package com.crediblex.fineract.portfolio.account.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;

public class CustomAccountTransferDTO extends AccountTransferDTO {

    @Getter
    @Setter
    private BigDecimal netLoanDisbursementAmount;

    public CustomAccountTransferDTO(LocalDate transactionDate, BigDecimal transactionAmount, PortfolioAccountType fromAccountType,
            PortfolioAccountType toAccountType, Long fromAccountId, Long toAccountId, String description, Locale locale,
            DateTimeFormatter fmt, PaymentDetail paymentDetail, Integer fromTransferType, Integer toTransferType, Long chargeId,
            Integer loanInstallmentNumber, Integer transferType, AccountTransferDetails accountTransferDetails, String noteText,
            ExternalId txnExternalId, Loan loan, SavingsAccount toSavingsAccount, SavingsAccount fromSavingsAccount,
            Boolean isRegularTransaction, Boolean isExceptionForBalanceCheck) {
        super(transactionDate, transactionAmount, fromAccountType, toAccountType, fromAccountId, toAccountId, description, locale, fmt,
                paymentDetail, fromTransferType, toTransferType, chargeId, loanInstallmentNumber, transferType, accountTransferDetails,
                noteText, txnExternalId, loan, toSavingsAccount, fromSavingsAccount, isRegularTransaction, isExceptionForBalanceCheck);
    }

    public CustomAccountTransferDTO(LocalDate transactionDate, BigDecimal transactionAmount, PortfolioAccountType fromAccountType,
            PortfolioAccountType toAccountType, Long fromAccountId, Long toAccountId, String description, Locale locale,
            DateTimeFormatter fmt, Integer fromTransferType, Integer toTransferType, ExternalId txnExternalId, Loan fromLoan, Loan toLoan) {
        super(transactionDate, transactionAmount, fromAccountType, toAccountType, fromAccountId, toAccountId, description, locale, fmt,
                fromTransferType, toTransferType, txnExternalId, fromLoan, toLoan);
    }
}
