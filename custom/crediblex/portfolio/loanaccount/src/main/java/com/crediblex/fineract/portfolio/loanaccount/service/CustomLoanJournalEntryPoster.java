package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.data.CustomAccountingBridgeDataDTO;
import com.crediblex.fineract.portfolio.loanaccount.mapper.CustomLoanAccountingBridgeMapper;
import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanJournalEntryPoster;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CustomLoanJournalEntryPoster extends LoanJournalEntryPoster {

    private final JournalEntryWritePlatformService journalEntryWritePlatformService;
    private final CustomLoanAccountingBridgeMapper customLoanAccountingBridgeMapper;

    public CustomLoanJournalEntryPoster(JournalEntryWritePlatformService journalEntryWritePlatformService,
            LoanAccountingBridgeMapper loanAccountingBridgeMapper, CustomLoanAccountingBridgeMapper customLoanAccountingBridgeMapper) {
        super(journalEntryWritePlatformService, loanAccountingBridgeMapper);
        this.journalEntryWritePlatformService = journalEntryWritePlatformService;
        this.customLoanAccountingBridgeMapper = customLoanAccountingBridgeMapper;
    }

    @Override
    public void postJournalEntries(final Loan loan, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds) {
        final MonetaryCurrency currency = loan.getCurrency();
        boolean isAccountTransfer = false;

        if (loan.isChargedOff()) {
            List<CustomAccountingBridgeDataDTO> accountingBridgeDataList = customLoanAccountingBridgeMapper
                    .deriveAccountingBridgeDataForChargeOff(currency.getCode(), existingTransactionIds, existingReversedTransactionIds,
                            isAccountTransfer, loan);
            for (CustomAccountingBridgeDataDTO accountingBridgeData : accountingBridgeDataList) {
                this.journalEntryWritePlatformService.createJournalEntriesForLoan(accountingBridgeData);
            }
        } else {
            BigDecimal principal = loan.getPrincipal().getAmount();
            com.crediblex.fineract.portfolio.loanaccount.domain.LoanWrapper loanWrapper = new com.crediblex.fineract.portfolio.loanaccount.domain.LoanWrapper(
                    loan);
            BigDecimal totalFees = loanWrapper.deriveTotalFeeChargesDueAtDisbursement();
            BigDecimal totalVat = loanWrapper.deriveTotalVATChargesDueAtDisbursement();
            BigDecimal netDisbursal = principal.subtract(totalFees).subtract(totalVat);

            // Prepare the accounting bridge DTO with the breakdown
            CustomAccountingBridgeDataDTO accountingBridgeData = customLoanAccountingBridgeMapper.deriveAccountingBridgeData(
                    currency.getCode(), existingTransactionIds, existingReversedTransactionIds, isAccountTransfer, loan);

            // Set the breakdown fields
            accountingBridgeData.setPrincipalPortion(principal);
            accountingBridgeData.setFeesPortion(totalFees);
            accountingBridgeData.setVatPortion(totalVat);
            accountingBridgeData.setNetDisbursalAmount(netDisbursal);

            // ToDo: Post the journal entries using the breakdown
            // this.journalEntryWritePlatformService.createJournalEntriesForLoan(accountingBridgeData);
        }
    }
}
