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

package com.crediblex.fineract.portfolio.loc.commands;

import com.crediblex.fineract.portfolio.loc.service.LineOfCreditWritePlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Service;

/**
 * Command handler for adjusting credit limit. This unified handler automatically determines
 * whether to increase or decrease the credit limit based on the new target amount provided.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@CommandType(entity = "LINE_OF_CREDIT", action = "ADJUST")
public class LineOfCreditAdjustCreditLimitCommandHandler implements NewCommandSourceHandler {

    private final LineOfCreditWritePlatformService lineOfCreditWritePlatformService;

    @Override
    public CommandProcessingResult processCommand(JsonCommand command) {
        return lineOfCreditWritePlatformService.adjustCreditLimit(command.entityId(), command);
    }
}
