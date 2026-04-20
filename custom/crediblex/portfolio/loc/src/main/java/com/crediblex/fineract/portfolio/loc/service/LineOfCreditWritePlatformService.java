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

    /**
     * Adjust the credit limit to a new target amount. This method automatically determines whether to increase or
     * decrease the limit based on the comparison of the new amount with the current effective limit at the given date.
     * Supports decimal amounts for precise credit limit management.
     *
     * @param lineOfCreditId
     *            The ID of the line of credit to adjust
     * @param command
     *            The JSON command containing: amount (new target limit), actionDate, locale, dateFormat, and optional
     *            note
     * @return CommandProcessingResult with details of the adjustment
     */
    CommandProcessingResult adjustCreditLimit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult undoCloseLineOfCredit(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult manageApprovedBuyers(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult addVendor(Long lineOfCreditId, JsonCommand command);

    CommandProcessingResult updateVendor(Long lineOfCreditId, Long vendorId, JsonCommand command);

    CommandProcessingResult deleteVendor(Long lineOfCreditId, Long vendorId);

    /**
     * Block (reserve) a portion of the credit limit, preventing borrowers from drawing down against that amount.
     * <p>
     * Available Amount = Credit Limit − Blocked Amount − Consumed Amount
     */
    CommandProcessingResult blockAmount(Long lineOfCreditId, JsonCommand command);

    /**
     * Unblock (release) a previously blocked amount, restoring it to the drawable available balance.
     */
    CommandProcessingResult unblockAmount(Long lineOfCreditId, JsonCommand command);
}
