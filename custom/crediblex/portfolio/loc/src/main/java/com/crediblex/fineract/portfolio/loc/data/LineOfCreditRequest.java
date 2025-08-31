/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loc.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request DTO for Line of Credit operations")
public class LineOfCreditRequest {

    @Schema(example = "34", description = "Client ID for the line of credit")
    private Long clientId;

    @Schema(example = "Business Credit Line", description = "Name of the line of credit")
    private String name;

    @Schema(example = "PAYABLE", description = "Type of product for the line of credit")
    private ProductType productType;

    @Schema(example = "3000000", description = "Maximum amount allowed for the line of credit (can be string or number)")
    private String maximumAmount;

    @Schema(example = "29 August 2025", description = "Start date of the line of credit")
    private String startDate;

    @Schema(example = "29 October 2025", description = "End date of the line of credit")
    private String endDate;

    @Schema(example = "dd MMMM yyyy", description = "Format of the dates provided (e.g., 'dd MMMM yyyy' for '29 August 2025')")
    private String dateFormat;

    @Schema(example = "en", description = "Locale to interpret the date format")
    private String locale;

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error serializing request to JSON", e);
        }
    }
}
