package com.crediblex.fineract.portfolio.account.service;

import com.crediblex.fineract.portfolio.account.data.CustomAccountTransferDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.AccountTransfersDataValidator;
import org.apache.fineract.portfolio.account.domain.AccountTransferAssembler;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferType;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformServiceImpl;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.savings.SavingsTransactionBooleanValues;
import org.apache.fineract.portfolio.savings.domain.GSIMRepositoy;
import org.apache.fineract.portfolio.savings.domain.GroupSavingsIndividualMonitoring;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Service
public class CustomAccountTransfersWritePlatformServiceImpl extends AccountTransfersWritePlatformServiceImpl {

    public CustomAccountTransfersWritePlatformServiceImpl(AccountTransfersDataValidator accountTransfersDataValidator,
            AccountTransferAssembler accountTransferAssembler, AccountTransferRepository accountTransferRepository,
            SavingsAccountAssembler savingsAccountAssembler, SavingsAccountDomainService savingsAccountDomainService,
            LoanAssembler loanAccountAssembler, LoanAccountDomainService loanAccountDomainService,
            SavingsAccountWritePlatformService savingsAccountWritePlatformService,
            AccountTransferDetailRepository accountTransferDetailRepository, LoanReadPlatformService loanReadPlatformService,
            GSIMRepositoy gsimRepository, ConfigurationDomainService configurationDomainService, ExternalIdFactory externalIdFactory,
            FineractProperties fineractProperties) {
        super(accountTransfersDataValidator, accountTransferAssembler, accountTransferRepository, savingsAccountAssembler,
                savingsAccountDomainService, loanAccountAssembler, loanAccountDomainService, savingsAccountWritePlatformService,
                accountTransferDetailRepository, loanReadPlatformService, gsimRepository, configurationDomainService, externalIdFactory,
                fineractProperties);
    }

    @Override
    @Transactional
    public Long transferFunds(final AccountTransferDTO accountTransferDTO) {
        Long transferTransactionId = null;
        final boolean isAccountTransfer = true;
        final boolean isRegularTransaction = accountTransferDTO.isRegularTransaction();
        final boolean backdatedTxnsAllowedTill = false;
        AccountTransferDetails accountTransferDetails = accountTransferDTO.getAccountTransferDetails();
        if (isSavingsToLoanAccountTransfer(accountTransferDTO.getFromAccountType(), accountTransferDTO.getToAccountType())) {
            //
            SavingsAccount fromSavingsAccount = null;
            Loan toLoanAccount = null;
            if (accountTransferDetails == null) {
                if (accountTransferDTO.getFromSavingsAccount() == null) {
                    fromSavingsAccount = this.savingsAccountAssembler.assembleFrom(accountTransferDTO.getFromAccountId(),
                            backdatedTxnsAllowedTill);
                } else {
                    fromSavingsAccount = accountTransferDTO.getFromSavingsAccount();
                    this.savingsAccountAssembler.setHelpers(fromSavingsAccount);
                }
                if (accountTransferDTO.getLoan() == null) {
                    toLoanAccount = this.loanAccountAssembler.assembleFrom(accountTransferDTO.getToAccountId());
                } else {
                    toLoanAccount = accountTransferDTO.getLoan();
                    this.loanAccountAssembler.setHelpers(toLoanAccount);
                }

            } else {
                fromSavingsAccount = accountTransferDetails.fromSavingsAccount();
                this.savingsAccountAssembler.setHelpers(fromSavingsAccount);
                toLoanAccount = accountTransferDetails.toLoanAccount();
                this.loanAccountAssembler.setHelpers(toLoanAccount);
            }

            final SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(isAccountTransfer,
                    isRegularTransaction, fromSavingsAccount.isWithdrawalFeeApplicableForTransfer(),
                    AccountTransferType.fromInt(accountTransferDTO.getTransferType()).isInterestTransfer(),
                    accountTransferDTO.isExceptionForBalanceCheck());

            final SavingsAccountTransaction withdrawal = this.savingsAccountDomainService.handleWithdrawal(fromSavingsAccount,
                    accountTransferDTO.getFmt(), accountTransferDTO.getTransactionDate(), accountTransferDTO.getTransactionAmount(),
                    accountTransferDTO.getPaymentDetail(), transactionBooleanValues, backdatedTxnsAllowedTill);

            LoanTransaction loanTransaction;

            ExternalId txnExternalId = accountTransferDTO.getTxnExternalId();
            // Safety net (it might need to generate new one)
            ExternalId externalId = externalIdFactory.create(txnExternalId.getValue());

            if (AccountTransferType.fromInt(accountTransferDTO.getTransferType()).isChargePayment()) {
                loanTransaction = this.loanAccountDomainService.makeChargePayment(toLoanAccount, accountTransferDTO.getChargeId(),
                        accountTransferDTO.getTransactionDate(), accountTransferDTO.getTransactionAmount(),
                        accountTransferDTO.getPaymentDetail(), null, externalId, accountTransferDTO.getToTransferType(),
                        accountTransferDTO.getLoanInstallmentNumber());

            } else if (AccountTransferType.fromInt(accountTransferDTO.getTransferType()).isLoanDownPayment()) {
                final boolean isRecoveryRepayment = false;
                final Boolean isHolidayValidationDone = false;
                final HolidayDetailDTO holidayDetailDto = null;
                final String chargeRefundChargeType = null;
                loanTransaction = this.loanAccountDomainService.makeRepayment(LoanTransactionType.DOWN_PAYMENT, toLoanAccount,
                        accountTransferDTO.getTransactionDate(), accountTransferDTO.getTransactionAmount(),
                        accountTransferDTO.getPaymentDetail(), null, externalId, isRecoveryRepayment, chargeRefundChargeType,
                        isAccountTransfer, holidayDetailDto, isHolidayValidationDone);
                toLoanAccount = loanTransaction.getLoan();
            } else {
                final boolean isRecoveryRepayment = false;
                final Boolean isHolidayValidationDone = false;
                final HolidayDetailDTO holidayDetailDto = null;
                final String chargeRefundChargeType = null;
                loanTransaction = this.loanAccountDomainService.makeRepayment(LoanTransactionType.REPAYMENT, toLoanAccount,
                        accountTransferDTO.getTransactionDate(), accountTransferDTO.getTransactionAmount(),
                        accountTransferDTO.getPaymentDetail(), null, externalId, isRecoveryRepayment, chargeRefundChargeType,
                        isAccountTransfer, holidayDetailDto, isHolidayValidationDone);
                toLoanAccount = loanTransaction.getLoan();
            }

            accountTransferDetails = this.accountTransferAssembler.assembleSavingsToLoanTransfer(accountTransferDTO, fromSavingsAccount,
                    toLoanAccount, withdrawal, loanTransaction);
            this.accountTransferDetailRepository.saveAndFlush(accountTransferDetails);
            transferTransactionId = accountTransferDetails.getId();
        } else if (isSavingsToSavingsAccountTransfer(accountTransferDTO.getFromAccountType(), accountTransferDTO.getToAccountType())) {

            SavingsAccount fromSavingsAccount;
            SavingsAccount toSavingsAccount;
            if (accountTransferDetails == null) {
                if (accountTransferDTO.getFromSavingsAccount() == null) {
                    fromSavingsAccount = this.savingsAccountAssembler.assembleFrom(accountTransferDTO.getFromAccountId(),
                            backdatedTxnsAllowedTill);
                } else {
                    fromSavingsAccount = accountTransferDTO.getFromSavingsAccount();
                    this.savingsAccountAssembler.setHelpers(fromSavingsAccount);
                }
                if (accountTransferDTO.getToSavingsAccount() == null) {
                    toSavingsAccount = this.savingsAccountAssembler.assembleFrom(accountTransferDTO.getToAccountId(), false);
                } else {
                    toSavingsAccount = accountTransferDTO.getToSavingsAccount();
                    this.savingsAccountAssembler.setHelpers(toSavingsAccount);
                }
            } else {
                fromSavingsAccount = accountTransferDetails.fromSavingsAccount();
                this.savingsAccountAssembler.setHelpers(fromSavingsAccount);
                toSavingsAccount = accountTransferDetails.toSavingsAccount();
                this.savingsAccountAssembler.setHelpers(toSavingsAccount);
            }

            final SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(isAccountTransfer,
                    isRegularTransaction, fromSavingsAccount.isWithdrawalFeeApplicableForTransfer(),
                    AccountTransferType.fromInt(accountTransferDTO.getTransferType()).isInterestTransfer(),
                    accountTransferDTO.isExceptionForBalanceCheck());

            LocalDate transactionDate = accountTransferDTO.getTransactionDate();
            if (configurationDomainService.isSavingsInterestPostingAtCurrentPeriodEnd()
                    && configurationDomainService.isNextDayFixedDepositInterestTransferEnabledForPeriodEnd()
                    && AccountTransferType.fromInt(accountTransferDTO.getTransferType()).isInterestTransfer()) {
                transactionDate = transactionDate.plusDays(1);
            }

            final SavingsAccountTransaction withdrawal = this.savingsAccountDomainService.handleWithdrawal(fromSavingsAccount,
                    accountTransferDTO.getFmt(), transactionDate, accountTransferDTO.getTransactionAmount(),
                    accountTransferDTO.getPaymentDetail(), transactionBooleanValues, backdatedTxnsAllowedTill);

            final SavingsAccountTransaction deposit = this.savingsAccountDomainService.handleDeposit(toSavingsAccount,
                    accountTransferDTO.getFmt(), transactionDate, accountTransferDTO.getTransactionAmount(),
                    accountTransferDTO.getPaymentDetail(), isAccountTransfer, isRegularTransaction, backdatedTxnsAllowedTill);

            accountTransferDetails = this.accountTransferAssembler.assembleSavingsToSavingsTransfer(accountTransferDTO, fromSavingsAccount,
                    toSavingsAccount, withdrawal, deposit);
            this.accountTransferDetailRepository.saveAndFlush(accountTransferDetails);
            transferTransactionId = accountTransferDetails.getId();

        } else if (isLoanToSavingsAccountTransfer(accountTransferDTO.getFromAccountType(), accountTransferDTO.getToAccountType())) {

            Loan fromLoanAccount = null;
            SavingsAccount toSavingsAccount = null;
            if (accountTransferDetails == null) {
                if (accountTransferDTO.getLoan() == null) {
                    fromLoanAccount = this.loanAccountAssembler.assembleFrom(accountTransferDTO.getFromAccountId());
                } else {
                    fromLoanAccount = accountTransferDTO.getLoan();
                    this.loanAccountAssembler.setHelpers(fromLoanAccount);
                }
                toSavingsAccount = this.savingsAccountAssembler.assembleFrom(accountTransferDTO.getToAccountId(), backdatedTxnsAllowedTill);
            } else {
                fromLoanAccount = accountTransferDetails.fromLoanAccount();
                this.loanAccountAssembler.setHelpers(fromLoanAccount);
                toSavingsAccount = accountTransferDetails.toSavingsAccount();
                this.savingsAccountAssembler.setHelpers(toSavingsAccount);
            }
            LoanTransaction loanTransaction = null;

            ExternalId txnExternalId = accountTransferDTO.getTxnExternalId();
            // Safety net (it might need to generate new one)
            ExternalId externalId = externalIdFactory.create(txnExternalId.getValue());

            if (LoanTransactionType.DISBURSEMENT.getValue().equals(accountTransferDTO.getFromTransferType())) {
                loanTransaction = this.loanAccountDomainService.makeDisburseTransaction(accountTransferDTO.getFromAccountId(),
                        accountTransferDTO.getTransactionDate(), accountTransferDTO.getTransactionAmount(),
                        accountTransferDTO.getPaymentDetail(), accountTransferDTO.getNoteText(), externalId);
            } else {
                loanTransaction = this.loanAccountDomainService.makeRefund(accountTransferDTO.getFromAccountId(),
                        new CommandProcessingResultBuilder(), accountTransferDTO.getTransactionDate(),
                        accountTransferDTO.getTransactionAmount(), accountTransferDTO.getPaymentDetail(), accountTransferDTO.getNoteText(),
                        externalId);
            }

            BigDecimal netLoanDisbursementAmount = accountTransferDTO.getTransactionAmount();
            if (accountTransferDTO instanceof CustomAccountTransferDTO) {
                netLoanDisbursementAmount = ((CustomAccountTransferDTO) accountTransferDTO).getNetLoanDisbursementAmount();
                fromLoanAccount.setNetDisbursalAmount(netLoanDisbursementAmount);
            }

            final SavingsAccountTransaction deposit = this.savingsAccountDomainService.handleDeposit(toSavingsAccount,
                    accountTransferDTO.getFmt(), accountTransferDTO.getTransactionDate(), netLoanDisbursementAmount,
                    accountTransferDTO.getPaymentDetail(), isAccountTransfer, isRegularTransaction, backdatedTxnsAllowedTill);
            accountTransferDetails = this.accountTransferAssembler.assembleLoanToSavingsTransfer(accountTransferDTO, fromLoanAccount,
                    toSavingsAccount, deposit, loanTransaction);
            this.accountTransferDetailRepository.saveAndFlush(accountTransferDetails);
            transferTransactionId = accountTransferDetails.getId();

            // if the savings account is GSIM, update its parent as well
            if (toSavingsAccount.getGsim() != null) {
                GroupSavingsIndividualMonitoring gsim = gsimRepository.findById(toSavingsAccount.getGsim().getId()).orElseThrow();
                BigDecimal currentBalance = gsim.getParentDeposit();
                BigDecimal newBalance = currentBalance.add(netLoanDisbursementAmount);
                gsim.setParentDeposit(newBalance);
                gsimRepository.save(gsim);
            }
        } else {
            throw new GeneralPlatformDomainRuleException("error.msg.accounttransfer.loan.to.loan.not.supported",
                    "Account transfer from loan to another loan is not supported");
        }

        return transferTransactionId;
    }
}
