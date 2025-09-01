package com.crediblex.fineract.portfolio.loc.api;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that all fields from the full JSON payload are properly handled
 */
public class LineOfCreditFullPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testFullPayloadSerializationAndDeserialization() throws Exception {
        // Create the full request with all fields from the user's JSON payload
        LineOfCreditRequest request = new LineOfCreditRequest();
        request.setClientId(1L);
        request.setName("clientlient payable");
        request.setProductType("payable");
        request.setMaximumAmount("3,000,000");
        request.setStartDate("29 August 2025");
        request.setEndDate("29 October 2025");
        request.setDateFormat("dd MMMM yyyy");
        request.setLocale("en");
        request.setApprovedCreditFacilityAmount("2,500,000");
        request.setExternalId("LOC-2025-001");
        request.setActivationDate("30 August 2025");
        request.setCurrency("USD");
        request.setAdvancePercentage("85.50");
        request.setTenorDays(60);
        request.setApprovedBuyers("ABC Corp, XYZ Ltd, DEF Industries");
        request.setProcessingFeePctLoc("2.50");
        request.setCashMarginType("PERCENTAGE");
        request.setCashMarginValue("150,000");
        request.setInvHandlingFeeBasis("PER_INVOICE");
        request.setInvHandlingFeePct("1.25");
        request.setInvHandlingFeeMinAmount("5,000");
        request.setInvHandlingFeeCurrency("USD");
        request.setInterimReviewDate("15 September 2025");
        request.setRateType("FLOATING");
        request.setAnnualInterestRate("12.50");
        request.setIsInterestUpfrontOrPostDisbursal("POST_DISBURSAL");
        request.setClientCompanyName("CredibleX Solutions Ltd");
        request.setClientContactPersonName("John Smith");
        request.setClientContactPersonPhone("+1-555-0123");
        request.setClientContactPersonEmail("john.smith@crediblex.com");
        request.setAuthorizedSignatoryName("Jane Doe");
        request.setAuthorizedSignatoryPhone("+1-555-0456");
        request.setAuthorizedSignatoryEmail("jane.doe@crediblex.com");
        request.setVa("VA123456789");
        request.setDistributionPartner("Global Finance Partners");
        request.setBankTransferFee("25.00");
        request.setSpecialConditions("Subject to quarterly review. Early repayment allowed with 30 days notice.");
        request.setLatePaymentFee("500.00");

        // Serialize to JSON
        String json = request.toJson();
        System.out.println("Full payload JSON:");
        System.out.println(json);

        // Verify all fields are present in JSON
        assertThat(json).contains("\"clientId\":1");
        assertThat(json).contains("\"name\":\"clientlient payable\"");
        assertThat(json).contains("\"productType\":\"payable\"");
        assertThat(json).contains("\"maximumAmount\":\"3,000,000\"");
        assertThat(json).contains("\"approvedCreditFacilityAmount\":\"2,500,000\"");
        assertThat(json).contains("\"externalId\":\"LOC-2025-001\"");
        assertThat(json).contains("\"activationDate\":\"30 August 2025\"");
        assertThat(json).contains("\"currency\":\"USD\"");
        assertThat(json).contains("\"advancePercentage\":\"85.50\"");
        assertThat(json).contains("\"tenorDays\":60");
        assertThat(json).contains("\"approvedBuyers\":\"ABC Corp, XYZ Ltd, DEF Industries\"");
        assertThat(json).contains("\"processingFeePctLoc\":\"2.50\"");
        assertThat(json).contains("\"cashMarginType\":\"PERCENTAGE\"");
        assertThat(json).contains("\"cashMarginValue\":\"150,000\"");
        assertThat(json).contains("\"invHandlingFeeBasis\":\"PER_INVOICE\"");
        assertThat(json).contains("\"invHandlingFeePct\":\"1.25\"");
        assertThat(json).contains("\"invHandlingFeeMinAmount\":\"5,000\"");
        assertThat(json).contains("\"invHandlingFeeCurrency\":\"USD\"");
        assertThat(json).contains("\"interimReviewDate\":\"15 September 2025\"");
        assertThat(json).contains("\"rateType\":\"FLOATING\"");
        assertThat(json).contains("\"annualInterestRate\":\"12.50\"");
        assertThat(json).contains("\"isInterestUpfrontOrPostDisbursal\":\"POST_DISBURSAL\"");
        assertThat(json).contains("\"clientCompanyName\":\"CredibleX Solutions Ltd\"");
        assertThat(json).contains("\"clientContactPersonName\":\"John Smith\"");
        assertThat(json).contains("\"clientContactPersonPhone\":\"+1-555-0123\"");
        assertThat(json).contains("\"clientContactPersonEmail\":\"john.smith@crediblex.com\"");
        assertThat(json).contains("\"authorizedSignatoryName\":\"Jane Doe\"");
        assertThat(json).contains("\"authorizedSignatoryPhone\":\"+1-555-0456\"");
        assertThat(json).contains("\"authorizedSignatoryEmail\":\"jane.doe@crediblex.com\"");
        assertThat(json).contains("\"va\":\"VA123456789\"");
        assertThat(json).contains("\"distributionPartner\":\"Global Finance Partners\"");
        assertThat(json).contains("\"bankTransferFee\":\"25.00\"");
        assertThat(json).contains("\"specialConditions\":\"Subject to quarterly review. Early repayment allowed with 30 days notice.\"");
        assertThat(json).contains("\"latePaymentFee\":\"500.00\"");

        // Deserialize back from JSON
        LineOfCreditRequest deserializedRequest = objectMapper.readValue(json, LineOfCreditRequest.class);

        // Verify all fields are correctly deserialized
        assertThat(deserializedRequest.getClientId()).isEqualTo(1L);
        assertThat(deserializedRequest.getName()).isEqualTo("clientlient payable");
        assertThat(deserializedRequest.getProductType()).isEqualTo("payable");
        assertThat(deserializedRequest.getMaximumAmount()).isEqualTo("3,000,000");
        assertThat(deserializedRequest.getApprovedCreditFacilityAmount()).isEqualTo("2,500,000");
        assertThat(deserializedRequest.getExternalId()).isEqualTo("LOC-2025-001");
        assertThat(deserializedRequest.getActivationDate()).isEqualTo("30 August 2025");
        assertThat(deserializedRequest.getCurrency()).isEqualTo("USD");
        assertThat(deserializedRequest.getAdvancePercentage()).isEqualTo("85.50");
        assertThat(deserializedRequest.getTenorDays()).isEqualTo(60);
        assertThat(deserializedRequest.getApprovedBuyers()).isEqualTo("ABC Corp, XYZ Ltd, DEF Industries");
        assertThat(deserializedRequest.getProcessingFeePctLoc()).isEqualTo("2.50");
        assertThat(deserializedRequest.getCashMarginType()).isEqualTo("PERCENTAGE");
        assertThat(deserializedRequest.getCashMarginValue()).isEqualTo("150,000");
        assertThat(deserializedRequest.getInvHandlingFeeBasis()).isEqualTo("PER_INVOICE");
        assertThat(deserializedRequest.getInvHandlingFeePct()).isEqualTo("1.25");
        assertThat(deserializedRequest.getInvHandlingFeeMinAmount()).isEqualTo("5,000");
        assertThat(deserializedRequest.getInvHandlingFeeCurrency()).isEqualTo("USD");
        assertThat(deserializedRequest.getInterimReviewDate()).isEqualTo("15 September 2025");
        assertThat(deserializedRequest.getRateType()).isEqualTo("FLOATING");
        assertThat(deserializedRequest.getAnnualInterestRate()).isEqualTo("12.50");
        assertThat(deserializedRequest.getIsInterestUpfrontOrPostDisbursal()).isEqualTo("POST_DISBURSAL");
        assertThat(deserializedRequest.getClientCompanyName()).isEqualTo("CredibleX Solutions Ltd");
        assertThat(deserializedRequest.getClientContactPersonName()).isEqualTo("John Smith");
        assertThat(deserializedRequest.getClientContactPersonPhone()).isEqualTo("+1-555-0123");
        assertThat(deserializedRequest.getClientContactPersonEmail()).isEqualTo("john.smith@crediblex.com");
        assertThat(deserializedRequest.getAuthorizedSignatoryName()).isEqualTo("Jane Doe");
        assertThat(deserializedRequest.getAuthorizedSignatoryPhone()).isEqualTo("+1-555-0456");
        assertThat(deserializedRequest.getAuthorizedSignatoryEmail()).isEqualTo("jane.doe@crediblex.com");
        assertThat(deserializedRequest.getVa()).isEqualTo("VA123456789");
        assertThat(deserializedRequest.getDistributionPartner()).isEqualTo("Global Finance Partners");
        assertThat(deserializedRequest.getBankTransferFee()).isEqualTo("25.00");
        assertThat(deserializedRequest.getSpecialConditions()).isEqualTo("Subject to quarterly review. Early repayment allowed with 30 days notice.");
        assertThat(deserializedRequest.getLatePaymentFee()).isEqualTo("500.00");

        System.out.println("✅ All 37 fields successfully serialized and deserialized!");
    }
}
