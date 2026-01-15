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
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request DTO for managing approved buyers/suppliers on active Line of Credit")
public class ManageApprovedBuyersRequest {

    @Schema(description = "List of approved buyers/suppliers")
    @NotEmpty(message = "At least one approved buyer/supplier must be provided")
    private List<ApprovedBuyerSupplier> approvedBuyers;

    @Schema(example = "Managing approved buyers for LOC", description = "Optional note for the action")
    private String note;

    @Schema(example = "dd MMMM yyyy", description = "Date format")
    private String dateFormat = "dd MMMM yyyy";

    @Schema(example = "en", description = "Locale")
    private String locale = "en";

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApprovedBuyerSupplier {

        @Schema(example = "ACME Corp", description = "Name of approved buyer/supplier")
        private String name;
    }

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error serializing request to JSON", e);
        }
    }
}
