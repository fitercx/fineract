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

package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.data.AddVendorRequest;
import com.crediblex.fineract.portfolio.loc.data.VendorResponse;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;

public interface LineOfCreditWritePlatformService {

    CommandProcessingResult createLineOfCredit(JsonCommand command, Long clientId);

    CommandProcessingResult updateLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult activateLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult deactivateLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult reactivateLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult deleteLineOfCredit(Long lineOfCreditId);

    CommandProcessingResult approveLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult closeLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult increaseCreditLimit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult reduceCreditLimit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult undoCloseLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult manageApprovedBuyers(Long lineOfCreditId, JsonCommand command);

    VendorResponse addVendor(Long lineOfCreditId, String requestBody);
}
