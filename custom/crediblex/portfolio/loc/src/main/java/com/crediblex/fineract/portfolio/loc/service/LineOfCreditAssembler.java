package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditApprovedBuyers;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditClientOptionalInfo;
import com.google.gson.JsonArray;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LineOfCreditAssembler {

    private final SavingsAccountRepository savingsAccountRepository;
    private final ClientRepositoryWrapper clientRepository;

    protected LineOfCredit assembleFrom(final JsonCommand command) {
        final String productType = command.stringValueOfParameterNamed("productType");
        final BigDecimal maximumAmount = command.bigDecimalValueOfParameterNamed("maximumAmount");
        final LocalDate startDate = command.localDateValueOfParameterNamed("startDate");
        final LocalDate endDate = command.localDateValueOfParameterNamed("endDate");

        final BigDecimal approvedCreditFacilityAmount = command.hasParameter("approvedCreditFacilityAmount") ?
                command.bigDecimalValueOfParameterNamed("approvedCreditFacilityAmount") : null;
        final String externalId = command.hasParameter("externalId") ?
                command.stringValueOfParameterNamed("externalId") : null;
        final String currency = command.hasParameter("currencyCode") ?
                command.stringValueOfParameterNamed("currencyCode") : null;
        final BigDecimal advancePercentage = command.hasParameter("advancePercentage") ?
                command.bigDecimalValueOfParameterNamed("advancePercentage") : null;
        final Integer tenorDays = command.hasParameter("tenorDays") ?
                command.integerValueOfParameterNamed("tenorDays") : null;

        List<LineOfCreditApprovedBuyers> approvedBuyers;
        if (command.parameterExists("approvedBuyers")) {
            approvedBuyers = new ArrayList<>();
            JsonArray dpArray = command.arrayOfParameterNamed("approvedBuyers");
            if (dpArray != null) {
                dpArray.forEach(
                        t -> approvedBuyers.add(new LineOfCreditApprovedBuyers(t.getAsJsonObject().get("name").getAsString(), null)));
            }

        } else {
            approvedBuyers = null;
        }

        final String cashMarginType = command.hasParameter("cashMarginType") ?
                command.stringValueOfParameterNamed("cashMarginType") : null;
        final BigDecimal cashMarginValue = command.hasParameter("cashMarginValue") ?
                command.bigDecimalValueOfParameterNamed("cashMarginValue") : null;

        final String rateType = command.hasParameter("rateType") ?
                command.stringValueOfParameterNamed("rateType") : null;
        final BigDecimal annualInterestRate = command.hasParameter("annualInterestRate") ?
                command.bigDecimalValueOfParameterNamed("annualInterestRate") : null;
        final Boolean isInterestUpfrontOrPostDisbursal = command.hasParameter("isInterestUpfrontOrPostDisbursal") ?
                command.booleanObjectValueOfParameterNamed("isInterestUpfrontOrPostDisbursal") : null;


        final String clientCompanyName = command.hasParameter("clientCompanyName") ?
                command.stringValueOfParameterNamed("clientCompanyName") : null;
        final String clientContactPersonName = command.hasParameter("clientContactPersonName") ?
                command.stringValueOfParameterNamed("clientContactPersonName") : null;
        final String clientContactPersonPhone = command.hasParameter("clientContactPersonPhone") ?
                command.stringValueOfParameterNamed("clientContactPersonPhone") : null;
        final String clientContactPersonEmail = command.hasParameter("clientContactPersonEmail") ?
                command.stringValueOfParameterNamed("clientContactPersonEmail") : null;
        final String authorizedSignatoryName = command.hasParameter("authorizedSignatoryName") ?
                command.stringValueOfParameterNamed("authorizedSignatoryName") : null;
        final String authorizedSignatoryPhone = command.hasParameter("authorizedSignatoryPhone") ?
                command.stringValueOfParameterNamed("authorizedSignatoryPhone") : null;
        final String authorizedSignatoryEmail = command.hasParameter("authorizedSignatoryEmail") ?
                command.stringValueOfParameterNamed("authorizedSignatoryEmail") : null;

        LineOfCreditClientOptionalInfo optionalClientInfo = new LineOfCreditClientOptionalInfo(clientCompanyName,
                clientContactPersonName, clientContactPersonPhone, clientContactPersonEmail,
                authorizedSignatoryName, authorizedSignatoryPhone, authorizedSignatoryEmail);

        final String va = command.hasParameter("va") ?
                command.stringValueOfParameterNamed("va") : null;

        final String distributionPartner = command.hasParameter("distributionPartner") ?
                command.stringValueOfParameterNamed("distributionPartner") : null;


        final String specialConditions = command.hasParameter("specialConditions") ?
                command.stringValueOfParameterNamed("specialConditions") : null;


        final Long clientId = command.longValueOfParameterNamed("clientId");

        final Client client = this.clientRepository.findOneWithNotFoundDetection(clientId);

        // settlement savings account linkage (optional)
        SavingsAccount settlementAccount = null;
        if (command.hasParameter("settlementSavingsAccountId")) {
            final Long settlementId = command.longValueOfParameterNamed("settlementSavingsAccountId");
            if (settlementId != null) {
                settlementAccount = savingsAccountRepository.findById(settlementId)
                        .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.settlement.savings.not.found", "Settlement savings account not found", "settlementSavingsAccountId"));

                if(!settlementAccount.getCurrency().getCode().equals(currency)){
                    throw new PlatformApiDataValidationException("error.msg.loc.currency.mismatch.settlement.savings",
                            "LOC currency must match settlement savings account currency", "currency");
                }
            }
        }

        final LineOfCredit lineOfCredit = new LineOfCredit(client, productType, maximumAmount, startDate, endDate, approvedCreditFacilityAmount, externalId, currency, advancePercentage, tenorDays, cashMarginType, cashMarginValue,null,rateType,annualInterestRate,isInterestUpfrontOrPostDisbursal,va,
                specialConditions,optionalClientInfo,approvedBuyers);
        if (settlementAccount != null) {
            lineOfCredit.setSettlementSavingsAccount(settlementAccount);
        }

        return lineOfCredit;
    }
}
