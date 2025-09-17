package com.crediblex.fineract.accounting.journalentry;

import com.crediblex.fineract.accounting.journalentry.service.CustomAccrualBasedAccountingProcessorForLoan;
import com.crediblex.fineract.accounting.journalentry.service.CustomCashBasedAccountingProcessorForLoan;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CustomJournalEntryConfigurationBeans {

    @Bean
    @Primary
    public AccountingProcessorHelper accountingProcessorHelper(JournalEntryRepository glJournalEntryRepository,
            ProductToGLAccountMappingRepository accountMappingRepository,
            FinancialActivityAccountRepositoryWrapper financialActivityAccountRepository, GLClosureRepository closureRepository,
            GLAccountRepository glAccountRepository, OfficeRepository officeRepository,
            AccountTransfersReadPlatformService accountTransfersReadPlatformService, ChargeRepositoryWrapper chargeRepositoryWrapper,
            BusinessEventNotifierService businessEventNotifierService) {
        return new CustomAccountingProcessorHelper(glJournalEntryRepository, accountMappingRepository, financialActivityAccountRepository,
                closureRepository, glAccountRepository, officeRepository, accountTransfersReadPlatformService, chargeRepositoryWrapper,
                businessEventNotifierService);
    }

    @Bean
    @Primary
    public CustomAccrualBasedAccountingProcessorForLoan accrualBasedAccountingProcessorForLoan(AccountingProcessorHelper helper,
            JournalEntryWritePlatformService journalEntryWritePlatformService) {
        return new CustomAccrualBasedAccountingProcessorForLoan(helper, journalEntryWritePlatformService);
    }

    @Bean
    @Primary
    public CustomCashBasedAccountingProcessorForLoan cashBasedAccountingProcessorForLoan(AccountingProcessorHelper helper,
            JournalEntryWritePlatformService journalEntryWritePlatformService) {
        return new CustomCashBasedAccountingProcessorForLoan(helper, journalEntryWritePlatformService);
    }

}
