package com.crediblex.fineract.portfolio.loc.serialization;

//public class LineOfCreditV1Mapper {
//}

// File: 'fineract-provider/src/main/java/com/crediblex/fineract/portfolio/loc/serialization/LineOfCreditDataV1Mapper.java'
//package com.crediblex.fineract.portfolio.loc.serialization;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.time.format.DateTimeFormatter;
import org.apache.fineract.avro.generic.v1.CurrencyDataV1;
import org.apache.fineract.avro.loc.v1.LineOfCreditDataV1;
//import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.client.models.MonetaryCurrency;
import org.springframework.stereotype.Component;

@Component
public class LineOfCreditV1Mapper {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    public LineOfCreditDataV1 map(LineOfCredit loc) {        CurrencyDataV1 currency = null;
        MonetaryCurrency mc = new MonetaryCurrency();
//        if (mc != null) {
            currency = CurrencyDataV1.newBuilder()
                    .setCode(mc.getCode())
                    .setDecimalPlaces(mc.getDigitsAfterDecimal())
                    .setInMultiplesOf(mc.getCurrencyInMultiplesOf())
                    .build();
//        }


        LineOfCreditDataV1 dto = LineOfCreditDataV1.newBuilder()
                .setId(loc.getId())
//                .setAccountNo(loc.getAccountNo())
                .setExternalId(loc.getExternalId())
                .setClientId(loc.getClient() != null ? loc.getClient().getId() : null)
                .setProductType(loc.getProductType() != null ? loc.getProductType().name() : null)
//                .setCurrency(currency)
                .setMaximumAmount(loc.getMaximumAmount())
                .setApprovedCreditFacilityAmount(loc.getApprovedCreditFacilityAmount())
                .setAvailableBalance(loc.getSummary() != null ? loc.getSummary().getAvailableBalance() : null)
                .setStatus(loc.getStatus() != null ? loc.getStatus().name() : null)
                .setStartDate(loc.getStartDate() != null ? loc.getStartDate().format(ISO) : null)
                .setEndDate(loc.getEndDate() != null ? loc.getEndDate().format(ISO) : null)
                .setInterimReviewDate(loc.getInterimReviewDate() != null ? loc.getInterimReviewDate().format(ISO) : null)
                .setAdvancePercentage(loc.getAdvancePercentage())
                .setTenorDays(loc.getTenorDays())
                .setCashMarginType(loc.getCashMarginType() != null ? loc.getCashMarginType().name() : null)
                .setCashMarginValue(loc.getCashMarginValue())
                .setRateType(loc.getRateType() != null ? loc.getRateType().name() : null)
                .setAnnualInterestRate(loc.getAnnualInterestRate())
                .setVirtualAccount(loc.getVirtualAccount())
                .setSpecialConditions(loc.getSpecialConditions())
                .setReviewPeriod(loc.getReviewPeriod())
                .setLoanOfficerId(loc.getLoanOfficerId())
                .setDistributionPartner(loc.getDistributionPartner())
                .setMaximumAmount(loc.getMaximumAmount())
                .setInterestChargeTime(loc.getInterestChargeTime() != null ? loc.getInterestChargeTime().name() : null)
                .setSettlementSavingsAccountId(loc.getSettlementSavingsAccount() != null ? loc.getSettlementSavingsAccount().getId() : null)
                .build();

        return dto;
    }
}
