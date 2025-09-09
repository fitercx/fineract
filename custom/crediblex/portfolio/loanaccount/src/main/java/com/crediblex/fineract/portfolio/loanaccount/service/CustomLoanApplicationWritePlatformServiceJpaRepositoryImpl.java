package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loc.data.ProductType;
import com.crediblex.fineract.portfolio.loc.service.LocLoanApplicationValidator;
import com.crediblex.fineract.portfolio.loc.service.LocLoanApplicationWritePlatformServiceJpaRepositoryImpl;
import com.google.gson.JsonObject;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.dataqueries.data.EntityTables;
import org.apache.fineract.infrastructure.dataqueries.data.StatusEnum;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanCreatedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.domain.AccountAssociationsRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarRepository;
import org.apache.fineract.portfolio.calendar.service.CalendarReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleAssembler;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationTransitionValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.*;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.GSIMReadPlatformService;
import org.eclipse.persistence.exceptions.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Primary
@Service
@Slf4j
public class CustomLoanApplicationWritePlatformServiceJpaRepositoryImpl extends LoanApplicationWritePlatformServiceJpaRepositoryImpl {
    private final BusinessEventNotifierService businessEventNotifierService;
    @Autowired
    private LocLoanApplicationWritePlatformServiceJpaRepositoryImpl locLoanApplicationService;
    @Autowired
    private LocLoanApplicationValidator locLoanApplicationValidator;
    @Autowired
    private CustomLoanWritePlatformServiceJpaRepositoryImpl customLoanService;


    public CustomLoanApplicationWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context, LoanApplicationTransitionValidator loanApplicationTransitionValidator, LoanApplicationValidator loanApplicationValidator, LoanRepositoryWrapper loanRepositoryWrapper, NoteRepository noteRepository, LoanAssembler loanAssembler, LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory, CalendarRepository calendarRepository, CalendarInstanceRepository calendarInstanceRepository, SavingsAccountRepositoryWrapper savingsAccountRepository, AccountAssociationsRepository accountAssociationsRepository, BusinessEventNotifierService businessEventNotifierService, LoanScheduleAssembler loanScheduleAssembler, LoanUtilService loanUtilService, CalendarReadPlatformService calendarReadPlatformService, EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, GLIMAccountInfoRepository glimRepository, LoanRepository loanRepository, GSIMReadPlatformService gsimReadPlatformService, LoanLifecycleStateMachine defaultLoanLifecycleStateMachine, LoanAccrualsProcessingService loanAccrualsProcessingService, LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator, LoanScheduleService loanScheduleService, BusinessEventNotifierService businessEventNotifierService1) {
        super(context, loanApplicationTransitionValidator, loanApplicationValidator, loanRepositoryWrapper, noteRepository, loanAssembler, loanRepaymentScheduleTransactionProcessorFactory, calendarRepository, calendarInstanceRepository, savingsAccountRepository, accountAssociationsRepository, businessEventNotifierService, loanScheduleAssembler, loanUtilService, calendarReadPlatformService, entityDatatableChecksWritePlatformService, glimRepository, loanRepository, gsimReadPlatformService, defaultLoanLifecycleStateMachine, loanAccrualsProcessingService, loanDownPaymentTransactionValidator, loanScheduleService);
        this.businessEventNotifierService = businessEventNotifierService1;
    }

    @Transactional
    @Override
    public CommandProcessingResult submitApplication(final JsonCommand command) {

        try {
            // Line of Credit validation (before standard validation to catch LOC-specific errors first)
            locLoanApplicationValidator.validateLineOfCredit(command.parsedJson());

            // Validations (prior assembling) - use standard validation
            this.loanApplicationValidator.validateForCreate(command);

            // Assembling loan
            final Loan loan = this.loanAssembler.assembleFrom(command);
            // Validations (further validations which requires the assembling first)
            this.loanApplicationValidator.validateForCreate(loan);
            // Need to flush to gather loan id
            this.loanRepositoryWrapper.saveAndFlush(loan);
            // Account number regeneration (need loan id...)
            this.loanAssembler.accountNumberGeneration(command, loan);
            // Save interest recalculation calendar
            if (loan.getLoanProduct().isInterestRecalculationEnabled()) {
                createAndPersistCalendarInstanceForInterestRecalculation(loan);
            }
            // Save note
            final String submittedOnNote = command.stringValueOfParameterNamed("submittedOnNote");
            createNote(submittedOnNote, loan);
            // Save calendar instance
            createCalendar(command, loan);
            // Save linked account information
            final Long savingsAccountId = command.longValueOfParameterNamed("linkAccountId");
            createSavingsAccountAssociation(savingsAccountId, loan);
            // Save related datatable entries
            if (command.parameterExists(LoanApiConstants.datatables)) {
                this.entityDatatableChecksWritePlatformService.saveDatatables(StatusEnum.CREATE.getValue(), EntityTables.LOAN.getName(),
                        loan.getId(), loan.productId(), command.arrayOfParameterNamed(LoanApiConstants.datatables));
            }

            loanRepositoryWrapper.flush();
            // Check mandatory datatable entries were created
            this.entityDatatableChecksWritePlatformService.runTheCheckForProduct(loan.getId(), EntityTables.LOAN.getName(),
                    StatusEnum.CREATE.getValue(), EntityTables.LOAN.getForeignKeyColumnNameOnDatatable(), loan.productId());
            // Trigger business event
            businessEventNotifierService.notifyPostBusinessEvent(new LoanCreatedBusinessEvent(loan));

            // Process with line of credit
            if (loan.getId() != null) {
                boolean success = locLoanApplicationService.processLoanApplicationWithLineOfCredit(command, loan.getId());
                if (!success) {
                    log.error("Failed to associate loan {} with line of credit", loan.getId());
                }
            }
            // Building response
            return new CommandProcessingResultBuilder()
                    .withCommandId(command.commandId())
                    .withEntityId(loan.getId())
                    .withEntityExternalId(loan.getExternalId())
                    .withOfficeId(loan.getOfficeId())
                    .withClientId(loan.getClientId())
                    .withGroupId(loan.getGroupId())
                    .withLoanId(loan.getId()).withGlimId(loan.getGlimId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(command, throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult approveApplication(final Long loanId, final JsonCommand command) {
        try {
            // Check if this is a RECEIVABLE LOC loan and adjust the approved amount
            String productType = customLoanService.getLocProductType(loanId);
            if (ProductType.RECEIVABLE.name().equals(productType)) {
                // Get the original approved amount from the command
                BigDecimal originalApprovedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.approvedLoanAmountParameterName);

                if (originalApprovedAmount != null) {
                    // Calculate the discounted amount (this will be the new approved amount)
                    BigDecimal discountedAmount = customLoanService.calculateDiscountedAmount(loanId, originalApprovedAmount);

                    // Create a modified JSON object with the discounted amount
                    JsonObject modifiedJson = command.parsedJson().getAsJsonObject().deepCopy();
                    modifiedJson.addProperty(LoanApiConstants.approvedLoanAmountParameterName, discountedAmount);

                    // Create a new command with the modified JSON
                    JsonCommand modifiedCommand = JsonCommand.fromExistingCommand(command, modifiedJson);

                    log.info("Adjusted approved amount for RECEIVABLE LOC loan {}: original={}, discounted={}",
                            loanId, originalApprovedAmount, discountedAmount);

                    // Call the parent implementation with the modified command
                    return super.approveApplication(loanId, modifiedCommand);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to adjust approved amount for RECEIVABLE LOC loan {}: {}. Proceeding with standard approval.",
                    loanId, e.getMessage());
            // Don't fail the approval if LOC adjustment fails, proceed with standard approval
        }

        // For non-RECEIVABLE loans or if adjustment fails, proceed with standard approval
        return super.approveApplication(loanId, command);
    }
}