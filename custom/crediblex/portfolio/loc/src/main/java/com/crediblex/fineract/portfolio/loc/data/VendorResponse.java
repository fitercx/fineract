package com.crediblex.fineract.portfolio.loc.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("creditLimit")
    private BigDecimal creditLimit;

    @JsonProperty("losExternalId")
    private String losExternalId;

    @JsonProperty("lineOfCreditId")
    private Long lineOfCreditId;
}
