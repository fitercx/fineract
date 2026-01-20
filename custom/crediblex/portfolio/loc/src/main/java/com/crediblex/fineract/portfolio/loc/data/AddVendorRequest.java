package com.crediblex.fineract.portfolio.loc.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class AddVendorRequest {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("creditLimit")
    private BigDecimal creditLimit;
    
    @JsonProperty("losExternalId")
    private String losExternalId;
    
    public AddVendorRequest(String name, BigDecimal creditLimit, String losExternalId) {
        this.name = name;
        this.creditLimit = creditLimit;
        this.losExternalId = losExternalId;
    }
}