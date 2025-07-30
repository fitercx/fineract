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

package com.crediblex.fineract.loc.service;

import com.crediblex.fineract.loc.data.LineOfCreditData;
import com.crediblex.fineract.loc.data.LineOfCreditTransactionData;
import com.crediblex.fineract.loc.domain.LineOfCredit;
import com.crediblex.fineract.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.loc.repository.LineOfCreditRepository;
import com.crediblex.fineract.loc.repository.LineOfCreditTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineOfCreditReadPlatformServiceImpl implements LineOfCreditReadPlatformService {

    private final PlatformSecurityContext context;
    private final LineOfCreditRepository lineOfCreditRepository;
    private final LineOfCreditTransactionRepository transactionRepository;
    private final ClientReadPlatformService clientReadPlatformService;

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

    @Override
    public Collection<LineOfCreditTransactionData> retrieveAllTransactions(Long lineOfCreditId) {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final List<LineOfCreditTransaction> transactions = this.transactionRepository.findByLineOfCreditIdOrderByTransactionDateDesc(lineOfCreditId);
        return transactions.stream()
                .map(this::assembleTransactionData)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LineOfCreditTransactionData> retrieveAllTransactions(Long lineOfCreditId, Pageable pageable) {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final Page<LineOfCreditTransaction> transactions = this.transactionRepository.findByLineOfCreditIdOrderByTransactionDateDesc(lineOfCreditId, pageable);
        return transactions.map(this::assembleTransactionData);
    }

    @Override
    public LineOfCreditTransactionData retrieveTransaction(Long transactionId) {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final LineOfCreditTransaction transaction = this.transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + transactionId));

        return assembleTransactionData(transaction);
    }

    @Override
    public LineOfCreditTransactionData retrieveTransactionTemplate() {
        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final Collection<EnumOptionData> transactionTypeOptions = getTransactionTypeOptions();
        return LineOfCreditTransactionData.template(transactionTypeOptions);
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
                lineOfCredit.getCreatedDate().toLocalDate(),
                lineOfCredit.getCreatedBy(),
                lineOfCredit.getLastModifiedDate().toLocalDate(),
                lineOfCredit.getLastModifiedBy()
        );
    }

    private LineOfCreditTransactionData assembleTransactionData(LineOfCreditTransaction transaction) {
        final EnumOptionData transactionType = getTransactionTypeEnumOptionData(transaction.getTransactionType());

        return LineOfCreditTransactionData.instance(
                transaction.getId(),
                transaction.getLineOfCredit().getId(),
                transactionType,
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getTransactionDate(),
                transaction.getReferenceNumber(),
                transaction.getDescription(),
                transaction.getCreatedDate(),
                transaction.getCreatedBy(),
                transaction.getLastModifiedDate(),
                transaction.getLastModifiedBy()
        );
    }

    private EnumOptionData getActivationStatusEnumOptionData(LineOfCredit.ActivationStatus status) {
        return EnumOptionData.instance(status.ordinal(), status.name());
    }

    private EnumOptionData getTransactionTypeEnumOptionData(LineOfCreditTransaction.TransactionType type) {
        return EnumOptionData.instance(type.ordinal(), type.name());
    }

    private Collection<EnumOptionData> getActivationStatusOptions() {
        final List<EnumOptionData> options = new ArrayList<>();
        for (LineOfCredit.ActivationStatus status : LineOfCredit.ActivationStatus.values()) {
            options.add(EnumOptionData.instance(status.ordinal(), status.name()));
        }
        return options;
    }

    private Collection<String> getProductTypeOptions() {
        final List<String> options = new ArrayList<>();
        options.add("BUSINESS");
        options.add("PERSONAL");
        options.add("OVERDRAFT");
        return options;
    }

    private Collection<EnumOptionData> getTransactionTypeOptions() {
        final List<EnumOptionData> options = new ArrayList<>();
        for (LineOfCreditTransaction.TransactionType type : LineOfCreditTransaction.TransactionType.values()) {
            options.add(EnumOptionData.instance(type.ordinal(), type.name()));
        }
        return options;
    }
} 