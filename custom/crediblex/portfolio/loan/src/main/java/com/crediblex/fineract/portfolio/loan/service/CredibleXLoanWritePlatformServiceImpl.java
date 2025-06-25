package com.crediblex.fineract.portfolio.loan.service;

import com.google.gson.JsonElement;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.cob.service.LoanAccountLockService;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.domain.AccountAssociationsRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarRepository;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.organisation.teller.data.CashierTransactionDataValidator;
import org.apache.fineract.portfolio.loanaccount.guarantor.service.GuarantorDomainService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.*;
import org.apache.fineract.portfolio.loanaccount.service.*;
import org.apache.fineract.portfolio.loanaccount.service.adjustment.LoanAdjustmentService;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecksRepository;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.service.RepaymentWithPostDatedChecksAssembler;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Primary
public class CredibleXLoanWritePlatformServiceImpl extends LoanWritePlatformServiceJpaRepositoryImpl {

    private final JdbcTemplate jdbcTemplate;

    public CredibleXLoanWritePlatformServiceImpl(PlatformSecurityContext context, LoanTransactionValidator loanTransactionValidator, LoanUpdateCommandFromApiJsonDeserializer loanUpdateCommandFromApiJsonDeserializer, LoanRepositoryWrapper loanRepositoryWrapper, LoanAccountDomainService loanAccountDomainService, NoteRepository noteRepository, LoanTransactionRepository loanTransactionRepository, LoanTransactionRelationRepository loanTransactionRelationRepository, LoanAssembler loanAssembler, JournalEntryWritePlatformService journalEntryWritePlatformService, CalendarInstanceRepository calendarInstanceRepository, PaymentDetailWritePlatformService paymentDetailWritePlatformService, HolidayRepositoryWrapper holidayRepository, ConfigurationDomainService configurationDomainService, WorkingDaysRepositoryWrapper workingDaysRepository, AccountTransfersWritePlatformService accountTransfersWritePlatformService, AccountTransfersReadPlatformService accountTransfersReadPlatformService, AccountAssociationsReadPlatformService accountAssociationsReadPlatformService, LoanReadPlatformService loanReadPlatformService, FromJsonHelper fromApiJsonHelper, CalendarRepository calendarRepository, LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService, LoanApplicationValidator loanApplicationValidator, AccountAssociationsRepository accountAssociationRepository, AccountTransferDetailRepository accountTransferDetailRepository, BusinessEventNotifierService businessEventNotifierService, GuarantorDomainService guarantorDomainService, LoanUtilService loanUtilService, EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, CodeValueRepositoryWrapper codeValueRepository, CashierTransactionDataValidator cashierTransactionDataValidator, GLIMAccountInfoRepository glimRepository, LoanRepository loanRepository, RepaymentWithPostDatedChecksAssembler repaymentWithPostDatedChecksAssembler, PostDatedChecksRepository postDatedChecksRepository, LoanRepaymentScheduleInstallmentRepository loanRepaymentScheduleInstallmentRepository, LoanLifecycleStateMachine loanLifecycleStateMachine, LoanAccountLockService loanAccountLockService, ExternalIdFactory externalIdFactory, LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService, ErrorHandler errorHandler, LoanDownPaymentHandlerService loanDownPaymentHandlerService, LoanTransactionAssembler loanTransactionAssembler, LoanAccrualsProcessingService loanAccrualsProcessingService, LoanOfficerValidator loanOfficerValidator, LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator, LoanDisbursementService loanDisbursementService, LoanScheduleService loanScheduleService, LoanChargeValidator loanChargeValidator, LoanOfficerService loanOfficerService, ReprocessLoanTransactionsService reprocessLoanTransactionsService, LoanAccountService loanAccountService, LoanJournalEntryPoster journalEntryPoster, LoanAdjustmentService loanAdjustmentService, LoanAccountingBridgeMapper loanAccountingBridgeMapper, LoanMapper loanMapper, LoanTransactionProcessingService loanTransactionProcessingService, FineractProperties fineractProperties, JdbcTemplate jdbcTemplate ) {
        super(context, loanTransactionValidator, loanUpdateCommandFromApiJsonDeserializer, loanRepositoryWrapper, loanAccountDomainService, noteRepository, loanTransactionRepository, loanTransactionRelationRepository, loanAssembler, journalEntryWritePlatformService, calendarInstanceRepository, paymentDetailWritePlatformService, holidayRepository, configurationDomainService, workingDaysRepository, accountTransfersWritePlatformService, accountTransfersReadPlatformService, accountAssociationsReadPlatformService, loanReadPlatformService, fromApiJsonHelper, calendarRepository, loanScheduleHistoryWritePlatformService, loanApplicationValidator, accountAssociationRepository, accountTransferDetailRepository, businessEventNotifierService, guarantorDomainService, loanUtilService, entityDatatableChecksWritePlatformService, codeValueRepository, cashierTransactionDataValidator, glimRepository, loanRepository, repaymentWithPostDatedChecksAssembler, postDatedChecksRepository, loanRepaymentScheduleInstallmentRepository, loanLifecycleStateMachine, loanAccountLockService, externalIdFactory, loanAccrualTransactionBusinessEventService, errorHandler, loanDownPaymentHandlerService, loanTransactionAssembler, loanAccrualsProcessingService, loanOfficerValidator, loanDownPaymentTransactionValidator, loanDisbursementService, loanScheduleService, loanChargeValidator, loanOfficerService, reprocessLoanTransactionsService, loanAccountService, journalEntryPoster, loanAdjustmentService, loanAccountingBridgeMapper, loanMapper, loanTransactionProcessingService, fineractProperties);
        this.jdbcTemplate = jdbcTemplate;
    }


    @Override
    @Transactional
    public CommandProcessingResult forecloseLoan(Long loanId, JsonCommand command) {

        final Boolean isForcedClosure = command.booleanObjectValueOfParameterNamed("isForcedClosure");

        if (isForcedClosure != null && !(isForcedClosure instanceof Boolean)) {
            ApiParameterError error = ApiParameterError.parameterError(
                    "validation.msg.loan.isForcedClosure.invalid",
                    "The parameter isForcedClosure must be a boolean value",
                    "isForcedClosure", isForcedClosure
            );
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }

        JsonElement parsedJson = command.parsedJson();
        if (parsedJson != null && parsedJson.isJsonObject()) {
            parsedJson.getAsJsonObject().remove("isForcedClosure");
        }

        JsonCommand cleanedCommand = JsonCommand.fromExistingCommand(command, parsedJson, null);

        CommandProcessingResult result = super.forecloseLoan(loanId, cleanedCommand);

        jdbcTemplate.update(
                "UPDATE m_loan SET is_forced_closure = ? WHERE id = ?",
                Boolean.TRUE.equals(isForcedClosure), loanId
        );

        return result;
    }

}
