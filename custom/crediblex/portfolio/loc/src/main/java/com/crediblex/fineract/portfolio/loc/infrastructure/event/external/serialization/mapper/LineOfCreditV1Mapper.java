package com.crediblex.fineract.portfolio.loc.infrastructure.event.external.serialization.mapper;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.time.format.DateTimeFormatter;
import org.apache.fineract.avro.loc.v1.LineOfCreditDataV1;
import org.springframework.stereotype.Component;

@Component
public class LineOfCreditV1Mapper {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    public LineOfCreditDataV1 map(LineOfCredit loc) {

        LineOfCreditDataV1 dto = LineOfCreditDataV1.newBuilder().setId(loc.getId()).setExternalId(loc.getExternalId())
                .setClientId(loc.getClient() != null ? loc.getClient().getId() : null)
                .setProductType(loc.getProductType() != null ? loc.getProductType().name() : null).setMaximumAmount(loc.getMaximumAmount())
                .setApprovedCreditFacilityAmount(loc.getApprovedCreditFacilityAmount())
                .setAvailableBalance(loc.getSummary() != null ? loc.getSummary().getAvailableBalance() : null)
                .setStatus(loc.getStatus() != null ? loc.getStatus().name() : null)
                .setStartDate(loc.getStartDate() != null ? loc.getStartDate().format(ISO) : null)
                .setEndDate(loc.getEndDate() != null ? loc.getEndDate().format(ISO) : null)
                .setInterimReviewDate(loc.getInterimReviewDate() != null ? loc.getInterimReviewDate().format(ISO) : null)
                .setAdvancePercentage(loc.getAdvancePercentage()).setTenorDays(loc.getTenorDays())
                .setCashMarginType(loc.getCashMarginType() != null ? loc.getCashMarginType().name() : null)
                .setCashMarginValue(loc.getCashMarginValue()).setRateType(loc.getRateType() != null ? loc.getRateType().name() : null)
                .setAnnualInterestRate(loc.getAnnualInterestRate()).setVirtualAccount(loc.getVirtualAccount())
                .setSpecialConditions(loc.getSpecialConditions()).setReviewPeriod(loc.getReviewPeriod())
                .setLoanOfficerId(loc.getLoanOfficerId()).setDistributionPartner(loc.getDistributionPartner())
                .setMaximumAmount(loc.getMaximumAmount())
                .setInterestChargeTime(loc.getInterestChargeTime() != null ? loc.getInterestChargeTime().name() : null)
                .setSettlementSavingsAccountId(loc.getSettlementSavingsAccount() != null ? loc.getSettlementSavingsAccount().getId() : null)
                .build();

        return dto;
    }
}
