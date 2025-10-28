package com.crediblex.fineract.accounting.journalentry;

import com.crediblex.fineract.accounting.journalentry.data.CustomChargePaymentDTO;
import com.crediblex.fineract.accounting.journalentry.journalentry.CustomLoanDTO;
import com.crediblex.fineract.portfolio.loanaccount.data.CustomAccountingBridgeDataDTO;
import com.crediblex.fineract.portfolio.loanaccount.data.CustomLoanChargePaidByDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.data.ChargePaymentDTO;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeDataDTO;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeLoanTransactionDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;

public class CustomAccountingProcessorHelper extends AccountingProcessorHelper {

    public CustomAccountingProcessorHelper(JournalEntryRepository glJournalEntryRepository,
            ProductToGLAccountMappingRepository accountMappingRepository,
            FinancialActivityAccountRepositoryWrapper financialActivityAccountRepository, GLClosureRepository closureRepository,
            GLAccountRepository glAccountRepository, OfficeRepository officeRepository,
            AccountTransfersReadPlatformService accountTransfersReadPlatformService, ChargeRepositoryWrapper chargeRepositoryWrapper,
            BusinessEventNotifierService businessEventNotifierService) {
        super(glJournalEntryRepository, accountMappingRepository, financialActivityAccountRepository, closureRepository,
                glAccountRepository, officeRepository, accountTransfersReadPlatformService, chargeRepositoryWrapper,
                businessEventNotifierService);
    }

    @Override
    public LoanDTO populateLoanDtoFromDTO(final AccountingBridgeDataDTO accountingBridgeData) {
        final Long loanId = accountingBridgeData.getLoanId();
        final Long loanProductId = accountingBridgeData.getLoanProductId();
        final Long officeId = accountingBridgeData.getOfficeId();
        final String currencyCode = accountingBridgeData.getCurrencyCode();
        final List<LoanTransactionDTO> newLoanTransactions = new ArrayList<>();
        boolean isAccountTransfer = accountingBridgeData.isAccountTransfer();
        boolean isLoanMarkedAsChargeOff = accountingBridgeData.isChargeOff();
        boolean isLoanMarkedAsFraud = accountingBridgeData.isFraud();
        final Long chargeOffReasonCodeValue = accountingBridgeData.getChargeOffReasonCodeValue();
        final boolean cashBasedAccountingEnabled = accountingBridgeData.isCashBasedAccountingEnabled();
        final boolean upfrontAccrualBasedAccountingEnabled = accountingBridgeData.isUpfrontAccrualBasedAccountingEnabled();
        final boolean periodicAccrualBasedAccountingEnabled = accountingBridgeData.isPeriodicAccrualBasedAccountingEnabled();

        final List<AccountingBridgeLoanTransactionDTO> loanTransactionDTOs = accountingBridgeData.getNewLoanTransactions();

        for (final AccountingBridgeLoanTransactionDTO loanTxnDto : loanTransactionDTOs) {
            final Long transactionOfficeId = loanTxnDto.getOfficeId();
            final String transactionId = loanTxnDto.getId().toString();
            final LocalDate transactionDate = loanTxnDto.getDate();
            final LoanTransactionEnumData transactionType = loanTxnDto.getType();
            final BigDecimal amount = loanTxnDto.getAmount();
            final BigDecimal principal = loanTxnDto.getPrincipalPortion();
            final BigDecimal interest = loanTxnDto.getInterestPortion();
            final BigDecimal fees = loanTxnDto.getFeeChargesPortion();
            final BigDecimal taxes = loanTxnDto.getTaxChargesPortion();
            final BigDecimal penalties = loanTxnDto.getPenaltyChargesPortion();
            final BigDecimal overPayments = loanTxnDto.getOverPaymentPortion();
            final boolean reversed = loanTxnDto.isReversed();
            final Long paymentTypeId = loanTxnDto.getPaymentTypeId();
            final String chargeRefundChargeType = loanTxnDto.getChargeRefundChargeType();
            final LoanChargeData loanChargeData = loanTxnDto.getLoanChargeData();

            final List<ChargePaymentDTO> feePaymentDetails = new ArrayList<>();
            final List<ChargePaymentDTO> penaltyPaymentDetails = new ArrayList<>();
            final List<ChargePaymentDTO> taxPaymentDetails = new ArrayList<>();
            // extract charge payment details (if exists)
            if (loanTxnDto.getLoanChargesPaid() != null) {
                final List<LoanChargePaidByDTO> loanChargesPaidData = loanTxnDto.getLoanChargesPaid();
                for (final LoanChargePaidByDTO loanChargePaid : loanChargesPaidData) {
                    final Long chargeId = loanChargePaid.getChargeId();
                    final Long loanChargeId = loanChargePaid.getLoanChargeId();
                    final boolean isPenalty = loanChargePaid.getIsPenalty();
                    final BigDecimal chargeAmountPaid = loanChargePaid.getAmount();
                    final CustomChargePaymentDTO chargePaymentDTO = new CustomChargePaymentDTO(chargeId, chargeAmountPaid, loanChargeId);
                    if (loanChargePaid instanceof CustomLoanChargePaidByDTO loanChargePaidByDTO) {
                        chargePaymentDTO.setTaxGroupId(loanChargePaidByDTO.getTaxGroupId());
                        chargePaymentDTO.setIncomeGLAccountId(loanChargePaidByDTO.getIncomeGLAccountId());
                        chargePaymentDTO.setTaxGLAccountId(loanChargePaidByDTO.getTaxGLAccountId());
                        chargePaymentDTO.setTaxGroupName(loanChargePaidByDTO.getTaxGroupName());
                        chargePaymentDTO.setTaxAmount(loanChargePaidByDTO.getTaxAmount());
                        chargePaymentDTO.setDebitGLAccountId(loanChargePaidByDTO.getDebitGLAccountId());
                        chargePaymentDTO.setCreditGLAccountId(loanChargePaidByDTO.getCreditGLAccountId());
                        chargePaymentDTO.setApplicableToFactoRateFeeTaxes(loanChargePaidByDTO.isApplicableToFactoRateFeeTaxes());
                        chargePaymentDTO.setApplicableToSpecifiedDueDateTaxes(loanChargePaidByDTO.isApplicableToSpecifiedDueDateTaxes());
                    }
                    if (isPenalty) {
                        penaltyPaymentDetails.add(chargePaymentDTO);
                    } else if (chargePaymentDTO.isApplicableToFactoRateFeeTaxes()
                            || chargePaymentDTO.isApplicableToSpecifiedDueDateTaxes()) {
                        taxPaymentDetails.add(chargePaymentDTO);
                    } else {
                        feePaymentDetails.add(chargePaymentDTO);
                    }
                }
            }

            boolean localIsAccountTransfer = isAccountTransfer;
            if (!localIsAccountTransfer) {
                localIsAccountTransfer = this.accountTransfersReadPlatformService.isAccountTransfer(Long.parseLong(transactionId),
                        PortfolioAccountType.LOAN);
            }

            BigDecimal principalPaid = loanTxnDto.getPrincipalPaid();
            BigDecimal feePaid = loanTxnDto.getFeePaid();
            BigDecimal penaltyPaid = loanTxnDto.getPenaltyPaid();

            final LoanTransactionDTO transaction = new LoanTransactionDTO(transactionOfficeId, paymentTypeId, transactionId,
                    transactionDate, transactionType, amount, principal, interest, fees, taxes, penalties, overPayments, reversed,
                    penaltyPaymentDetails, feePaymentDetails, taxPaymentDetails, localIsAccountTransfer, chargeRefundChargeType,
                    loanChargeData, principalPaid, feePaid, penaltyPaid);

            transaction.setLoanToLoanTransfer(loanTxnDto.isLoanToLoanTransfer());
            newLoanTransactions.add(transaction);
        }

        return new CustomLoanDTO(loanId, loanProductId, officeId, currencyCode, cashBasedAccountingEnabled,
                upfrontAccrualBasedAccountingEnabled, periodicAccrualBasedAccountingEnabled, newLoanTransactions, isLoanMarkedAsChargeOff,
                isLoanMarkedAsFraud, chargeOffReasonCodeValue,
                (accountingBridgeData instanceof CustomAccountingBridgeDataDTO)
                        ? ((CustomAccountingBridgeDataDTO) accountingBridgeData).getNetDisbursalAmount()
                        : null);
    }

    public void createCreditJournalEntryForLoanCharges(final Office office, final String currencyCode, final Long loanId,
            final String transactionId, final LocalDate transactionDate, final BigDecimal totalAmount,
            final List<ChargePaymentDTO> chargePaymentDTOs) {
        createJournalEntriesForTaxPaymentInternal(office, currencyCode, loanId, transactionId, transactionDate, totalAmount,
                chargePaymentDTOs, true);
    }

    private void createJournalEntriesForTaxPaymentInternal(final Office office, final String currencyCode, final Long loanId,
            final String transactionId, final LocalDate transactionDate, final BigDecimal totalAmount,
            final List<ChargePaymentDTO> chargePaymentDTOs, final boolean isCredit) {
        final Map<GLAccount, BigDecimal> creditDetailsMap = new LinkedHashMap<>();
        for (final ChargePaymentDTO chargePaymentDTOSuper : chargePaymentDTOs) {

            CustomChargePaymentDTO chargePaymentDTO = (CustomChargePaymentDTO) chargePaymentDTOSuper;
            BigDecimal amount = chargePaymentDTO.getTaxAmount() != null ? chargePaymentDTO.getTaxAmount() : BigDecimal.ZERO;

            final GLAccount creditAccount = glAccountRepository.findById(chargePaymentDTO.getIncomeGLAccountId())
                    .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.glaccount.not.found",
                            "GL Account not found for ID: " + chargePaymentDTO.getIncomeGLAccountId()));

            creditDetailsMap.merge(creditAccount, amount, BigDecimal::add);

        }

        BigDecimal totalCreditedAmount = BigDecimal.ZERO;

        for (Map.Entry<GLAccount, BigDecimal> entry : creditDetailsMap.entrySet()) {
            GLAccount account = entry.getKey();
            BigDecimal amount = entry.getValue();
            totalCreditedAmount = totalCreditedAmount.add(amount);

            if (isCredit) {
                this.createCreditJournalEntryForLoan(office, currencyCode, account, loanId, transactionId, transactionDate, amount);
            } else {
                createDebitJournalEntryForLoan(office, currencyCode, account, loanId, transactionId, transactionDate, amount);
            }
        }

        if (totalAmount.compareTo(totalCreditedAmount) != 0) {
            throw new PlatformDataIntegrityException(
                    "Meltdown in advanced accounting...sum of all charges is not equal to the fee charge for a transaction",
                    "Meltdown in advanced accounting...sum of all charges is not equal to the fee charge for a transaction",
                    totalCreditedAmount, totalAmount);
        }

    }

    public void createJournalEntriesForInstallmentChargeTaxes(final Office office, final String currencyCode, final Long loanId,
            final String transactionId, final LocalDate transactionDate, final BigDecimal totalAmount,
            final List<ChargePaymentDTO> chargePaymentDTOs) {
        final Map<GLAccount, BigDecimal> creditDetailsMap = new LinkedHashMap<>();
        for (final ChargePaymentDTO chargePaymentDTOSuper : chargePaymentDTOs) {
            final CustomChargePaymentDTO chargePaymentDTO = (CustomChargePaymentDTO) chargePaymentDTOSuper;
            final BigDecimal amount = chargePaymentDTO.getTaxAmount() != null ? chargePaymentDTO.getTaxAmount() : BigDecimal.ZERO;
            final GLAccount creditAccount = glAccountRepository.findById(chargePaymentDTO.getCreditGLAccountId())
                    .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.gl.account.not.found",
                            "GL Account not found for ID: " + chargePaymentDTO.getCreditGLAccountId()));
            creditDetailsMap.merge(creditAccount, amount, BigDecimal::add);
        }
        BigDecimal totalCreditedAmount = BigDecimal.ZERO;
        for (Map.Entry<GLAccount, BigDecimal> entry : creditDetailsMap.entrySet()) {
            GLAccount account = entry.getKey();
            BigDecimal amount = entry.getValue();
            totalCreditedAmount = totalCreditedAmount.add(amount);
            this.createCreditJournalEntryForLoan(office, currencyCode, account, loanId, transactionId, transactionDate, amount);
        }
        if (totalAmount.compareTo(totalCreditedAmount) != 0) {
            throw new PlatformDataIntegrityException(
                    "Meltdown in advanced accounting...sum of all tax charges is not equal to the tax charge for a transaction",
                    "Meltdown in advanced accounting...sum of all tax charges is not equal to the tax charge for a transaction",
                    totalCreditedAmount, totalAmount);
        }

    }
}
