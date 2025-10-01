package com.crediblex.fineract.portfolio.loanaccount.service;

import static com.crediblex.fineract.portfolio.loanaccount.data.LoanAccountAdditionalProperties.LINE_OF_CREDIT_ID;

import com.crediblex.fineract.portfolio.loanaccount.data.LoanAccountAdditionalProperties;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loanaccount.exception.LineOfCreditIsNotAvailableException;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCounterpartyType;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditLoanBuyerSupplierDetail;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditLoanBuyerSupplierDetailRepository;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditApprovedBuyersRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
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
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleAssembler;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationTransitionValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanApplicationWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.GSIMReadPlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Service
@Slf4j
public class CustomLoanApplicationWritePlatformServiceJpaRepositoryImpl extends LoanApplicationWritePlatformServiceJpaRepositoryImpl {

    private final BusinessEventNotifierService businessEventNotifierService;

    private final LoanLineOfCreditParamsRepository loanLocParamsRepository;
    private final FromJsonHelper fromApiJsonHelper;
    private final LineOfCreditRepositoryWrapper lineOfCreditRepositoryWrapper;
    private final LineOfCreditApprovedBuyersRepository lineOfCreditApprovedBuyersRepository;
    private final LineOfCreditLoanBuyerSupplierDetailRepository lineOfCreditLoanBuyerSupplierDetailRepository;

    public CustomLoanApplicationWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context,
            LoanApplicationTransitionValidator loanApplicationTransitionValidator, LoanApplicationValidator loanApplicationValidator,
            LoanRepositoryWrapper loanRepositoryWrapper, NoteRepository noteRepository, LoanAssembler loanAssembler,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            CalendarRepository calendarRepository, CalendarInstanceRepository calendarInstanceRepository,
            SavingsAccountRepositoryWrapper savingsAccountRepository, AccountAssociationsRepository accountAssociationsRepository,
            BusinessEventNotifierService businessEventNotifierService, LoanScheduleAssembler loanScheduleAssembler,
            LoanUtilService loanUtilService, CalendarReadPlatformService calendarReadPlatformService,
            EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, GLIMAccountInfoRepository glimRepository,
            LoanRepository loanRepository, GSIMReadPlatformService gsimReadPlatformService,
            LoanLifecycleStateMachine defaultLoanLifecycleStateMachine, LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator, LoanScheduleService loanScheduleService,
            BusinessEventNotifierService businessEventNotifierService1, LoanLineOfCreditParamsRepository loanLocParamsRepository,
            FromJsonHelper fromApiJsonHelper, LineOfCreditRepositoryWrapper lineOfCreditRepositoryWrapper,
            LineOfCreditApprovedBuyersRepository lineOfCreditApprovedBuyersRepository,
            LineOfCreditLoanBuyerSupplierDetailRepository lineOfCreditLoanBuyerSupplierDetailRepository) {
        super(context, loanApplicationTransitionValidator, loanApplicationValidator, loanRepositoryWrapper, noteRepository, loanAssembler,
                loanRepaymentScheduleTransactionProcessorFactory, calendarRepository, calendarInstanceRepository, savingsAccountRepository,
                accountAssociationsRepository, businessEventNotifierService, loanScheduleAssembler, loanUtilService,
                calendarReadPlatformService, entityDatatableChecksWritePlatformService, glimRepository, loanRepository,
                gsimReadPlatformService, defaultLoanLifecycleStateMachine, loanAccrualsProcessingService,
                loanDownPaymentTransactionValidator, loanScheduleService);
        this.businessEventNotifierService = businessEventNotifierService1;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.loanLocParamsRepository = loanLocParamsRepository;
        this.lineOfCreditRepositoryWrapper = lineOfCreditRepositoryWrapper;
        this.lineOfCreditApprovedBuyersRepository = lineOfCreditApprovedBuyersRepository;
        this.lineOfCreditLoanBuyerSupplierDetailRepository = lineOfCreditLoanBuyerSupplierDetailRepository;
    }

    @Transactional
    @Override
    public CommandProcessingResult submitApplication(final JsonCommand command) {

        try {
            // Validations (prior assembling) - use standard validation
            this.loanApplicationValidator.validateForCreate(command);

            // Assembling loan
            final Loan loan = this.loanAssembler.assembleFrom(command);
            // Validations (further validations which requires the assembling first)
            this.loanApplicationValidator.validateForCreate(loan);
            // Need to flush to gather loan id
            this.loanRepositoryWrapper.saveAndFlush(loan);

            processLoanApplicationWithLineOfCredit(command, loan);
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

            // Building response
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(loan.getId())
                    .withEntityExternalId(loan.getExternalId()).withOfficeId(loan.getOfficeId()).withClientId(loan.getClientId())
                    .withGroupId(loan.getGroupId()).withLoanId(loan.getId()).withGlimId(loan.getGlimId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(command, throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult modifyApplication(final Long loanId, final JsonCommand command) {
        CommandProcessingResult result = super.modifyApplication(loanId, command);
        if (result.getResourceId() > 0 && result.getChanges() != null) {
            updateWithLineOfCreditParams(command, loanId, result);
        }
        return result;
    }

    private void updateWithLineOfCreditParams(JsonCommand command, Long loanId, CommandProcessingResult changes) {
        if (command.parsedJson() == null) {
            return;
        }

        // Find existing entity or create new one if it doesn't exist
        LoanLineOfCreditParams entity = loanLocParamsRepository.findByLoanId(loanId).orElse(null);

        if (entity != null) {
            final Map<String, Object> actualChanges = entity.updateFromJson(command);

            // Handle LineOfCredit relationship change if detected
            if (actualChanges.containsKey(LINE_OF_CREDIT_ID)) {
                final Long newLineOfCreditId = (Long) actualChanges.get(LINE_OF_CREDIT_ID);
                if (newLineOfCreditId != null) {
                    final LineOfCredit newLineOfCredit = lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(newLineOfCreditId);
                    entity.updateLineOfCredit(newLineOfCredit);
                } else {
                    loanLocParamsRepository.delete(entity);
                }
            }

            LineOfCredit lineOfCredit = entity.getLineOfCredit();
            final Loan loan = entity.getLoan();

            if (lineOfCredit.getProductType() == LocProductType.RECEIVABLE) {
                processCounterpartyDetails(command, loan, LineOfCreditCounterpartyType.BUYER);
            } else {
                processCounterpartyDetails(command, loan, LineOfCreditCounterpartyType.SUPPLIER);
            }

            if (!actualChanges.isEmpty()) {
                loanLocParamsRepository.saveAndFlush(entity);

                // Add changes to the command processing result
                if (changes != null) {
                    changes.getChanges().putAll(actualChanges);
                }
            }
        } else {
            // Create new entity if it doesn't exist and we have the required parameters
            if (hasAnyLocParam(command)) {
                final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);

                // Get the LineOfCredit from the command or use existing logic
                LineOfCredit lineOfCredit = getLineOfCreditFromCommand(command).orElse(null);

                if (lineOfCredit != null) {
                    entity = LoanLineOfCreditParams.fromJson(loan, lineOfCredit, command);
                    loanLocParamsRepository.saveAndFlush(entity);

                    if (lineOfCredit.getProductType() == LocProductType.RECEIVABLE) {
                        processCounterpartyDetails(command, loan, LineOfCreditCounterpartyType.BUYER);
                    } else {
                        processCounterpartyDetails(command, loan, LineOfCreditCounterpartyType.SUPPLIER);
                    }

                    // Add all new values as changes
                    if (changes != null) {
                        if (entity.getLineOfCredit() != null) {
                            changes.getChanges().put(LINE_OF_CREDIT_ID, entity.getLineOfCredit().getId());
                        }
                        if (entity.getInvoiceNo() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.INVOICE_NO, entity.getInvoiceNo());
                        }
                        if (entity.getInvoiceDate() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.INVOICE_DATE, entity.getInvoiceDate());
                        }
                        if (entity.getInvoiceDueDate() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.INVOICE_DUE_DATE, entity.getInvoiceDueDate());
                        }
                        if (entity.getInvoiceCurrency() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.INVOICE_CURRENCY, entity.getInvoiceCurrency());
                        }
                        if (entity.getInvoiceAmount() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.INVOICE_AMOUNT, entity.getInvoiceAmount());
                        }
                        // Track new LOC-related fields
                        if (entity.getDisapprovedAmount() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT, entity.getDisapprovedAmount());
                        }
                        if (entity.getApprovedReceivableAmount() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.APPROVED_RECEIVABLE_AMOUNT,
                                    entity.getApprovedReceivableAmount());
                        }
                        if (entity.getAdvancePercentage() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE, entity.getAdvancePercentage());
                        }
                        if (entity.getAmountAfterAdvance() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE, entity.getAmountAfterAdvance());
                        }
                        if (entity.getBuyerDetails() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.BUYER_DETAILS, entity.getBuyerDetails());
                        }
                        // Track additional LOC-related fields
                        if (entity.getExchangeRate() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.EXCHANGE_RATE, entity.getExchangeRate());
                        }
                        if (entity.getMarkup() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.MARKUP, entity.getMarkup());
                        }
                        if (entity.getAmountInFacilityCurrency() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY,
                                    entity.getAmountInFacilityCurrency());
                        }
                        if (entity.getApprovedPayableAmount() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.APPROVED_PAYABLE_AMOUNT,
                                    entity.getApprovedPayableAmount());
                        }
                        if (entity.getSupplierDetails() != null) {
                            changes.getChanges().put(LoanAccountAdditionalProperties.SUPPLIER_DETAILS, entity.getSupplierDetails());
                        }
                    }
                }
            }
        }
    }

    private boolean hasAnyLocParam(JsonCommand command) {
        return command.hasParameter(LINE_OF_CREDIT_ID) || command.hasParameter(LoanAccountAdditionalProperties.INVOICE_NO)
                || command.hasParameter(LoanAccountAdditionalProperties.INVOICE_DATE)
                || command.hasParameter(LoanAccountAdditionalProperties.INVOICE_DUE_DATE)
                || command.hasParameter(LoanAccountAdditionalProperties.INVOICE_CURRENCY)
                || command.hasParameter(LoanAccountAdditionalProperties.INVOICE_AMOUNT)
                || command.hasParameter(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT)
                || command.hasParameter(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE)
                || command.hasParameter(LoanAccountAdditionalProperties.BUYER_DETAILS)
                || command.hasParameter(LoanAccountAdditionalProperties.EXCHANGE_RATE)
                || command.hasParameter(LoanAccountAdditionalProperties.MARKUP)
                || command.hasParameter(LoanAccountAdditionalProperties.SUPPLIER_DETAILS);
    }

    private Optional<LineOfCredit> getLineOfCreditFromCommand(JsonCommand command) {
        if (command.hasParameter(LINE_OF_CREDIT_ID)) {
            final Long lineOfCreditId = command.longValueOfParameterNamed(LINE_OF_CREDIT_ID);
            if (lineOfCreditId != null) {
                return Optional.of(lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(lineOfCreditId));
            }
        }

        return Optional.empty();
    }

    public void processLoanApplicationWithLineOfCredit(final JsonCommand command, final Loan loan) {

        Long lineOfCreditId = fromApiJsonHelper.extractLongNamed(LINE_OF_CREDIT_ID, command.parsedJson());

        // If we have a line of credit ID, associate it with the loan
        if (lineOfCreditId != null && loan != null) {

            LineOfCredit lineOfCredit = lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(lineOfCreditId);

            if (!lineOfCredit.canDrawDown(loan.getExpectedDisbursementDate())) {
                throw new LineOfCreditIsNotAvailableException(lineOfCredit.getExternalId());
            }

            // Check if LoanLineOfCreditParams already exists for this loan
            Optional<LoanLineOfCreditParams> existingLoanLocParams = loanLocParamsRepository.findByLoanId(loan.getId());
            LoanLineOfCreditParams loanLocParams;

            if (existingLoanLocParams.isPresent()) {
                // Update existing params
                loanLocParams = existingLoanLocParams.get();
                loanLocParams.updateFromJson(command);
                loanLocParams.updateLineOfCredit(lineOfCredit);
            } else {
                // Create new params
                loanLocParams = LoanLineOfCreditParams.fromJson(loan, lineOfCredit, command);
            }

            loanLocParamsRepository.save(loanLocParams);

            // Process buyer and supplier details for line of credit applications

            if (lineOfCredit.getProductType() == LocProductType.RECEIVABLE) {

                processCounterpartyDetails(command, loan, LineOfCreditCounterpartyType.BUYER);

                BigDecimal advanceAmount = getApprovedReceivableAmount(loan, loanLocParams);

                loan.setApprovedPrincipal(advanceAmount.subtract(loan.getTotalInterest()));
                loan.setNetDisbursalAmount(advanceAmount.subtract(loan.getTotalInterest()));
                loan.getLoanRepaymentScheduleDetail().setPrincipal(advanceAmount.subtract(loan.getTotalInterest()));

                loan.updateLoanSummaryDerivedFields();
                loan.updateLoanSummaryAndStatus();

                this.loanRepositoryWrapper.saveAndFlush(loan);

                loanLocParamsRepository.saveAndFlush(loanLocParams);
            } else {

                processCounterpartyDetails(command, loan, LineOfCreditCounterpartyType.SUPPLIER);

            }

        }

    }

    /**
     * Process buyer and supplier details for line of credit applications
     */
    private void processCounterpartyDetails(final JsonCommand command, final Loan loan, LineOfCreditCounterpartyType counterpartyType) {
        // Get existing buyer/supplier details
        List<LineOfCreditLoanBuyerSupplierDetail> existingDetails = lineOfCreditLoanBuyerSupplierDetailRepository.findByLoan(loan);
        String commandValue = counterpartyType == LineOfCreditCounterpartyType.BUYER ? "buyerDetails" : "supplierDetails";

        if (command.hasParameter(commandValue)) {
            final String[] counterpartyIds = command.arrayValueOfParameterNamed(commandValue);
            if (counterpartyIds != null) {
                List<LineOfCreditLoanBuyerSupplierDetail> newDetails = new ArrayList<>();

                for (String counterpartyId : counterpartyIds) {
                    Long longCounterpartyId = null;
                    try {
                        longCounterpartyId = Long.parseLong(counterpartyId);
                    } catch (NumberFormatException e) {
                        throw new PlatformApiDataValidationException("error.msg.invalid." + counterpartyType.name().toLowerCase() + ".id",
                                counterpartyType.name().toLowerCase() + " id is not valid", counterpartyId, e);
                    }

                    final Long finalLongCounterpartyId = longCounterpartyId;

                    // Check if this counterparty detail already exists to avoid duplicates
                    boolean alreadyExists = existingDetails.stream()
                            .anyMatch(detail -> detail.getApprovedBuyers().getId().equals(finalLongCounterpartyId));

                    if (!alreadyExists) {
                        LineOfCreditLoanBuyerSupplierDetail counterpartyDetail = new LineOfCreditLoanBuyerSupplierDetail(loan,
                                counterpartyType,
                                lineOfCreditApprovedBuyersRepository.findById(longCounterpartyId)
                                        .orElseThrow(() -> new PlatformApiDataValidationException(
                                                "error.msg.invalid." + counterpartyType.name().toLowerCase() + ".id",
                                                counterpartyType.name().toLowerCase() + " with id " + finalLongCounterpartyId
                                                        + " does not exist",
                                                String.valueOf(finalLongCounterpartyId))));

                        newDetails.add(counterpartyDetail);
                    }
                }

                // Save only the new details (avoiding duplicates)
                if (!newDetails.isEmpty()) {
                    lineOfCreditLoanBuyerSupplierDetailRepository.saveAll(newDetails);
                }
            }
        }

    }

    private static BigDecimal getApprovedReceivableAmount(Loan loan, LoanLineOfCreditParams loanLocParams) {
        BigDecimal disapprovedAmount = loanLocParams.getDisapprovedAmount() != null ? loanLocParams.getDisapprovedAmount()
                : BigDecimal.ZERO;
        BigDecimal advancePercentage = loanLocParams.getAdvancePercentage() != null ? loanLocParams.getAdvancePercentage()
                : BigDecimal.ZERO;

        // Calculate the amount after advance
        BigDecimal amountAfterAdvance = loan.getPrincipal().getAmount().subtract(disapprovedAmount);
        if (amountAfterAdvance.compareTo(BigDecimal.ZERO) < 0) {
            amountAfterAdvance = BigDecimal.ZERO;
        }

        return amountAfterAdvance.multiply(advancePercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

}
