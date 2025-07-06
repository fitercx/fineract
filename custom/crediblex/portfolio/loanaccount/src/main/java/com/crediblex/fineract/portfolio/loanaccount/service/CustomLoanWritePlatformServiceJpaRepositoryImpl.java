/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.apache.fineract.portfolio.loanaccount.domain.Loan.ACTUAL_DISBURSEMENT_DATE;
import static org.apache.fineract.portfolio.loanaccount.domain.Loan.PARAM_STATUS;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanChargeWrapper;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import io.micrometer.common.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.cob.service.LoanAccountLockService;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.configuration.service.TemporaryConfigurationServiceContainer;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanDisbursalTransactionBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.teller.data.CashierTransactionDataValidator;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.domain.*;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.accountdetails.domain.AccountType;
import org.apache.fineract.portfolio.calendar.domain.*;
import org.apache.fineract.portfolio.calendar.domain.Calendar;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanaccount.guarantor.service.GuarantorDomainService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.serialization.*;
import org.apache.fineract.portfolio.loanaccount.service.*;
import org.apache.fineract.portfolio.loanaccount.service.adjustment.LoanAdjustmentService;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.exception.LinkedAccountRequiredException;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecks;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecksRepository;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.service.RepaymentWithPostDatedChecksAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Primary
@Service
public class CustomLoanWritePlatformServiceJpaRepositoryImpl extends LoanWritePlatformServiceJpaRepositoryImpl {

    private final PlatformSecurityContext context;
    private final LoanTransactionValidator loanTransactionValidator;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanAccountDomainService loanAccountDomainService;
    private final NoteRepository noteRepository;
    private final LoanTransactionRepository loanTransactionRepository;
    private final LoanAssembler loanAssembler;
    private final CalendarInstanceRepository calendarInstanceRepository;
    private final PaymentDetailWritePlatformService paymentDetailWritePlatformService;
    private final ConfigurationDomainService configurationDomainService;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private final AccountAssociationsReadPlatformService accountAssociationsReadPlatformService;
    private final CalendarRepository calendarRepository;
    private final LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService;
    private final LoanApplicationValidator loanApplicationValidator;
    private final AccountAssociationsRepository accountAssociationRepository;
    private final AccountTransferDetailRepository accountTransferDetailRepository;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final LoanUtilService loanUtilService;
    private final CashierTransactionDataValidator cashierTransactionDataValidator;
    private final RepaymentWithPostDatedChecksAssembler repaymentWithPostDatedChecksAssembler;
    private final PostDatedChecksRepository postDatedChecksRepository;
    private final LoanRepaymentScheduleInstallmentRepository loanRepaymentScheduleInstallmentRepository;
    private final LoanLifecycleStateMachine loanLifecycleStateMachine;
    private final ExternalIdFactory externalIdFactory;
    private final LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService;
    private final LoanDownPaymentHandlerService loanDownPaymentHandlerService;
    private final LoanAccrualsProcessingService loanAccrualsProcessingService;
    private final LoanDisbursementService loanDisbursementService;
    private final LoanScheduleService loanScheduleService;
    private final ReprocessLoanTransactionsService reprocessLoanTransactionsService;
    private final LoanJournalEntryPoster journalEntryPoster;
    private final LoanMapper loanMapper;
    private final FineractProperties fineractProperties;

    private final JdbcTemplate jdbcTemplate;

    public CustomLoanWritePlatformServiceJpaRepositoryImpl(final PlatformSecurityContext context,
            final LoanTransactionValidator loanTransactionValidator,
            final LoanUpdateCommandFromApiJsonDeserializer loanUpdateCommandFromApiJsonDeserializer,
            final LoanRepositoryWrapper loanRepositoryWrapper, final LoanAccountDomainService loanAccountDomainService,
            final NoteRepository noteRepository, final LoanTransactionRepository loanTransactionRepository,
            final LoanTransactionRelationRepository loanTransactionRelationRepository, final LoanAssembler loanAssembler,
            final JournalEntryWritePlatformService journalEntryWritePlatformService,
            final CalendarInstanceRepository calendarInstanceRepository,
            final PaymentDetailWritePlatformService paymentDetailWritePlatformService, final HolidayRepositoryWrapper holidayRepository,
            final ConfigurationDomainService configurationDomainService, final WorkingDaysRepositoryWrapper workingDaysRepository,
            final AccountTransfersWritePlatformService accountTransfersWritePlatformService,
            final AccountTransfersReadPlatformService accountTransfersReadPlatformService,
            final AccountAssociationsReadPlatformService accountAssociationsReadPlatformService,
            final LoanReadPlatformService loanReadPlatformService, final FromJsonHelper fromApiJsonHelper,
            final CalendarRepository calendarRepository,
            final LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService,
            final LoanApplicationValidator loanApplicationValidator, final AccountAssociationsRepository accountAssociationRepository,
            final AccountTransferDetailRepository accountTransferDetailRepository,
            final BusinessEventNotifierService businessEventNotifierService, final GuarantorDomainService guarantorDomainService,
            final LoanUtilService loanUtilService,
            final EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService,
            final CodeValueRepositoryWrapper codeValueRepository, final CashierTransactionDataValidator cashierTransactionDataValidator,
            final GLIMAccountInfoRepository glimRepository, final LoanRepository loanRepository,
            final RepaymentWithPostDatedChecksAssembler repaymentWithPostDatedChecksAssembler,
            final PostDatedChecksRepository postDatedChecksRepository,
            final LoanRepaymentScheduleInstallmentRepository loanRepaymentScheduleInstallmentRepository,
            final LoanLifecycleStateMachine loanLifecycleStateMachine, final LoanAccountLockService loanAccountLockService,
            final ExternalIdFactory externalIdFactory,
            final LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService, final ErrorHandler errorHandler,
            final LoanDownPaymentHandlerService loanDownPaymentHandlerService, final LoanTransactionAssembler loanTransactionAssembler,
            final LoanAccrualsProcessingService loanAccrualsProcessingService, final LoanOfficerValidator loanOfficerValidator,
            final LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator,
            final LoanDisbursementService loanDisbursementService, final LoanScheduleService loanScheduleService,
            final LoanChargeValidator loanChargeValidator, final LoanOfficerService loanOfficerService,
            final ReprocessLoanTransactionsService reprocessLoanTransactionsService, final LoanAccountService loanAccountService,
            final LoanJournalEntryPoster journalEntryPoster, final LoanAdjustmentService loanAdjustmentService,
            final LoanAccountingBridgeMapper loanAccountingBridgeMapper, final LoanMapper loanMapper,
            final LoanTransactionProcessingService loanTransactionProcessingService, final FineractProperties fineractProperties,JdbcTemplate jdbcTemplate) {
        super(context, loanTransactionValidator, loanUpdateCommandFromApiJsonDeserializer, loanRepositoryWrapper, loanAccountDomainService,
                noteRepository, loanTransactionRepository, loanTransactionRelationRepository, loanAssembler,
                journalEntryWritePlatformService, calendarInstanceRepository, paymentDetailWritePlatformService, holidayRepository,
                configurationDomainService, workingDaysRepository, accountTransfersWritePlatformService,
                accountTransfersReadPlatformService, accountAssociationsReadPlatformService, loanReadPlatformService, fromApiJsonHelper,
                calendarRepository, loanScheduleHistoryWritePlatformService, loanApplicationValidator, accountAssociationRepository,
                accountTransferDetailRepository, businessEventNotifierService, guarantorDomainService, loanUtilService,
                entityDatatableChecksWritePlatformService, codeValueRepository, cashierTransactionDataValidator, glimRepository,
                loanRepository, repaymentWithPostDatedChecksAssembler, postDatedChecksRepository,
                loanRepaymentScheduleInstallmentRepository, loanLifecycleStateMachine, loanAccountLockService, externalIdFactory,
                loanAccrualTransactionBusinessEventService, errorHandler, loanDownPaymentHandlerService, loanTransactionAssembler,
                loanAccrualsProcessingService, loanOfficerValidator, loanDownPaymentTransactionValidator, loanDisbursementService,
                loanScheduleService, loanChargeValidator, loanOfficerService, reprocessLoanTransactionsService, loanAccountService,
                journalEntryPoster, loanAdjustmentService, loanAccountingBridgeMapper, loanMapper, loanTransactionProcessingService,
                fineractProperties);
        this.context = context;
        this.loanTransactionValidator = loanTransactionValidator;
        this.loanRepositoryWrapper = loanRepositoryWrapper;
        this.loanAccountDomainService = loanAccountDomainService;
        this.noteRepository = noteRepository;
        this.loanTransactionRepository = loanTransactionRepository;
        this.loanAssembler = loanAssembler;
        this.calendarInstanceRepository = calendarInstanceRepository;
        this.paymentDetailWritePlatformService = paymentDetailWritePlatformService;
        this.configurationDomainService = configurationDomainService;
        this.accountTransfersWritePlatformService = accountTransfersWritePlatformService;
        this.accountAssociationsReadPlatformService = accountAssociationsReadPlatformService;
        this.calendarRepository = calendarRepository;
        this.loanScheduleHistoryWritePlatformService = loanScheduleHistoryWritePlatformService;
        this.loanApplicationValidator = loanApplicationValidator;
        this.accountAssociationRepository = accountAssociationRepository;
        this.accountTransferDetailRepository = accountTransferDetailRepository;
        this.businessEventNotifierService = businessEventNotifierService;
        this.loanUtilService = loanUtilService;
        this.cashierTransactionDataValidator = cashierTransactionDataValidator;
        this.repaymentWithPostDatedChecksAssembler = repaymentWithPostDatedChecksAssembler;
        this.postDatedChecksRepository = postDatedChecksRepository;
        this.loanRepaymentScheduleInstallmentRepository = loanRepaymentScheduleInstallmentRepository;
        this.loanLifecycleStateMachine = loanLifecycleStateMachine;
        this.externalIdFactory = externalIdFactory;
        this.loanAccrualTransactionBusinessEventService = loanAccrualTransactionBusinessEventService;
        this.loanDownPaymentHandlerService = loanDownPaymentHandlerService;
        this.loanAccrualsProcessingService = loanAccrualsProcessingService;
        this.loanDisbursementService = loanDisbursementService;
        this.loanScheduleService = loanScheduleService;
        this.reprocessLoanTransactionsService = reprocessLoanTransactionsService;
        this.journalEntryPoster = journalEntryPoster;
        this.loanMapper = loanMapper;
        this.fineractProperties = fineractProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    private void disburseLoanToSavings(final Loan loan, final JsonCommand command, final Money amount, final PaymentDetail paymentDetail) {
        final LocalDate transactionDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
        final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
        final PortfolioAccountData portfolioAccountData = this.accountAssociationsReadPlatformService
                .retriveLoanLinkedAssociation(loan.getId());
        if (portfolioAccountData == null) {
            final String errorMessage = "Disburse Loan with id:" + loan.getId() + " requires linked savings account for payment";
            throw new LinkedAccountRequiredException("loan.disburse.to.savings", errorMessage, loan.getId());
        }
        final SavingsAccount fromSavingsAccount = null;
        final boolean isExceptionForBalanceCheck = false;
        final boolean isRegularTransaction = true;

        // Calculate breakdown
        DisbursalBreakdown breakdown = calculateDisbursalBreakdown(loan);

        final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(transactionDate, breakdown.netDisbursal,
                PortfolioAccountType.LOAN, PortfolioAccountType.SAVINGS, loan.getId(), portfolioAccountData.getId(),
                "Loan Disbursement (Net of Fees and Charges)", locale, fmt, paymentDetail, LoanTransactionType.DISBURSEMENT.getValue(),
                null, null, null, AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null, txnExternalId, loan, null,
                fromSavingsAccount, isRegularTransaction, isExceptionForBalanceCheck);
        this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
    }

    private static class DisbursalBreakdown {

        BigDecimal principal;
        BigDecimal totalFees;
        BigDecimal totalTaxes;
        BigDecimal netDisbursal;

        DisbursalBreakdown(BigDecimal principal, BigDecimal totalFees, BigDecimal totalTaxes) {
            this.principal = principal;
            this.totalFees = totalFees;
            this.totalTaxes = totalTaxes;
            this.netDisbursal = principal.subtract(totalFees).subtract(totalTaxes);
        }
    }

    private DisbursalBreakdown calculateDisbursalBreakdown(Loan loan) {
        BigDecimal principal = loan.getPrincipal().getAmount();
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (LoanCharge charge : loan.getActiveCharges()) {
            if (charge.isDueAtDisbursement() && charge.isChargePending()) {
                LoanChargeWrapper chargeWrapper = new LoanChargeWrapper(charge);
                totalFees = totalFees.add(chargeWrapper.getFeePortionExcludingTax());
                totalTax = totalTax.add(chargeWrapper.getTaxesAmount());
            }
        }
        return new DisbursalBreakdown(principal, totalFees, totalTax);
    }

    @Transactional
    @Override
    public CommandProcessingResult disburseLoan(final Long loanId, final JsonCommand command, Boolean isAccountTransfer,
            Boolean isWithoutAutoPayment) {
        loanTransactionValidator.validateDisbursement(command, isAccountTransfer, loanId);

        Loan loan = loanAssembler.assembleFrom(loanId);

        if (loan.loanProduct().isDisallowExpectedDisbursements()) {
            List<LoanDisbursementDetails> filteredList = loan.getDisbursementDetails().stream()
                    .filter(disbursementDetails -> disbursementDetails.actualDisbursementDate() == null).toList();
            // Check whether a new LoanDisbursementDetails is required
            if (filteredList.isEmpty()) {
                // create artificial 'tranche/expected disbursal' as current disburse code expects it for
                // multi-disbursal products
                final LocalDate artificialExpectedDate = loan.getExpectedDisbursedOnLocalDate();
                LoanDisbursementDetails disbursementDetail = new LoanDisbursementDetails(artificialExpectedDate, null,
                        loan.getDisbursedAmount(), null, false);
                disbursementDetail.updateLoan(loan);
                loan.getAllDisbursementDetails().add(disbursementDetail);
            }
        }

        final LocalDate nextPossibleRepaymentDate = loan.getNextPossibleRepaymentDateForRescheduling();
        final LocalDate rescheduledRepaymentDate = command.localDateValueOfParameterNamed("adjustRepaymentDate");
        final LocalDate actualDisbursementDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
        if (!loan.isMultiDisburmentLoan()) {
            loan.setActualDisbursementDate(actualDisbursementDate);
        }

        // validate actual disbursement date against meeting date
        ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, null);

        businessEventNotifierService.notifyPreBusinessEvent(new LoanDisbursalBusinessEvent(loan));

        List<Long> existingTransactionIds = new ArrayList<>();
        List<Long> existingReversedTransactionIds = new ArrayList<>();

        final AppUser currentUser = getAppUserIfPresent();
        final Map<String, Object> changes = new LinkedHashMap<>();

        final PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, changes);
        if (paymentDetail != null && paymentDetail.getPaymentType() != null && paymentDetail.getPaymentType().getIsCashPayment()) {
            BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");
            this.cashierTransactionDataValidator.validateOnLoanDisbursal(currentUser, loan.getCurrencyCode(), transactionAmount);
        }
        final boolean isPaymentTypeApplicableForDisbursementCharge = configurationDomainService
                .isPaymentTypeApplicableForDisbursementCharge();

        // Recalculate first repayment date based in actual disbursement date.
        updateLoanCounters(loan, actualDisbursementDate);
        Money amountBeforeAdjust = loan.getPrincipal();
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);

        if (loan.canDisburse()) {
            // Get netDisbursalAmount from disbursal screen field.
            final BigDecimal netDisbursalAmount = command
                    .bigDecimalValueOfParameterNamed(LoanApiConstants.disbursementNetDisbursalAmountParameterName);
            if (netDisbursalAmount != null) {
                loan.setNetDisbursalAmount(netDisbursalAmount);
            }
            Money disburseAmount = loanDisbursementService.adjustDisburseAmount(loan, command, actualDisbursementDate);
            Money amountToDisburse = disburseAmount.copy();
            boolean recalculateSchedule = amountBeforeAdjust.isNotEqualTo(loan.getPrincipal());
            final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

            if (loan.isTopup() && loan.getClientId() != null) {
                final BigDecimal loanOutstanding = loanApplicationValidator.validateTopupLoan(loan, actualDisbursementDate);

                amountToDisburse = disburseAmount.minus(loanOutstanding);
                disburseLoanToLoan(loan, command, loanOutstanding);
            }

            LoanTransaction disbursementTransaction = null;
            if (isAccountTransfer) {
                disburseLoanToSavings(loan, command, amountToDisburse, paymentDetail);
                existingTransactionIds.addAll(loan.findExistingTransactionIds());
                existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
            } else {
                existingTransactionIds.addAll(loan.findExistingTransactionIds());
                existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
                disbursementTransaction = LoanTransaction.disbursement(loan, amountToDisburse, paymentDetail, actualDisbursementDate,
                        txnExternalId, loan.getTotalOverpaidAsMoney());
                disbursementTransaction.updateLoan(loan);
                loan.addLoanTransaction(disbursementTransaction);
                loanTransactionRepository.saveAndFlush(disbursementTransaction);
            }
            if (loan.getRepaymentScheduleInstallments().isEmpty()) {
                /*
                 * If no schedule, generate one (applicable to non-tranche multi-disbursal loans)
                 */
                recalculateSchedule = true;
            }

            regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO, nextPossibleRepaymentDate,
                    rescheduledRepaymentDate);
            boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
            if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
            }
            disburseLoan(command, isPaymentTypeApplicableForDisbursementCharge, paymentDetail, loan, currentUser, changes,
                    scheduleGeneratorDTO);
            loan.adjustNetDisbursalAmount(amountToDisburse.getAmount());

            loanAccrualsProcessingService.reprocessExistingAccruals(loan);

            LocalDate firstInstallmentDueDate = loan.fetchRepaymentScheduleInstallment(1).getDueDate();
            if (loan.isInterestBearingAndInterestRecalculationEnabled()
                    && (DateUtils.isBeforeBusinessDate(firstInstallmentDueDate) || loan.isDisbursementMissed())) {
                loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
            }

            if (loan.isAutoRepaymentForDownPaymentEnabled() && !isWithoutAutoPayment) {
                // updating linked savings account for auto down payment transaction for disbursement to savings account
                if (isAccountTransfer && loan.shouldCreateStandingInstructionAtDisbursement()) {
                    final PortfolioAccountData linkedSavingsAccountData = this.accountAssociationsReadPlatformService
                            .retriveLoanLinkedAssociation(loanId);
                    final SavingsAccount fromSavingsAccount = null;
                    final boolean isRegularTransaction = true;
                    final boolean isExceptionForBalanceCheck = false;

                    BigDecimal disbursedAmountPercentageForDownPayment = loan.getLoanRepaymentScheduleDetail()
                            .getDisbursedAmountPercentageForDownPayment();
                    Money downPaymentMoney = Money.of(loan.getCurrency(),
                            MathUtil.percentageOf(amountToDisburse.getAmount(), disbursedAmountPercentageForDownPayment, 19));
                    if (loan.getLoanProduct().getInstallmentAmountInMultiplesOf() != null) {
                        downPaymentMoney = Money.roundToMultiplesOf(downPaymentMoney,
                                loan.getLoanProduct().getInstallmentAmountInMultiplesOf());
                    }
                    final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(actualDisbursementDate,
                            downPaymentMoney.getAmount(), PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN,
                            linkedSavingsAccountData.getId(), loan.getId(),
                            "To loan " + loan.getAccountNumber() + " from savings " + linkedSavingsAccountData.getAccountNo()
                                    + " Standing instruction transfer ",
                            locale, fmt, null, null, LoanTransactionType.DOWN_PAYMENT.getValue(), null, null,
                            AccountTransferType.LOAN_DOWN_PAYMENT.getValue(), null, null, ExternalId.empty(), null, null,
                            fromSavingsAccount, isRegularTransaction, isExceptionForBalanceCheck);
                    this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
                } else {
                    loanDownPaymentHandlerService.handleDownPayment(scheduleGeneratorDTO, command, disbursementTransaction, loan);
                    loanAccrualsProcessingService.reprocessExistingAccruals(loan);
                    if (loan.isInterestBearingAndInterestRecalculationEnabled()) {
                        loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
                    }
                }
            }
        }
        if (!changes.isEmpty()) {
            loan.updateLoanScheduleDependentDerivedFields();
            loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

            createNote(loan, command, changes);
            // auto create standing instruction
            createStandingInstruction(loan);
        }

        final Set<LoanCharge> loanCharges = loan.getActiveCharges();
        final Map<Long, BigDecimal> disBuLoanCharges = new HashMap<>();
        for (final LoanCharge loanCharge : loanCharges) {
            if (loanCharge.isDueAtDisbursement() && loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()
                    && loanCharge.isChargePending()) {
                disBuLoanCharges.put(loanCharge.getId(), loanCharge.amountOutstanding());
            }
        }

        for (final Map.Entry<Long, BigDecimal> entrySet : disBuLoanCharges.entrySet()) {
            final PortfolioAccountData savingAccountData = this.accountAssociationsReadPlatformService.retriveLoanLinkedAssociation(loanId);
            final SavingsAccount fromSavingsAccount = null;
            final boolean isRegularTransaction = true;
            final boolean isExceptionForBalanceCheck = false;
            final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(actualDisbursementDate, entrySet.getValue(),
                    PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN, savingAccountData.getId(), loanId, "Loan Charge Payment",
                    locale, fmt, null, null, LoanTransactionType.REPAYMENT_AT_DISBURSEMENT.getValue(), entrySet.getKey(), null,
                    AccountTransferType.CHARGE_PAYMENT.getValue(), null, null, ExternalId.empty(), null, null, fromSavingsAccount,
                    isRegularTransaction, isExceptionForBalanceCheck);
            this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
        }
        updateRecurringCalendarDatesForInterestRecalculation(loan);
        loanAccrualsProcessingService.processAccrualsOnInterestRecalculation(loan, loan.isInterestBearingAndInterestRecalculationEnabled(),
                false);
        this.loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());

        // Post Dated Checks
        if (command.parameterExists("postDatedChecks")) {
            // get repayment with post dates checks to update
            Set<PostDatedChecks> postDatedChecks = this.repaymentWithPostDatedChecksAssembler.fromParsedJson(command.json(), loan);
            updatePostDatedChecks(postDatedChecks);
        }

        businessEventNotifierService.notifyPostBusinessEvent(new LoanDisbursalBusinessEvent(loan));

        Long disbursalTransactionId = null;
        ExternalId disbursalTransactionExternalId = null;

        if (!isAccountTransfer) {
            // If accounting is not periodic accrual, the last transaction might be the accrual not the disbursement
            LoanTransaction disbursalTransaction = Lists.reverse(loan.getLoanTransactions()).stream()
                    .filter(e -> LoanTransactionType.DISBURSEMENT.equals(e.getTypeOf())).findFirst().orElseThrow();
            disbursalTransactionId = disbursalTransaction.getId();
            disbursalTransactionExternalId = disbursalTransaction.getExternalId();
            businessEventNotifierService.notifyPostBusinessEvent(new LoanDisbursalTransactionBusinessEvent(disbursalTransaction));
        }

        journalEntryPoster.postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);
        loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(loan.getId()) //
                .withEntityExternalId(loan.getExternalId()) //
                .withSubEntityId(disbursalTransactionId) //
                .withSubEntityExternalId(disbursalTransactionExternalId) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .with(changes) //
                .build();
    }

    @Override
    @Transactional
    public CommandProcessingResult forecloseLoan(Long loanId, JsonCommand command) {

        final Boolean isForcedClosure = command.booleanObjectValueOfParameterNamed("isForcedClosure");
        final Boolean isRestructured = command.booleanObjectValueOfParameterNamed("isRestructured");

        if (isForcedClosure != null && !(isForcedClosure instanceof Boolean)) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.loan.isForcedClosure.invalid",
                    "The parameter isForcedClosure must be a boolean value", "isForcedClosure", isForcedClosure);
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }

        if (isRestructured != null && !(isRestructured instanceof Boolean)) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.loan.isRestructured.invalid",
                    "The parameter isRestructured must be a boolean value", "isRestructured", isRestructured);
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }

        JsonElement parsedJson = command.parsedJson();
        if (parsedJson != null && parsedJson.isJsonObject()) {
            parsedJson.getAsJsonObject().remove("isForcedClosure");
            parsedJson.getAsJsonObject().remove("isRestructured");
        }

        JsonCommand cleanedCommand = JsonCommand.fromExistingCommand(command, parsedJson, null);

        CommandProcessingResult result = super.forecloseLoan(loanId, cleanedCommand);

        if (result != null && result.getResourceId() != null && result.getResourceId() > 0L) {
            jdbcTemplate.update("UPDATE m_loan SET is_forced_closure = ?, is_restructured = ? WHERE id = ?",
                    Boolean.TRUE.equals(isForcedClosure), Boolean.TRUE.equals(isRestructured), loanId);
        }

        return result;
    }
    private AppUser getAppUserIfPresent() {
        AppUser user = null;
        if (this.context != null) {
            user = this.context.getAuthenticatedUserIfPresent();
        }
        return user;
    }

    private void updateLoanCounters(final Loan loan, final LocalDate actualDisbursementDate) {

        if (loan.isGroupLoan()) {
            final List<Loan> loansToUpdateForLoanCounter = this.loanRepositoryWrapper.getGroupLoansDisbursedAfter(actualDisbursementDate,
                    loan.getGroupId(), AccountType.GROUP);
            final Integer newLoanCounter = getNewGroupLoanCounter(loan);
            final Integer newLoanProductCounter = getNewGroupLoanProductCounter(loan);
            updateLoanCounter(loan, loansToUpdateForLoanCounter, newLoanCounter, newLoanProductCounter);
        } else {
            final List<Loan> loansToUpdateForLoanCounter = this.loanRepositoryWrapper
                    .getClientOrJLGLoansDisbursedAfter(actualDisbursementDate, loan.getClientId());
            final Integer newLoanCounter = getNewClientOrJLGLoanCounter(loan);
            final Integer newLoanProductCounter = getNewClientOrJLGLoanProductCounter(loan);
            updateLoanCounter(loan, loansToUpdateForLoanCounter, newLoanCounter, newLoanProductCounter);
        }
    }

    private Integer getNewGroupLoanCounter(final Loan loan) {

        Integer maxClientLoanCounter = this.loanRepositoryWrapper.getMaxGroupLoanCounter(loan.getGroupId(), AccountType.GROUP);
        if (maxClientLoanCounter == null) {
            maxClientLoanCounter = 1;
        } else {
            maxClientLoanCounter = maxClientLoanCounter + 1;
        }
        return maxClientLoanCounter;
    }

    private Integer getNewGroupLoanProductCounter(final Loan loan) {

        Integer maxLoanProductLoanCounter = this.loanRepositoryWrapper.getMaxGroupLoanProductCounter(loan.loanProduct().getId(),
                loan.getGroupId(), AccountType.GROUP);
        if (maxLoanProductLoanCounter == null) {
            maxLoanProductLoanCounter = 1;
        } else {
            maxLoanProductLoanCounter = maxLoanProductLoanCounter + 1;
        }
        return maxLoanProductLoanCounter;
    }

    private void updateLoanCounter(final Loan loan, final List<Loan> loansToUpdateForLoanCounter, Integer newLoanCounter,
            Integer newLoanProductCounter) {

        final boolean includeInBorrowerCycle = loan.loanProduct().isIncludeInBorrowerCycle();
        for (final Loan loanToUpdate : loansToUpdateForLoanCounter) {
            // Update client loan counter if loan product includeInBorrowerCycle
            // is true
            if (loanToUpdate.loanProduct().isIncludeInBorrowerCycle()) {
                Integer currentLoanCounter = loanToUpdate.getCurrentLoanCounter() == null ? 1 : loanToUpdate.getCurrentLoanCounter();
                if (newLoanCounter > currentLoanCounter) {
                    newLoanCounter = currentLoanCounter;
                }
                loanToUpdate.updateClientLoanCounter(++currentLoanCounter);
            }

            if (Objects.equals(loan.loanProduct().getId(), loanToUpdate.loanProduct().getId())) {
                Integer loanProductLoanCounter = loanToUpdate.getLoanProductLoanCounter();
                if (newLoanProductCounter > loanProductLoanCounter) {
                    newLoanProductCounter = loanProductLoanCounter;
                }
                loanToUpdate.updateLoanProductLoanCounter(++loanProductLoanCounter);
            }
        }

        if (includeInBorrowerCycle) {
            loan.updateClientLoanCounter(newLoanCounter);
        } else {
            loan.updateClientLoanCounter(null);
        }
        loan.updateLoanProductLoanCounter(newLoanProductCounter);
        this.loanRepositoryWrapper.save(loansToUpdateForLoanCounter);
    }

    private Integer getNewClientOrJLGLoanCounter(final Loan loan) {

        Integer maxClientLoanCounter = this.loanRepositoryWrapper.getMaxClientOrJLGLoanCounter(loan.getClientId());
        if (maxClientLoanCounter == null) {
            maxClientLoanCounter = 1;
        } else {
            maxClientLoanCounter = maxClientLoanCounter + 1;
        }
        return maxClientLoanCounter;
    }

    private Integer getNewClientOrJLGLoanProductCounter(final Loan loan) {

        Integer maxLoanProductLoanCounter = this.loanRepositoryWrapper.getMaxClientOrJLGLoanProductCounter(loan.loanProduct().getId(),
                loan.getClientId());
        if (maxLoanProductLoanCounter == null) {
            maxLoanProductLoanCounter = 1;
        } else {
            maxLoanProductLoanCounter = maxLoanProductLoanCounter + 1;
        }
        return maxLoanProductLoanCounter;
    }

    private void disburseLoanToLoan(final Loan loan, final JsonCommand command, final BigDecimal amount) {
        final LocalDate transactionDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
        final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
        final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(transactionDate, amount, PortfolioAccountType.LOAN,
                PortfolioAccountType.LOAN, loan.getId(), loan.getTopupLoanDetails().getLoanIdToClose(), "Loan Topup", locale, fmt,
                LoanTransactionType.DISBURSEMENT.getValue(), LoanTransactionType.REPAYMENT.getValue(), txnExternalId, loan, null);
        AccountTransferDetails accountTransferDetails = this.accountTransfersWritePlatformService.repayLoanWithTopup(accountTransferDTO);
        loan.getTopupLoanDetails().setAccountTransferDetails(accountTransferDetails.getId());
        loan.getTopupLoanDetails().setTopupAmount(amount);
    }

    private void regenerateScheduleOnDisbursement(final JsonCommand command, final Loan loan, final boolean recalculateSchedule,
            final ScheduleGeneratorDTO scheduleGeneratorDTO, final LocalDate nextPossibleRepaymentDate,
            final LocalDate rescheduledRepaymentDate) {
        final LocalDate actualDisbursementDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
        BigDecimal emiAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.fixedEmiAmountParameterName);

        boolean isEmiAmountChanged = false;
        LoanProduct loanProduct = loan.getLoanProduct();
        if ((loanProduct.isMultiDisburseLoan() || loanProduct.isCanDefineInstallmentAmount()) && emiAmount != null
                && emiAmount.compareTo(loan.retriveLastEmiAmount()) != 0) {
            if (loanProduct.isMultiDisburseLoan()) {
                final LocalDate dateValue = null;
                final boolean isSpecificToInstallment = false;
                final Boolean isChangeEmiIfRepaymentDateSameAsDisbursementDateEnabled = scheduleGeneratorDTO
                        .isChangeEmiIfRepaymentDateSameAsDisbursementDateEnabled();
                LocalDate effectiveDateFrom = actualDisbursementDate;
                if (!isChangeEmiIfRepaymentDateSameAsDisbursementDateEnabled && actualDisbursementDate.equals(nextPossibleRepaymentDate)) {
                    effectiveDateFrom = nextPossibleRepaymentDate.plusDays(1);
                }
                LoanTermVariations loanVariationTerms = new LoanTermVariations(LoanTermVariationType.EMI_AMOUNT.getValue(),
                        effectiveDateFrom, emiAmount, dateValue, isSpecificToInstallment, loan, LoanStatus.ACTIVE.getValue());
                loan.getLoanTermVariations().add(loanVariationTerms);
            } else {
                loan.setFixedEmiAmount(emiAmount);
            }
            isEmiAmountChanged = true;
        }
        if (rescheduledRepaymentDate != null && loanProduct.isMultiDisburseLoan()) {
            final boolean isSpecificToInstallment = false;
            LoanTermVariations loanVariationTerms = new LoanTermVariations(LoanTermVariationType.DUE_DATE.getValue(),
                    nextPossibleRepaymentDate, emiAmount, rescheduledRepaymentDate, isSpecificToInstallment, loan,
                    LoanStatus.ACTIVE.getValue());
            loan.getLoanTermVariations().add(loanVariationTerms);
        }

        if (loan.isActualDisbursedOnDateEarlierOrLaterThanExpected(actualDisbursementDate) || recalculateSchedule || isEmiAmountChanged
                || rescheduledRepaymentDate != null) {
            if (loan.isCumulativeSchedule() && loan.isInterestBearingAndInterestRecalculationEnabled()) {
                loanScheduleService.regenerateRepaymentScheduleWithInterestRecalculation(loan, scheduleGeneratorDTO);
            } else if (loan.isProgressiveSchedule() && loan.hasChargeOffTransaction() && loan.hasAccelerateChargeOffStrategy()) {
                loanScheduleService.regenerateRepaymentSchedule(loan, scheduleGeneratorDTO);
            }
        }
        loanAccrualsProcessingService.reprocessExistingAccruals(loan);

        if (loan.isInterestBearingAndInterestRecalculationEnabled()) {
            loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
        }

    }

    private void createAndSaveLoanScheduleArchive(final Loan loan, ScheduleGeneratorDTO scheduleGeneratorDTO) {
        LoanRescheduleRequest loanRescheduleRequest = null;
        LoanScheduleModel loanScheduleModel = loanMapper.regenerateScheduleModel(scheduleGeneratorDTO, loan);
        List<LoanRepaymentScheduleInstallment> installments = retrieveRepaymentScheduleFromModel(loanScheduleModel);
        this.loanScheduleHistoryWritePlatformService.createAndSaveLoanScheduleArchive(installments, loan, loanRescheduleRequest);
    }

    private List<LoanRepaymentScheduleInstallment> retrieveRepaymentScheduleFromModel(LoanScheduleModel model) {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>();
        for (final LoanScheduleModelPeriod scheduledLoanInstallment : model.getPeriods()) {
            if (scheduledLoanInstallment.isRepaymentPeriod() || scheduledLoanInstallment.isDownPaymentPeriod()) {
                final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(null,
                        scheduledLoanInstallment.periodNumber(), scheduledLoanInstallment.periodFromDate(),
                        scheduledLoanInstallment.periodDueDate(), scheduledLoanInstallment.principalDue(),
                        scheduledLoanInstallment.interestDue(), scheduledLoanInstallment.feeChargesDue(),
                        scheduledLoanInstallment.penaltyChargesDue(), scheduledLoanInstallment.isRecalculatedInterestComponent(),
                        scheduledLoanInstallment.getLoanCompoundingDetails());
                installments.add(installment);
            }
        }
        return installments;
    }

    private void disburseLoan(JsonCommand command, boolean isPaymentTypeApplicableForDisbursementCharge, PaymentDetail paymentDetail,
            Loan loan, AppUser currentUser, Map<String, Object> changes, ScheduleGeneratorDTO scheduleGeneratorDTO) {
        final PaymentDetail paymentDetail1 = isPaymentTypeApplicableForDisbursementCharge ? paymentDetail : null;
        final LocalDate actualDisbursementDate1 = command.localDateValueOfParameterNamed(ACTUAL_DISBURSEMENT_DATE);

        loan.setDisbursedBy(currentUser);
        loan.updateLoanScheduleDependentDerivedFields();

        changes.put(Loan.LOCALE, command.locale());
        changes.put(Loan.DATE_FORMAT, command.dateFormat());
        changes.put(ACTUAL_DISBURSEMENT_DATE, command.stringValueOfParameterNamed(ACTUAL_DISBURSEMENT_DATE));

        boolean disbursementMissedParam = loan.isDisbursementMissed();
        LocalDate firstInstallmentDueDate = loan.fetchRepaymentScheduleInstallment(1).getDueDate();
        if ((loan.isCumulativeSchedule() && loan.isInterestBearingAndInterestRecalculationEnabled()
                && (DateUtils.isBeforeBusinessDate(firstInstallmentDueDate) || disbursementMissedParam))) {
            loanScheduleService.regenerateRepaymentScheduleWithInterestRecalculation(loan, scheduleGeneratorDTO);
        } else {
            loanScheduleService.regenerateRepaymentSchedule(loan, scheduleGeneratorDTO);
        }

        loan.updateSummaryWithTotalFeeChargesDueAtDisbursement(loan.deriveSumTotalOfChargesDueAtDisbursement());
        loan.updateLoanRepaymentPeriodsDerivedFields(actualDisbursementDate1);
        loanTransactionValidator.validateActivityNotBeforeClientOrGroupTransferDate(loan, LoanEvent.LOAN_DISBURSED,
                actualDisbursementDate1);
        loanDisbursementService.handleDisbursementTransaction(loan, actualDisbursementDate1, paymentDetail1);
        loan.updateLoanSummaryDerivedFields();
        final Money interestApplied = Money.of(loan.getCurrency(), loan.getSummary().getTotalInterestCharged());

        /*
         * Add an interest applied transaction if the interest is accrued upfront (Up front accrual)
         */

        boolean isSingleDisbursement = loan.isMultiDisburmentLoan() && loan.getDisbursedLoanDisbursementDetails().size() == 1;
        boolean isUpfrontAccrual = loan.isUpfrontAccrualAccountingEnabledOnLoanProduct();
        boolean isCashOrDisabledAccounting = loan.isCashBasedAccountingEnabledOnLoanProduct() || loan.isAccountingDisabledOnLoanProduct();
        boolean allowCashAndNonCashAccrual = fineractProperties.getLoan().isAllowCashAndNonCashAccrual();

        boolean shouldApplyAccrualInterest = (isSingleDisbursement || !loan.isMultiDisburmentLoan())
                && (isUpfrontAccrual || (isCashOrDisabledAccounting && allowCashAndNonCashAccrual) && interestApplied.isGreaterThanZero());

        if (shouldApplyAccrualInterest) {
            ExternalId externalId = ExternalId.empty();
            if (TemporaryConfigurationServiceContainer.isExternalIdAutoGenerationEnabled()) {
                externalId = ExternalId.generate();
            }
            final LoanTransaction interestAppliedTransaction = LoanTransaction.accrueInterest(loan.getOffice(), loan, interestApplied,
                    actualDisbursementDate1, externalId);
            loan.addLoanTransaction(interestAppliedTransaction);
        }

        if (loan.getLoanProduct().isMultiDisburseLoan() || loan.isProgressiveSchedule()) {
            final List<LoanTransaction> allNonContraTransactionsPostDisbursement = loan.retrieveListOfTransactionsForReprocessing();
            if (!allNonContraTransactionsPostDisbursement.isEmpty()) {
                reprocessLoanTransactionsService.reprocessTransactions(loan);
            }
            loan.updateLoanSummaryDerivedFields();
        }

        loanLifecycleStateMachine.transition(LoanEvent.LOAN_DISBURSED, loan);
        changes.put(PARAM_STATUS, LoanEnumerations.status(loan.getLoanStatus()));
    }

    private Loan saveAndFlushLoanWithDataIntegrityViolationChecks(final Loan loan) {
        /*
         * Due to the "saveAndFlushLoanWithDataIntegrityViolationChecks" method the loan is saved and flushed in the
         * middle of the transaction. EclipseLink is in some situations are saving inconsistently the newly created
         * associations, like the newly created repayment schedule installments. The save and flush cannot be removed
         * safely till any native queries are used as part of this transaction either. See:
         * this.loanAccountDomainService.recalculateAccruals(loan);
         */
        try {
            loanRepaymentScheduleInstallmentRepository.saveAll(loan.getRepaymentScheduleInstallments());
            return this.loanRepositoryWrapper.saveAndFlush(loan);
        } catch (final JpaSystemException | DataIntegrityViolationException e) {
            final Throwable realCause = e.getCause();
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");
            if (realCause.getMessage().toLowerCase().contains("external_id_unique")) {
                baseDataValidator.reset().parameter(LoanApiConstants.externalIdParameterName).failWithCode("value.must.be.unique");
            }
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                        dataValidationErrors, e);
            }
            throw e;
        }
    }

    private void createNote(Loan loan, JsonCommand command, Map<String, Object> changes) {
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            changes.put("note", noteText);
            final Note note = Note.loanNote(loan, noteText);
            this.noteRepository.save(note);
        }
    }

    /**
     * create standing instruction for disbursed loan
     *
     * @param loan
     *            the disbursed loan
     **/
    private void createStandingInstruction(Loan loan) {

        if (loan.shouldCreateStandingInstructionAtDisbursement()) {
            AccountAssociations accountAssociations = this.accountAssociationRepository.findByLoanIdAndType(loan.getId(),
                    AccountAssociationType.LINKED_ACCOUNT_ASSOCIATION.getValue());

            if (accountAssociations != null) {

                SavingsAccount linkedSavingsAccount = accountAssociations.linkedSavingsAccount();

                // name is auto-generated
                final String name = "To loan " + loan.getAccountNumber() + " from savings " + linkedSavingsAccount.getAccountNumber();
                final Office fromOffice = loan.getOffice();
                final Client fromClient = loan.getClient();
                final Office toOffice = loan.getOffice();
                final Client toClient = loan.getClient();
                final Integer priority = StandingInstructionPriority.MEDIUM.getValue();
                final Integer transferType = AccountTransferType.LOAN_REPAYMENT.getValue();
                final Integer instructionType = StandingInstructionType.DUES.getValue();
                final Integer status = StandingInstructionStatus.ACTIVE.getValue();
                final Integer recurrenceType = AccountTransferRecurrenceType.AS_PER_DUES.getValue();
                final LocalDate validFrom = DateUtils.getBusinessLocalDate();

                AccountTransferDetails accountTransferDetails = AccountTransferDetails.savingsToLoanTransfer(fromOffice, fromClient,
                        linkedSavingsAccount, toOffice, toClient, loan, transferType);

                AccountTransferStandingInstruction accountTransferStandingInstruction = AccountTransferStandingInstruction.create(
                        accountTransferDetails, name, priority, instructionType, status, null, validFrom, null, recurrenceType, null, null,
                        null);
                accountTransferDetails.updateAccountTransferStandingInstruction(accountTransferStandingInstruction);

                this.accountTransferDetailRepository.save(accountTransferDetails);
            }
        }
    }

    private void updateRecurringCalendarDatesForInterestRecalculation(final Loan loan) {

        if (loan.isInterestBearingAndInterestRecalculationEnabled()
                && loan.loanInterestRecalculationDetails().getRestFrequencyType().isSameAsRepayment()) {
            final CalendarInstance calendarInstanceForInterestRecalculation = this.calendarInstanceRepository
                    .findByEntityIdAndEntityTypeIdAndCalendarTypeId(loan.loanInterestRecalculationDetailId(),
                            CalendarEntityType.LOAN_RECALCULATION_REST_DETAIL.getValue(), CalendarType.COLLECTION.getValue());

            Calendar calendarForInterestRecalculation = calendarInstanceForInterestRecalculation.getCalendar();
            calendarForInterestRecalculation.updateStartAndEndDate(loan.getDisbursementDate(), loan.getMaturityDate());
            this.calendarRepository.save(calendarForInterestRecalculation);
        }

    }

    private void updatePostDatedChecks(Set<PostDatedChecks> postDatedChecks) {
        this.postDatedChecksRepository.saveAll(postDatedChecks);
    }

}
