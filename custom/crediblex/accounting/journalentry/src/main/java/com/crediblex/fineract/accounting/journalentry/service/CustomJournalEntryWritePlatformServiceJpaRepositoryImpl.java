package com.crediblex.fineract.accounting.journalentry.service;

import com.crediblex.fineract.accounting.journalentry.CustomAccountingProcessorHelper;
import com.crediblex.fineract.accounting.journalentry.data.LineOfCreditDTO;
import java.util.Map;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.serialization.JournalEntryCommandFromApiJsonDeserializer;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorForLoanFactory;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorForSavingsFactory;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorForSharesFactory;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.CashBasedAccountingProcessorForClientTransactions;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.accounting.rule.domain.AccountingRuleRepository;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomJournalEntryWritePlatformServiceJpaRepositoryImpl extends JournalEntryWritePlatformServiceJpaRepositoryImpl {

    private final AccountingProcessorForLineOfCredit accountingProcessorForLineOfCredit;

    public CustomJournalEntryWritePlatformServiceJpaRepositoryImpl(GLClosureRepository glClosureRepository,
            GLAccountRepository glAccountRepository, JournalEntryRepository glJournalEntryRepository,
            OfficeRepositoryWrapper officeRepositoryWrapper, AccountingProcessorForLoanFactory accountingProcessorForLoanFactory,
            AccountingProcessorForSavingsFactory accountingProcessorForSavingsFactory,
            AccountingProcessorForSharesFactory accountingProcessorForSharesFactory, AccountingProcessorHelper helper,
            JournalEntryCommandFromApiJsonDeserializer fromApiJsonDeserializer, AccountingRuleRepository accountingRuleRepository,
            GLAccountReadPlatformService glAccountReadPlatformService, OrganisationCurrencyRepositoryWrapper organisationCurrencyRepository,
            PlatformSecurityContext context, PaymentDetailWritePlatformService paymentDetailWritePlatformService,
            FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper,
            CashBasedAccountingProcessorForClientTransactions accountingProcessorForClientTransactions,
            AccountingProcessorForLineOfCredit accountingProcessorForLineOfCredit) {
        super(glClosureRepository, glAccountRepository, glJournalEntryRepository, officeRepositoryWrapper,
                accountingProcessorForLoanFactory, accountingProcessorForSavingsFactory, accountingProcessorForSharesFactory, helper,
                fromApiJsonDeserializer, accountingRuleRepository, glAccountReadPlatformService, organisationCurrencyRepository, context,
                paymentDetailWritePlatformService, financialActivityAccountRepositoryWrapper, accountingProcessorForClientTransactions);
        this.accountingProcessorForLineOfCredit = accountingProcessorForLineOfCredit;
    }

    @Override
    public void createJournalEntriesForLineOfCredit(Map<String, Object> accountingBridgeData) {
        final boolean cashBasedAccountingEnabled = (Boolean) accountingBridgeData.get("cashBasedAccountingEnabled");
        final boolean accrualBasedAccountingEnabled = (Boolean) accountingBridgeData.get("accrualBasedAccountingEnabled");

        if (cashBasedAccountingEnabled || accrualBasedAccountingEnabled) {

            final LineOfCreditDTO lineOfCreditDto = ((CustomAccountingProcessorHelper) this.helper)
                    .populateLineOfCreditDtoFromMap(accountingBridgeData, cashBasedAccountingEnabled, accrualBasedAccountingEnabled);

            // Currently only handling tax on activation charge
            accountingProcessorForLineOfCredit.createJournalEntriesForLineOfCreditActivationCharge(lineOfCreditDto);

        }
    }

}
