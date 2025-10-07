package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.util.Optional;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.holiday.domain.HolidayRepository;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.collateralmanagement.service.LoanCollateralAssembler;
import org.apache.fineract.portfolio.fund.domain.FundRepository;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleAssembler;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleCalculationPlatformService;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanChargeMapper;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanCollateralManagementMapper;
import org.apache.fineract.portfolio.loanaccount.service.GLIMAccountInfoWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssemblerImpl;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementDetailsAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.LoanOfficerService;
import org.apache.fineract.portfolio.loanaccount.service.schedule.LoanScheduleComponent;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.rate.service.RateAssembler;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CustomLoanAssemblerImpl extends LoanAssemblerImpl {

    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    public CustomLoanAssemblerImpl(FromJsonHelper fromApiJsonHelper, LoanRepositoryWrapper loanRepository,
            LoanProductRepository loanProductRepository, ClientRepositoryWrapper clientRepository, GroupRepositoryWrapper groupRepository,
            FundRepository fundRepository, StaffRepository staffRepository, CodeValueRepositoryWrapper codeValueRepository,
            LoanScheduleAssembler loanScheduleAssembler, LoanChargeAssembler loanChargeAssembler,
            LoanCollateralAssembler collateralAssembler,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            HolidayRepository holidayRepository, ConfigurationDomainService configurationDomainService,
            WorkingDaysRepositoryWrapper workingDaysRepository, RateAssembler rateAssembler,
            LoanLifecycleStateMachine defaultLoanLifecycleStateMachine, ExternalIdFactory externalIdFactory,
            AccountNumberFormatRepositoryWrapper accountNumberFormatRepository, GLIMAccountInfoRepository glimRepository,
            AccountNumberGenerator accountNumberGenerator, GLIMAccountInfoWritePlatformService glimAccountInfoWritePlatformService,
            LoanCollateralAssembler loanCollateralAssembler, LoanScheduleCalculationPlatformService calculationPlatformService,
            LoanDisbursementDetailsAssembler loanDisbursementDetailsAssembler, LoanChargeMapper loanChargeMapper,
            LoanCollateralManagementMapper loanCollateralManagementMapper, LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanDisbursementService loanDisbursementService, LoanChargeService loanChargeService, LoanOfficerService loanOfficerService,
            LoanScheduleComponent loanSchedule, LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(fromApiJsonHelper, loanRepository, loanProductRepository, clientRepository, groupRepository, fundRepository, staffRepository,
                codeValueRepository, loanScheduleAssembler, loanChargeAssembler, collateralAssembler,
                loanRepaymentScheduleTransactionProcessorFactory, holidayRepository, configurationDomainService, workingDaysRepository,
                rateAssembler, defaultLoanLifecycleStateMachine, externalIdFactory, accountNumberFormatRepository, glimRepository,
                accountNumberGenerator, glimAccountInfoWritePlatformService, loanCollateralAssembler, calculationPlatformService,
                loanDisbursementDetailsAssembler, loanChargeMapper, loanCollateralManagementMapper, loanAccrualsProcessingService,
                loanDisbursementService, loanChargeService, loanOfficerService, loanSchedule);

        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    public Loan assembleFrom(final Long accountId) {
        return assembleFrom(accountId, true);
    }

    @Override
    public Loan assembleFrom(final Long accountId, final boolean loadLazyCollections) {
        final Loan loanAccount = loanRepository.findOneWithNotFoundDetection(accountId, loadLazyCollections);
        setHelpers(loanAccount);
        Optional<LoanLineOfCreditParams> lineOfCreditParams = loanLineOfCreditParamsRepository.findByLoanId(loanAccount.getId());
        loanAccount.setReceivableLocLoan(
                lineOfCreditParams.isPresent() && lineOfCreditParams.get().getLineOfCredit().getProductType().isReceivable());
        return loanAccount;
    }

}
