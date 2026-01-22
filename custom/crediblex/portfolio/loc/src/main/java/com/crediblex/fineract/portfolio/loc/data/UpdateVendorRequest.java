package com.crediblex.fineract.portfolio.loc.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateVendorRequest {

    @JsonProperty("name")
    private String name;

    public UpdateVendorRequest(String name) {
        this.name = name;
    }
}
