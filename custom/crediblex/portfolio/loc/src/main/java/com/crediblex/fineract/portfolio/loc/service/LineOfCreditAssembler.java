package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LocCashMarginType;
import com.crediblex.fineract.portfolio.loc.data.LocInterestChargeTime;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditApprovedBuyers;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditClientOptionalInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LineOfCreditAssembler {

    private final SavingsAccountRepository savingsAccountRepository;
    private final ClientRepositoryWrapper clientRepository;

    public LineOfCredit assembleFrom(final LineOfCreditRequest request, final Long clientId) {
        // Get client first
        final Client client = this.clientRepository.findOneWithNotFoundDetection(clientId);

        // Extract basic fields from request
        final LocProductType productType = LocProductType.fromInt(request.getProductType());
        final BigDecimal maximumAmount = new BigDecimal(request.getMaxCreditLimit().toString());

        // Parse dates using the provided format and locale
        final Locale locale = Locale.forLanguageTag(request.getLocale());
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(request.getDateFormat(), locale);

        LocalDate startDate = null;
        if (request.getStartDate() != null && !request.getStartDate().isEmpty()) {
            startDate = DateUtils.parseLocalDate(request.getStartDate(), request.getDateFormat(), locale);
        }

        if (request.getEndDate() != null && !request.getEndDate().isEmpty() && startDate != null
                && startDate.isAfter(LocalDate.parse(request.getEndDate(), formatter))) {
            throw new PlatformApiDataValidationException("error.msg.end.date.cannot.be.before.start.date",
                    "End date cannot be before start date", "endDate");
        }

        LocalDate endDate = null;
        if (request.getEndDate() != null && !request.getEndDate().isEmpty()) {
            endDate = DateUtils.parseLocalDate(request.getEndDate(), request.getDateFormat(), locale);
        }

        final String externalId = request.getExternalId();
        final String currency = request.getCurrencyCode();
        final BigDecimal advancePercentage = request.getAdvancePercentage() != null ? new BigDecimal(request.getAdvancePercentage()) : null;
        final Integer tenorDays = request.getTenorDays();

        // Handle approved buyers
        List<LineOfCreditApprovedBuyers> approvedBuyers = null;
        if (request.getApprovedBuyers() != null && !request.getApprovedBuyers().isEmpty()) {
            approvedBuyers = new ArrayList<>();
            for (LineOfCreditRequest.ApprovedBuyer buyer : request.getApprovedBuyers()) {
                approvedBuyers.add(new LineOfCreditApprovedBuyers(buyer.getName(), null));
            }
        }

        // Convert cash margin type from Integer to Enum
        final LocCashMarginType cashMarginType = request.getCashMarginType() != null
                ? LocCashMarginType.fromInt(request.getCashMarginType())
                : null;
        final BigDecimal cashMarginValue = request.getCashMarginValue() != null ? new BigDecimal(request.getCashMarginValue().toString())
                : null;

        // Parse interim review date if provided
        LocalDate interimReviewDate = null;

        if (request.getInterimReviewDate() != null && !request.getInterimReviewDate().isEmpty()) {
            interimReviewDate = DateUtils.parseLocalDate(request.getInterimReviewDate(), request.getDateFormat(), locale);

            if (DateUtils.getLocalDateOfTenant().isAfter(interimReviewDate)) {
                throw new PlatformApiDataValidationException("error.msg.interim.review.date.cannot.be.in.the.past",
                        "Interim review date cannot be in the past", "interimReviewDate");
            }
        }

        // Set rateType to null as it's not in the request payload
        final LocInterestChargeTime interestPaymentType = LocInterestChargeTime.fromInt(request.getInterestChargeTime());

        final BigDecimal annualInterestRate = request.getAnnualInterestRate() != null
                ? new BigDecimal(request.getAnnualInterestRate().toString())
                : null;

        // Create optional client info from request
        LineOfCreditClientOptionalInfo optionalClientInfo = new LineOfCreditClientOptionalInfo(request.getClientCompanyName(),
                request.getClientContactPersonName(), request.getClientContactPersonPhone(), request.getClientContactPersonEmail(),
                request.getAuthorizedSignatoryName(), request.getAuthorizedSignatoryPhone(), request.getAuthorizedSignatoryEmail());

        final String va = request.getVirtualAccount();
        final String distributionPartner = request.getDistributionPartner();
        final String specialConditions = request.getSpecialConditions();

        final Integer reviewPeriod = request.getReviewPeriod();

        final Long loanOfficerId = request.getLoanOfficerId();

        // Convert interestChargeTime from Integer to Enum
        final LocInterestChargeTime interestChargeTime = request.getInterestChargeTime() != null
                ? LocInterestChargeTime.fromInt(request.getInterestChargeTime())
                : null;

        // Handle settlement savings account linkage (optional)
        SavingsAccount settlementAccount = null;
        if (request.getSettlementSavingsAccountId() != null) {
            final Long settlementId = request.getSettlementSavingsAccountId().longValue();
            settlementAccount = savingsAccountRepository.findById(settlementId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.settlement.savings.not.found",
                            "Settlement savings account not found", "settlementSavingsAccountId"));

            if (!settlementAccount.getCurrency().getCode().equals(currency)) {
                throw new PlatformApiDataValidationException("error.msg.loc.currency.mismatch.settlement.savings",
                        "LOC currency must match settlement savings account currency", "currency");
            }
        }

        // Create LineOfCredit using the updated constructor
        final LineOfCredit lineOfCredit = new LineOfCredit(client, productType, maximumAmount, startDate, endDate, null, externalId,
                currency, advancePercentage, tenorDays, cashMarginType, cashMarginValue, interimReviewDate, interestPaymentType,
                annualInterestRate, va, specialConditions, reviewPeriod, loanOfficerId, distributionPartner, interestChargeTime,
                optionalClientInfo, approvedBuyers);

        if (settlementAccount != null) {
            lineOfCredit.setSettlementSavingsAccount(settlementAccount);
        }

        return lineOfCredit;
    }

}
