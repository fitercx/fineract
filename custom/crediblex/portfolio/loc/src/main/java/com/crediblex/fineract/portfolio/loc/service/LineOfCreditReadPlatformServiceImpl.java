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

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.repository.LineOfCreditRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LineOfCreditReadPlatformServiceImpl implements LineOfCreditReadPlatformService {

    private final PlatformSecurityContext context;
    private final LineOfCreditRepository lineOfCreditRepository;
    private final ClientReadPlatformService clientReadPlatformService;

    @Autowired
    public LineOfCreditReadPlatformServiceImpl(PlatformSecurityContext context,
                                               LineOfCreditRepository lineOfCreditRepository,
                                               ClientReadPlatformService clientReadPlatformService) {
        this.context = context;
        this.lineOfCreditRepository = lineOfCreditRepository;
        this.clientReadPlatformService = clientReadPlatformService;
    }

    @Override
    public Collection<LineOfCreditData> retrieveAllLineOfCredits() {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final List<LineOfCredit> lineOfCredits = this.lineOfCreditRepository.findAll();
        return lineOfCredits.stream()
                .map(this::assembleLineOfCreditData)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LineOfCreditData> retrieveAllLineOfCredits(Pageable pageable) {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final Page<LineOfCredit> lineOfCredits = this.lineOfCreditRepository.findAll(pageable);
        return lineOfCredits.map(this::assembleLineOfCreditData);
    }

    @Override
    public LineOfCreditData retrieveOne(Long lineOfCreditId) {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                .orElseThrow(() -> new RuntimeException("Line of credit not found with id: " + lineOfCreditId));

        return assembleLineOfCreditData(lineOfCredit);
    }

    @Override
    public LineOfCreditData retrieveTemplate() {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final Collection<EnumOptionData> activationStatusOptions = getActivationStatusOptions();
        final Collection<String> productTypeOptions = getProductTypeOptions();

        return LineOfCreditData.template(activationStatusOptions, productTypeOptions);
    }

    @Override
    public Collection<LineOfCreditData> retrieveAllLineOfCreditsForClient(Long clientId) {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final List<LineOfCredit> lineOfCredits = this.lineOfCreditRepository.findByClientId(clientId);
        return lineOfCredits.stream()
                .map(this::assembleLineOfCreditData)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<LineOfCreditData> retrieveActiveLineOfCreditsForClient(Long clientId) {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final List<LineOfCredit> lineOfCredits = this.lineOfCreditRepository.findByClientIdAndActivationStatus(clientId, LineOfCredit.ActivationStatus.ACTIVE);
        return lineOfCredits.stream()
                .map(this::assembleLineOfCreditData)
                .collect(Collectors.toList());
    }


    private LineOfCreditData assembleLineOfCreditData(LineOfCredit lineOfCredit) {
        final ClientData clientData = this.clientReadPlatformService.retrieveOne(lineOfCredit.getClient().getId());
        final EnumOptionData activationStatus = getActivationStatusEnumOptionData(lineOfCredit.getActivationStatus());

        return LineOfCreditData.instance(
                lineOfCredit.getId(),
                lineOfCredit.getClient().getId(),
                clientData,
                lineOfCredit.getName(),
                lineOfCredit.getProductType(),
                lineOfCredit.getMaximumAmount(),
                lineOfCredit.getAvailableBalance(),
                lineOfCredit.getConsumedAmount(),
                activationStatus,
                lineOfCredit.getStartDate(),
                lineOfCredit.getEndDate(),
                lineOfCredit.getCreatedDate().map(OffsetDateTime::toLocalDate).orElse(null),
                lineOfCredit.getCreatedBy().map(String::valueOf).orElse(null),
                lineOfCredit.getLastModifiedDate().map(OffsetDateTime::toLocalDate).orElse(null),
                lineOfCredit.getLastModifiedBy().map(String::valueOf).orElse(null)
        );
    }


    private EnumOptionData getActivationStatusEnumOptionData(LineOfCredit.ActivationStatus status) {
        return new EnumOptionData((long) status.ordinal(), status.name(), status.name());
    }

    private Collection<EnumOptionData> getActivationStatusOptions() {
        final List<EnumOptionData> options = new ArrayList<>();
        for (LineOfCredit.ActivationStatus status : LineOfCredit.ActivationStatus.values()) {
            options.add(new EnumOptionData((long) status.ordinal(), status.name(), status.name()));
        }
        return options;
    }

    private Collection<String> getProductTypeOptions() {
        final List<String> options = new ArrayList<>();
        options.add("Payable");
        options.add("Receivable");
        return options;
    }

} 