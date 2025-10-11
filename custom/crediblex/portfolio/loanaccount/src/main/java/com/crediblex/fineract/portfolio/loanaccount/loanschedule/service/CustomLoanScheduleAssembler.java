package com.crediblex.fineract.portfolio.loanaccount.loanschedule.service;

import com.google.gson.JsonElement;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.holiday.domain.HolidayRepository;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.floatingrates.service.FloatingRatesReadPlatformService;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.AprCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleGeneratorFactory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleAssembler;
import org.apache.fineract.portfolio.loanaccount.serialization.VariableLoanScheduleFromApiJsonValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementDetailsAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.LoanProductRelatedDetailUpdateUtil;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanScheduleAssembler extends LoanScheduleAssembler {

    public CustomLoanScheduleAssembler(FromJsonHelper fromApiJsonHelper, LoanProductRepository loanProductRepository,
            ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository, LoanChargeAssembler loanChargeAssembler,
            LoanScheduleGeneratorFactory loanScheduleFactory, AprCalculator aprCalculator, CalendarRepository calendarRepository,
            HolidayRepository holidayRepository, ConfigurationDomainService configurationDomainService,
            ClientRepositoryWrapper clientRepository, GroupRepositoryWrapper groupRepository,
            WorkingDaysRepositoryWrapper workingDaysRepository, FloatingRatesReadPlatformService floatingRatesReadPlatformService,
            VariableLoanScheduleFromApiJsonValidator variableLoanScheduleFromApiJsonValidator,
            CalendarInstanceRepository calendarInstanceRepository, LoanUtilService loanUtilService,
            LoanDisbursementDetailsAssembler loanDisbursementDetailsAssembler, LoanRepositoryWrapper loanRepositoryWrapper,
            LoanLifecycleStateMachine defaultLoanLifecycleStateMachine, LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanDisbursementService loanDisbursementService, LoanChargeService loanChargeService, LoanScheduleService loanScheduleService,
            LoanProductRelatedDetailUpdateUtil relatedDetailUpdateUtil) {
        super(fromApiJsonHelper, loanProductRepository, applicationCurrencyRepository, loanChargeAssembler, loanScheduleFactory,
                aprCalculator, calendarRepository, holidayRepository, configurationDomainService, clientRepository, groupRepository,
                workingDaysRepository, floatingRatesReadPlatformService, variableLoanScheduleFromApiJsonValidator,
                calendarInstanceRepository, loanUtilService, loanDisbursementDetailsAssembler, loanRepositoryWrapper,
                defaultLoanLifecycleStateMachine, loanAccrualsProcessingService, loanDisbursementService, loanChargeService,
                loanScheduleService, relatedDetailUpdateUtil);
    }

    @Override
    protected LoanApplicationTerms assembleLoanApplicationTermsFrom(JsonElement element, LoanProduct loanProduct) {
        LoanApplicationTerms terms = super.assembleLoanApplicationTermsFrom(element, loanProduct);

        if (loanProduct.isLocEnabled() && element.getAsJsonObject().has("lineOfCreditId")) {

            terms.setIsLineOfCredit(true);
            terms.setIsReceivableLineOfCredit(true);

        }

        return terms;
    }
}
