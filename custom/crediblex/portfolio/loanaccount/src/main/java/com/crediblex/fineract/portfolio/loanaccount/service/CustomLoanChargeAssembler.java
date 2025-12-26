package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CustomLoanChargeAssembler extends LoanChargeAssembler {

    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;
    private final ExternalIdFactory externalIdFactory;

    public CustomLoanChargeAssembler(FromJsonHelper fromApiJsonHelper, ChargeRepositoryWrapper chargeRepository,
            LoanChargeRepository loanChargeRepository, LoanProductRepository loanProductRepository, ExternalIdFactory externalIdFactory,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(fromApiJsonHelper, chargeRepository, loanChargeRepository, loanProductRepository, externalIdFactory);
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
        this.externalIdFactory = externalIdFactory;

    }

    @Override
    public LoanCharge createNewFromJson(final Loan loan, final Charge chargeDefinition, final JsonCommand command,
            final LocalDate dueDate) {
        final Locale locale = command.extractLocale();
        final BigDecimal amount = command.bigDecimalValueOfParameterNamed("amount", locale);
        final boolean factorRateEnabled = command.booleanPrimitiveValueOfParameterNamed(LoanApiConstants.FACTOR_RATE_ENABLED_PARAM_NAME);
        final BigDecimal factorRate = command.bigDecimalValueOfParameterNamed(LoanApiConstants.FACTOR_RATE_PARAM_NAME);

        final ChargeTimeType chargeTime = null;
        final ChargeCalculationType chargeCalculation = null;
        final ChargePaymentMode chargePaymentMode = null;
        BigDecimal amountPercentageAppliedTo = BigDecimal.ZERO;
        Optional<LoanLineOfCreditParams> loanLineOfCreditParamsOptional = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());

        boolean isReceivableLineOfCredit = loanLineOfCreditParamsOptional.isPresent()
                && loanLineOfCreditParamsOptional.get().getLineOfCredit().getProductType().isReceivable();

        switch (ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation())) {
            case PERCENT_OF_AMOUNT:
                if (command.hasParameter("principal")) {
                    amountPercentageAppliedTo = command.bigDecimalValueOfParameterNamed("principal");
                } else {
                    // For LOC Receivable loans, percentage-based charges should use approved principal
                    // (loan amount) instead of disbursed principal to ensure consistent fee calculation
                    if (isReceivableLineOfCredit) {
                        amountPercentageAppliedTo = loan.getApprovedPrincipal();
                    } else {
                        amountPercentageAppliedTo = loan.getPrincipal().getAmount();
                    }
                }

                if (isReceivableLineOfCredit) {
                    // For receivable line of credit total principal is interest + principal
                    amountPercentageAppliedTo = amountPercentageAppliedTo.add(command.bigDecimalValueOfParameterNamed("interest"));
                }
            break;
            case PERCENT_OF_AMOUNT_AND_INTEREST:
                if (command.hasParameter("principal") && command.hasParameter("interest")) {
                    amountPercentageAppliedTo = command.bigDecimalValueOfParameterNamed("principal")
                            .add(command.bigDecimalValueOfParameterNamed("interest"));
                } else {
                    // For LOC Receivable loans, percentage-based charges should use approved principal
                    // (loan amount) instead of disbursed principal to ensure consistent fee calculation
                    if (isReceivableLineOfCredit) {
                        amountPercentageAppliedTo = loan.getApprovedPrincipal().add(loan.getTotalInterest());
                    } else {
                        amountPercentageAppliedTo = loan.getPrincipal().getAmount().add(loan.getTotalInterest());
                    }
                }

                if (isReceivableLineOfCredit) {
                    // For receivable line of credit total principal is interest + principal
                    amountPercentageAppliedTo = amountPercentageAppliedTo.add(command.bigDecimalValueOfParameterNamed("interest"));
                }
            break;
            case PERCENT_OF_INTEREST:
                if (command.hasParameter("interest")) {
                    amountPercentageAppliedTo = command.bigDecimalValueOfParameterNamed("interest");
                } else {
                    amountPercentageAppliedTo = loan.getTotalInterest();
                }
            break;
            default:
            break;
        }

        BigDecimal loanCharge = BigDecimal.ZERO;
        if (ChargeTimeType.fromInt(chargeDefinition.getChargeTimeType()).equals(ChargeTimeType.INSTALMENT_FEE)) {
            BigDecimal percentage = amount;
            if (percentage == null) {
                percentage = chargeDefinition.getAmount();
            }
            loanCharge = loan.calculatePerInstallmentChargeAmount(ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation()),
                    percentage);
        }

        // If charge type is specified due date and loan is multi disburment
        // loan.
        // Then we need to get as of this loan charge due date how much amount
        // disbursed.
        if (chargeDefinition.getChargeTimeType().equals(ChargeTimeType.SPECIFIED_DUE_DATE.getValue()) && loan.isMultiDisburmentLoan()) {
            amountPercentageAppliedTo = BigDecimal.ZERO;
            for (final LoanDisbursementDetails loanDisbursementDetails : loan.getDisbursementDetails()) {
                if (!DateUtils.isAfter(loanDisbursementDetails.expectedDisbursementDate(), dueDate)) {
                    amountPercentageAppliedTo = amountPercentageAppliedTo.add(loanDisbursementDetails.principal());
                }
            }
        }

        ExternalId externalId = externalIdFactory.createFromCommand(command, "externalId");
        return new LoanCharge(loan, chargeDefinition, amountPercentageAppliedTo, amount, chargeTime, chargeCalculation, dueDate,
                chargePaymentMode, null, loanCharge, externalId, factorRateEnabled, factorRate);
    }
}
