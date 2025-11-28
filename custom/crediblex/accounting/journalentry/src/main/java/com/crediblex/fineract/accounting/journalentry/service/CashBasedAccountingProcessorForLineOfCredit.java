package com.crediblex.fineract.accounting.journalentry.service;

import com.crediblex.fineract.accounting.journalentry.data.LineOfCreditDTO;
import com.crediblex.fineract.accounting.journalentry.data.TaxPaymentDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashBasedAccountingProcessorForLineOfCredit implements AccountingProcessorForLineOfCredit {

    private final AccountingProcessorHelper helper;
    private final GLAccountRepository glAccountRepository;
    private final OfficeRepository officeRepository;

    @Override
    public void createJournalEntriesForLineOfCreditActivationCharge(LineOfCreditDTO lineOfCreditDTO) {
        final Office office = officeRepository.findById(lineOfCreditDTO.getOfficeId())
                .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.office.not.found",
                        "Office not found for ID: " + lineOfCreditDTO.getOfficeId()));

        final String currencyCode = lineOfCreditDTO.getCurrencyCode();
        final Long savingsAccountId = lineOfCreditDTO.getSavingsAccountId();
        final String transactionId = lineOfCreditDTO.getSavingsAccountTransactionId().toString();
        final boolean isReversal = lineOfCreditDTO.isReversed();
        final LocalDate transactionDate = lineOfCreditDTO.getTransactionDate();

        // Create journal entries for each tax payment
        for (TaxPaymentDTO taxPayment : lineOfCreditDTO.getTaxPayments()) {
            final BigDecimal amount = taxPayment.getAmount();

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {

                final GLAccount debitAccount = glAccountRepository
                        .findById(isReversal ? taxPayment.getCreditAccountId() : taxPayment.getDebitAccountId())
                        .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.glaccount.not.found",
                                "GL Account not found for ID: " + taxPayment.getDebitAccountId()));

                // Get the credit account
                final GLAccount creditAccount = glAccountRepository
                        .findById(isReversal ? taxPayment.getDebitAccountId() : taxPayment.getCreditAccountId())
                        .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.glaccount.not.found",
                                "GL Account not found for ID: " + taxPayment.getCreditAccountId()));

                helper.createDebitJournalEntryForSavings(office, currencyCode, debitAccount, savingsAccountId, transactionId,
                        transactionDate, amount);
                helper.createCreditJournalEntryForSavings(office, currencyCode, creditAccount, savingsAccountId, transactionId,
                        transactionDate, amount);

            }
        }
    }

}
