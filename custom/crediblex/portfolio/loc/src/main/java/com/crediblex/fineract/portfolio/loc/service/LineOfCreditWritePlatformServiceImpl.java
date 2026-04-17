/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.portfolio.loc.service;

import static com.crediblex.fineract.portfolio.loc.api.LineOfCreditApiConstants.ADJUSTED_CREDIT_LIMIT;

import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidBy;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidByRepository;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargeRepository;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditLoanBuyerSupplierDetail;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditLoanBuyerSupplierDetailRepository;
import com.crediblex.fineract.portfolio.loc.charge.service.LineOfCreditChargeDomainService;
import com.crediblex.fineract.portfolio.loc.data.AddVendorRequest;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.data.UpdateVendorRequest;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditApprovedBuyers;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNote;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNoteRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNoteType;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditSummary;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import com.crediblex.fineract.portfolio.loc.exception.ActivationInsufficientBalanceException;
import com.crediblex.fineract.portfolio.loc.exception.LineOfCreditInvalidStateException;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditActivatedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditApprovedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditClosedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditCreatedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditDeactivatedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditDecreasedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditIncreasedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditReactivatedBusinessEvent;
import com.crediblex.fineract.portfolio.loc.infrastructure.event.business.domain.LineOfCreditUpdatedBusinessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class LineOfCreditWritePlatformServiceImpl implements LineOfCreditWritePlatformService {

    private final LineOfCreditRepositoryWrapper lineOfCreditRepository;
    private final LineOfCreditReadPlatformService lineOfCreditReadPlatformService;
    private final LineOfCreditDataValidator dataValidator;
    private final SavingsAccountRepository savingsAccountRepository;
    private final LineOfCreditChargeRepository locChargeRepository;
    private final LineOfCreditChargePaidByRepository locChargePaidByRepository;
    private final LineOfCreditChargeDomainService locChargeDomainService;
    private final LineOfCreditLoanBuyerSupplierDetailRepository loanBuyerSupplierDetailRepository;
    private final SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    private final ChargeRepositoryWrapper chargeRepository;
    private final FromJsonHelper fromJsonHelper;
    private final LineOfCreditAssembler lineOfCreditAssembler;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final PlatformSecurityContext context;
    private final LineOfCreditNoteRepository lineOfCreditNoteRepository;
    private final LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService;
    private final LineOfCreditTransactionRepository lineOfCreditTransactionRepository;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Helper method to save a note for LOC actions if provided in the command
     */
    private void saveNoteIfProvided(LineOfCredit loc, JsonCommand command, LineOfCreditNoteType noteType) {
        if (command.hasParameter("note")) {
            String note = command.stringValueOfParameterNamed("note");
            if (note != null && !note.trim().isEmpty()) {
                LineOfCreditNote locNote = new LineOfCreditNote(loc, note.trim(), noteType);
                lineOfCreditNoteRepository.save(locNote);
            }
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult createLineOfCredit(JsonCommand command, Long clientId) {

        LineOfCreditRequest lcr = this.dataValidator.validateForCreate(command.json());
        LineOfCredit lineOfCredit = this.lineOfCreditAssembler.assembleFrom(lcr, clientId);

        lineOfCreditRepository.findByExternalIdWithFoundException(lineOfCredit.getExternalId());

        if (command.hasParameter("charges")) {
            JsonElement root = fromJsonHelper.parse(command.json());
            JsonArray chargesArray = fromJsonHelper.extractJsonArrayNamed("charges", root);
            if (chargesArray != null) {
                List<LineOfCreditCharge> newCharges = new ArrayList<>();
                int idx = 0;
                for (JsonElement charge : chargesArray) {
                    if (charge == null || charge.isJsonNull()) {
                        idx++;
                        continue;
                    }

                    // Extract chargeId - could be nested in the charge object or directly as "id"
                    Long chargeId = fromJsonHelper.extractLongNamed("chargeDefinitionId", charge);

                    if (chargeId == null) {
                        throw new PlatformApiDataValidationException("error.msg.loc.charge.chargeId.required",
                                "Charge chargeId or id required", "charges[" + idx + "]");
                    }

                    Charge chargeDefinition = chargeRepository.findOneWithNotFoundDetection(chargeId);
                    if (!chargeDefinition.isLineOfCreditCharge()) {

                        throw new PlatformApiDataValidationException("error.msg.loc.charge.invalid.appliesTo",
                                "Charge not configured for Line Of Credit", "chargeId");
                    }
                    if (!ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation()).equals(ChargeCalculationType.FLAT)
                            && !ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation())
                                    .equals(ChargeCalculationType.PERCENT_OF_AMOUNT)) {
                        throw new PlatformApiDataValidationException("error.msg.loc.charge.calculation.only.flat.or.percent",
                                "Only flat or percentage calculation allowed for LOC charges", "chargeId");
                    }

                    // Extract override amount - could be "overrideAmount", "editableAmount", or "amount"
                    BigDecimal overrideAmount = null;
                    if (fromJsonHelper.parameterExists("editableAmount", charge)
                            && fromJsonHelper.parameterHasValue("editableAmount", charge)) {
                        overrideAmount = fromJsonHelper.extractBigDecimalNamed("editableAmount", charge, Locale.ENGLISH);
                    }

                    LineOfCreditCharge instance = locChargeDomainService.create(lineOfCredit, chargeDefinition, overrideAmount);
                    instance.setLineOfCredit(lineOfCredit);
                    newCharges.add(instance);

                    log.debug("Created LOC charge: chargeId={}, amount={}, overrideAmount={}", chargeId, instance.getAmount(),
                            overrideAmount);
                    idx++;
                }
                lineOfCredit.replaceCharges(newCharges);
            }
        }

        final LineOfCredit savedLineOfCredit = this.lineOfCreditRepository.saveAndFlush(lineOfCredit);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditCreatedBusinessEvent(lineOfCredit));

        if (savedLineOfCredit.getApprovedBuyers() != null) {
            savedLineOfCredit.getApprovedBuyers().forEach(buyer -> buyer.setLineOfCredit(savedLineOfCredit));
        }

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(savedLineOfCredit.getId()).build();

    }

    @Override
    @Transactional
    public CommandProcessingResult updateLineOfCredit(Long lineOfCreditId, JsonCommand command) {

        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (!lineOfCredit.isEditable()) {
            throw new PlatformDataIntegrityException("error.msg.loc.update.not.allowed",
                    "Line of Credit cannot be updated in its current state: " + lineOfCredit.getStatus().name());
        }

        this.dataValidator.validateForUpdate(command.json());

        final Map<String, Object> changes = lineOfCredit.update(command);
        final JsonElement root = fromJsonHelper.parse(command.json());

        // Handle blockedAmount updates - check directly in JSON since it may not be a registered command parameter
        log.info("LOC Update: Checking for blockedAmount in payload");
        log.info("LOC Update: Raw JSON = {}", command.json());

        // Check directly in the JSON object instead of using parameterExists
        boolean hasBlockedAmount = root.isJsonObject() && root.getAsJsonObject().has("blockedAmount");
        log.info("LOC Update: hasBlockedAmount (direct check) = {}", hasBlockedAmount);

        if (hasBlockedAmount) {
            JsonElement blockedAmountElement = root.getAsJsonObject().get("blockedAmount");
            log.info("LOC Update: blockedAmountElement = {}, isJsonNull = {}", blockedAmountElement, blockedAmountElement.isJsonNull());

            if (!blockedAmountElement.isJsonNull()) {
                final BigDecimal newBlockedAmount = blockedAmountElement.getAsBigDecimal();
                log.info("LOC Update: Extracted blockedAmount = {}", newBlockedAmount);
                if (newBlockedAmount != null && newBlockedAmount.compareTo(BigDecimal.ZERO) >= 0) {
                    // Ensure summary is initialized
                    if (lineOfCredit.getSummary() == null) {
                        log.info("LOC Update: Summary is null, initializing...");
                        lineOfCredit.setSummary(LineOfCreditSummary.getInitialState());
                    }
                    final BigDecimal currentBlocked = lineOfCredit.getSummary().getBlockedAmount() != null
                            ? lineOfCredit.getSummary().getBlockedAmount()
                            : BigDecimal.ZERO;
                    log.info("LOC Update: currentBlocked = {}, newBlockedAmount = {}", currentBlocked, newBlockedAmount);
                    // Only update if value has changed
                    if (newBlockedAmount.compareTo(currentBlocked) != 0) {
                        log.info("LOC Update: Value changed, updating blockedAmount");
                        lineOfCredit.getSummary().setBlockedAmount(newBlockedAmount);
                        // Recalculate available balance: MaxLimit - Blocked - Consumed
                        final BigDecimal consumed = lineOfCredit.getSummary().getConsumedAmount() != null
                                ? lineOfCredit.getSummary().getConsumedAmount()
                                : BigDecimal.ZERO;
                        BigDecimal newAvailable = lineOfCredit.getMaximumAmount().subtract(newBlockedAmount).subtract(consumed);
                        if (newAvailable.compareTo(BigDecimal.ZERO) < 0) {
                            newAvailable = BigDecimal.ZERO;
                        }
                        lineOfCredit.getSummary().setAvailableBalance(newAvailable);
                        changes.put("blockedAmount", newBlockedAmount);
                        log.info("LOC Update: Set blockedAmount = {}, availableBalance = {}", newBlockedAmount, newAvailable);
                    } else {
                        log.info("LOC Update: Value unchanged, skipping update");
                    }
                }
            }
        }

        // handle settlement savings account update explicitly
        if (command.hasParameter("settlementSavingsAccountId")) {
            final Long settlementId = command.longValueOfParameterNamed("settlementSavingsAccountId");
            SavingsAccount settlementAccount = null;
            if (settlementId != null) {
                settlementAccount = savingsAccountRepository.findById(settlementId)
                        .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.settlement.savings.not.found",
                                "Settlement savings account not found", "settlementSavingsAccountId"));
            }
            lineOfCredit.setSettlementSavingsAccount(settlementAccount);
            changes.put("settlementSavingsAccountId", settlementId);
        }

        // update/delete charges if provided
        if (command.hasParameter("charges")) {

            // Build new charges list
            JsonArray chargesArray = fromJsonHelper.extractJsonArrayNamed("charges", root);
            List<LineOfCreditCharge> newCharges = new ArrayList<>();
            if (chargesArray != null) {
                int idx = 0;
                for (JsonElement chargeElem : chargesArray) {
                    if (chargeElem == null || chargeElem.isJsonNull()) {
                        idx++;
                        continue;
                    }
                    Long chargeId = chargeElem.getAsJsonObject().get("chargeDefinitionId").getAsLong();

                    Charge chargeDefinition = chargeRepository.findOneWithNotFoundDetection(chargeId);
                    if (!chargeDefinition.isLineOfCreditCharge()) {
                        throw new PlatformApiDataValidationException("error.msg.loc.charge.invalid.appliesTo",
                                "Charge not configured for Line Of Credit", "chargeId");
                    }
                    if (!ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation()).equals(ChargeCalculationType.FLAT)
                            && !ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation())
                                    .equals(ChargeCalculationType.PERCENT_OF_AMOUNT)) {
                        throw new PlatformApiDataValidationException("error.msg.loc.charge.calculation.only.flat.or.percent",
                                "Only flat or percentage calculation allowed for LOC charges", "chargeId");
                    }
                    BigDecimal overrideAmount = null;
                    String editableAmount = "editableAmount";
                    if (fromJsonHelper.parameterExists(editableAmount, chargeElem)
                            && fromJsonHelper.parameterHasValue(editableAmount, chargeElem)) {
                        overrideAmount = new BigDecimal(fromJsonHelper.extractStringNamed(editableAmount, chargeElem));
                    }

                    LineOfCreditCharge newCharge = locChargeDomainService.create(lineOfCredit, chargeDefinition, overrideAmount);
                    newCharge.setLineOfCredit(lineOfCredit);
                    newCharges.add(newCharge);
                    idx++;
                }
            }
            // Replace via entity helper (orphanRemoval deletes old after flush)
            lineOfCredit.replaceCharges(newCharges);
            // After save we will have IDs; store placeholder now
            changes.put("chargesCreatedCount", newCharges.size());
        }

        // update/delete approved buyers if provided
        if (command.hasParameter("approvedBuyers")) {
            JsonArray approvedBuyersArray = fromJsonHelper.extractJsonArrayNamed("approvedBuyers", root);
            List<LineOfCreditApprovedBuyers> newApprovedBuyers = new ArrayList<>();
            if (approvedBuyersArray != null) {
                approvedBuyersArray.forEach(buyerElement -> {
                    if (buyerElement != null && !buyerElement.isJsonNull()) {
                        String buyerName = buyerElement.getAsJsonObject().get("name").getAsString();
                        newApprovedBuyers.add(new LineOfCreditApprovedBuyers(buyerName, lineOfCredit));
                    }
                });
            }
            // Use the helper method to properly establish bidirectional relationship
            lineOfCredit.replaceApprovedBuyers(newApprovedBuyers);
            changes.put("approvedBuyersCount", newApprovedBuyers.size());
        }

        if (!changes.isEmpty()) {
            lineOfCredit.setStatus(LocStatus.SUBMITTED);
            lineOfCredit.resetStateChangeFields();
            this.lineOfCreditRepository.saveAndFlush(lineOfCredit);
            this.businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditUpdatedBusinessEvent(lineOfCredit));
        }

        // Save note if provided
        saveNoteIfProvided(lineOfCredit, command, LineOfCreditNoteType.UPDATE);

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();

    }

    @Override
    @Transactional
    public CommandProcessingResult activateLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

            if (!lineOfCredit.canActivate()) {
                throw new LineOfCreditInvalidStateException(lineOfCredit.getExternalId(), lineOfCredit.getStatus().name());
            }

            // Extract date and note from command
            LocalDate activationDate = command.localDateValueOfParameterNamed("actionDate");

            // Validate activation date is not before approval date
            if (lineOfCredit.getLineOfCreditStateChange().getApprovedOnDate() != null
                    && activationDate.isBefore(lineOfCredit.getLineOfCreditStateChange().getApprovedOnDate())) {
                throw new LineOfCreditInvalidStateException("Activation date cannot be before approval date");

            }

            lineOfCredit.setStatus(LocStatus.ACTIVE);
            lineOfCredit.getLineOfCreditStateChange().setActivateOnDate(activationDate);
            lineOfCredit.getLineOfCreditStateChange().setActivatedBy(context.authenticatedUser());

            applyInitialCharges(lineOfCredit);

            this.lineOfCreditRepository.saveAndFlush(lineOfCredit);
            businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditActivatedBusinessEvent(lineOfCredit));

            // Save note if provided
            saveNoteIfProvided(lineOfCredit, command, LineOfCreditNoteType.ACTIVATE);

            return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.activation.failed", e.getMessage(), "activation", e);
        } catch (final PlatformApiDataValidationException e) {
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.activation.failed", "Line of credit activation failed",
                    "lineOfCreditId", e);
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult approveLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (loc.getStatus() != LocStatus.SUBMITTED) {
            throw new LineOfCreditInvalidStateException(loc.getExternalId(), loc.getStatus().name());
        }

        // Extract date and note from command
        LocalDate approvalDate = command.localDateValueOfParameterNamed("actionDate");

        // Use current date if no date provided
        if (approvalDate == null) {
            approvalDate = LocalDate.now();
        }

        loc.setStatus(LocStatus.APPROVED);
        loc.getLineOfCreditStateChange().setApprovedOnDate(approvalDate);
        loc.getLineOfCreditStateChange().setApprovedBy(context.getAuthenticatedUserIfPresent());

        lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditApprovedBusinessEvent(loc));
        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.APPROVE);

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();

    }

    @Override
    @Transactional
    public CommandProcessingResult closeLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (!loc.canClose()) {
            throw new PlatformApiDataValidationException("error.msg.loc.cannot.close.active.draw.down",
                    "Cannot close LOC with active draw down or invalid state", "state");
        }

        // Check for unpaid charges
        List<LineOfCreditCharge> unpaid = locChargeRepository.findUnpaidOrdered(loc.getId());
        boolean hasOutstanding = unpaid.stream().anyMatch(c -> c.isActive() && !c.isPaid() && !c.isWaived()
                && c.getAmountOutstanding() != null && c.getAmountOutstanding().compareTo(BigDecimal.ZERO) > 0);
        if (hasOutstanding) {
            throw new PlatformApiDataValidationException("error.msg.loc.cannot.close.unpaid.charges",
                    "Cannot close LOC with unpaid charges", "charges");
        }

        // Extract date and note from command
        LocalDate closureDate = command.localDateValueOfParameterNamed("actionDate");

        // Use current date if no date provided
        if (closureDate == null) {
            closureDate = LocalDate.now();
        }

        if (closureDate.isAfter(DateUtils.getLocalDateOfTenant())) {
            throw new PlatformApiDataValidationException("error.msg.loc.cannot.close.in.the.future", "LOC cannot be closed in the future",
                    "charges");
        }

        // Validate closure date is not before activation date
        if (loc.getLineOfCreditStateChange().getActivateOnDate() != null
                && closureDate.isBefore(loc.getLineOfCreditStateChange().getActivateOnDate())) {
            throw new PlatformApiDataValidationException("error.msg.loc.cannot.close.date.before.activation",
                    "LOC cannot be closed in the future", "charges");
        }

        loc.setStatus(LocStatus.CLOSED);
        loc.getLineOfCreditStateChange().setClosedOnDate(closureDate);
        loc.getLineOfCreditStateChange().setClosedBy(context.getAuthenticatedUserIfPresent());
        lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditClosedBusinessEvent(loc));

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.CLOSE);

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();

    }

    @Override
    @Transactional
    public CommandProcessingResult increaseCreditLimit(Long lineOfCreditId, JsonCommand command) {
        // Fetch LOC
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        // Validate state (allow only ACTIVE or APPROVED to be increased)
        if (loc.getStatus() != LocStatus.ACTIVE && loc.getStatus() != LocStatus.APPROVED) {
            throw new PlatformApiDataValidationException("error.msg.loc.increase.limit.invalid.state",
                    "Credit limit can only be increased when LOC is ACTIVE or APPROVED", "state");
        }

        dataValidator.validateForIncreaseOrDecreaseOfCreditLimit(command);
        // Extract new limit - support multiple possible parameter names for flexibility
        BigDecimal newLimit = command.bigDecimalValueOfParameterNamed(ADJUSTED_CREDIT_LIMIT);
        LocalDate transactionDate = command.localDateValueOfParameterNamed("actionDate");

        Optional<LineOfCreditTransaction> lastTransaction = lineOfCreditTransactionRepository
                .findLastTransactionBeforeDate(lineOfCreditId, transactionDate.plusDays(1), PageRequest.of(0, 1)).stream().findFirst();

        BigDecimal currentBalance = BigDecimal.ZERO;
        if (lastTransaction.isPresent()) {
            currentBalance = lastTransaction.get().getBalanceAfter();
        }

        // This is wrong, comparison should be with transaction of same date.
        if (newLimit.compareTo(currentBalance) <= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.increase.limit.must.be.greater",
                    "New credit limit must be greater than existing limit for a given date", "newCreditLimit");
        }

        // Ensure consumed amount (if any) does not exceed new limit (shouldn't happen for increase but defensive)
        BigDecimal consumed = (loc.getSummary() != null && loc.getSummary().getConsumedAmount() != null)
                ? loc.getSummary().getConsumedAmount()
                : BigDecimal.ZERO;

        if (consumed.compareTo(newLimit) > 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.increase.limit.consumed.exceeds.new.limit",
                    "Consumed amount exceeds the proposed new credit limit", "newCreditLimit");
        }

        BigDecimal delta = newLimit.subtract(currentBalance);
        // INCREMENT is not loan-related, so pass null for loanId
        lineOfCreditBalanceUpdateService.computeLocBalance(null, delta, loc, transactionDate, LineOfCreditTransactionType.INCREMENT);
        loc.setMaximumAmount(loc.getMaximumAmount().add(delta));
        this.lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditIncreasedBusinessEvent(loc));

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.INCREASE_CREDIT_LIMIT);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousLimit", currentBalance);
        changes.put("newLimit", newLimit);
        changes.put("delta", delta);
        if (loc.getSummary() != null) {
            changes.put("availableBalance", loc.getSummary().getAvailableBalance());
        }

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult reduceCreditLimit(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (loc.getStatus() != LocStatus.ACTIVE && loc.getStatus() != LocStatus.APPROVED) {
            throw new PlatformApiDataValidationException("error.msg.loc.reduce.limit.invalid.state",
                    "Credit limit can only be reduced when LOC is ACTIVE or APPROVED", "state");
        }

        // Validate payload (expects adjustedCreditLimit parameter)
        dataValidator.validateForIncreaseOrDecreaseOfCreditLimit(command);
        BigDecimal adjustmentAmount = command.bigDecimalValueOfParameterNamed(ADJUSTED_CREDIT_LIMIT);
        // Use localDate extractor (consistent with increaseCreditLimit)
        LocalDate transactionDate = command.localDateValueOfParameterNamed("actionDate");

        // Derive the balance (limit) at the given transaction date using LOC transaction history (supports backdated)
        Optional<LineOfCreditTransaction> lastTransaction = lineOfCreditTransactionRepository
                .findLastTransactionBeforeDate(lineOfCreditId, transactionDate.plusDays(1), PageRequest.of(0, 1)).stream().findFirst();

        BigDecimal currentBalanceAtDate; // Balance (limit) effective at the transaction date
        if (lastTransaction.isPresent()) {
            currentBalanceAtDate = lastTransaction.get().getBalanceAfter();
        } else {
            // Fallback to current maximum amount if no historical transactions exist
            currentBalanceAtDate = loc.getMaximumAmount() == null ? BigDecimal.ZERO : loc.getMaximumAmount();
        }

        // New limit must be strictly less than the effective limit at that date
        if (adjustmentAmount.compareTo(currentBalanceAtDate) >= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.reduce.limit.must.be.less",
                    "Adjustment amount must be less than existing limit for the given date", ADJUSTED_CREDIT_LIMIT);
        }

        if (adjustmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.reduce.limit.amount.must.be.positive",
                    "Adjustment amount limit must be greater than zero", ADJUSTED_CREDIT_LIMIT);
        }

        // Update LOC balance history and summary (recomputation handled inside for backdated entries)
        // DECREMENT is not loan-related, so pass null for loanId
        lineOfCreditBalanceUpdateService.computeLocBalance(null, adjustmentAmount, loc, transactionDate,
                LineOfCreditTransactionType.DECREMENT);

        // Adjust current maximum limit (current state); even for backdated, we store present effective limit.
        loc.setMaximumAmount(loc.getMaximumAmount().subtract(adjustmentAmount));

        // Validate against consumed amount (current state). Defensive: cannot set limit below already consumed.
        BigDecimal consumed = (loc.getSummary() != null && loc.getSummary().getConsumedAmount() != null)
                ? loc.getSummary().getConsumedAmount()
                : BigDecimal.ZERO;
        if (loc.getMaximumAmount().compareTo(consumed) < 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.reduce.limit.consumed.exceeds.new.limit",
                    "Consumed amount exceeds the proposed new credit limit", ADJUSTED_CREDIT_LIMIT);
        }

        this.lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditDecreasedBusinessEvent(loc));

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.REDUCE_CREDIT_LIMIT);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousLimit", currentBalanceAtDate);
        changes.put("newLimit", loc.getMaximumAmount());
        if (loc.getSummary() != null) {
            changes.put("availableBalance", loc.getSummary().getAvailableBalance());
            changes.put("consumedAmount", loc.getSummary().getConsumedAmount());
        }
        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult adjustCreditLimit(Long lineOfCreditId, JsonCommand command) {
        // Fetch LOC
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        // Validate state (allow only ACTIVE or APPROVED to have limit adjusted)
        if (loc.getStatus() != LocStatus.ACTIVE && loc.getStatus() != LocStatus.APPROVED) {
            throw new PlatformApiDataValidationException("error.msg.loc.adjust.limit.invalid.state",
                    "Credit limit can only be adjusted when LOC is ACTIVE or APPROVED", "state");
        }

        // Validate payload - expects 'amount' parameter which is the new target limit
        dataValidator.validateForAdjustCreditLimit(command);

        // Extract new target limit (the desired approved facility) - supports decimal amounts
        BigDecimal newTargetLimit = command.bigDecimalValueOfParameterNamed(ADJUSTED_CREDIT_LIMIT);
        LocalDate transactionDate = command.localDateValueOfParameterNamed("actionDate");

        // Get the current credit limit (maximumAmount) - this is what we compare against
        BigDecimal currentCreditLimit = loc.getMaximumAmount() == null ? BigDecimal.ZERO : loc.getMaximumAmount();

        // Compare new target limit with current credit limit to determine if it's an increase or decrease
        int comparison = newTargetLimit.compareTo(currentCreditLimit);

        if (comparison == 0) {
            // No change needed - new limit equals current limit
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("previousLimit", currentCreditLimit);
            changes.put("newLimit", newTargetLimit);
            changes.put("adjustmentType", "NO_CHANGE");
            changes.put("delta", BigDecimal.ZERO);
            if (loc.getSummary() != null) {
                changes.put("availableBalance", loc.getSummary().getAvailableBalance());
            }
            return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
        } else if (comparison > 0) {
            // New limit is greater than current - this is an INCREASE
            return performCreditLimitIncrease(loc, newTargetLimit, currentCreditLimit, transactionDate, command);
        } else {
            // New limit is less than current - this is a DECREASE
            return performCreditLimitDecrease(loc, newTargetLimit, currentCreditLimit, transactionDate, command);
        }
    }

    private CommandProcessingResult performCreditLimitIncrease(LineOfCredit loc, BigDecimal newLimit, BigDecimal currentCreditLimit,
            LocalDate transactionDate, JsonCommand command) {
        Long lineOfCreditId = loc.getId();

        // Ensure consumed amount (if any) does not exceed new limit (shouldn't happen for increase but defensive)
        BigDecimal consumed = (loc.getSummary() != null && loc.getSummary().getConsumedAmount() != null)
                ? loc.getSummary().getConsumedAmount()
                : BigDecimal.ZERO;

        if (consumed.compareTo(newLimit) > 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.increase.limit.consumed.exceeds.new.limit",
                    "Consumed amount exceeds the proposed new credit limit", "amount");
        }

        // Calculate delta: difference between new limit and current credit limit
        BigDecimal delta = newLimit.subtract(currentCreditLimit);
        
        // Update LOC balance - INCREMENT adds to available balance by the delta amount
        lineOfCreditBalanceUpdateService.computeLocBalance(null, delta, loc, transactionDate, LineOfCreditTransactionType.INCREMENT);
        
        // Update the maximum amount (credit limit) to the new target limit
        loc.setMaximumAmount(newLimit);
        this.lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditIncreasedBusinessEvent(loc));

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.INCREASE_CREDIT_LIMIT);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousLimit", currentCreditLimit);
        changes.put("newLimit", newLimit);
        changes.put("adjustmentType", "INCREASE");
        changes.put("delta", delta);
        if (loc.getSummary() != null) {
            changes.put("availableBalance", loc.getSummary().getAvailableBalance());
        }

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
    }

    private CommandProcessingResult performCreditLimitDecrease(LineOfCredit loc, BigDecimal newLimit, BigDecimal currentCreditLimit,
            LocalDate transactionDate, JsonCommand command) {
        Long lineOfCreditId = loc.getId();

        // Validate that new limit is positive
        if (newLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.adjust.limit.must.be.positive",
                    "New credit limit must be greater than zero", "amount");
        }

        // Validate against consumed amount - cannot set limit below already consumed
        BigDecimal consumed = (loc.getSummary() != null && loc.getSummary().getConsumedAmount() != null)
                ? loc.getSummary().getConsumedAmount()
                : BigDecimal.ZERO;
        if (newLimit.compareTo(consumed) < 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.adjust.limit.consumed.exceeds.new.limit",
                    "Consumed amount exceeds the proposed new credit limit", "amount");
        }

        // Calculate the delta (how much we're reducing by)
        BigDecimal delta = currentCreditLimit.subtract(newLimit);

        // Update LOC balance - DECREMENT reduces available balance by the delta amount
        lineOfCreditBalanceUpdateService.computeLocBalance(null, delta, loc, transactionDate,
                LineOfCreditTransactionType.DECREMENT);

        // Update the maximum amount (credit limit) to the new target limit
        loc.setMaximumAmount(newLimit);

        this.lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditDecreasedBusinessEvent(loc));

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.REDUCE_CREDIT_LIMIT);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousLimit", currentCreditLimit);
        changes.put("newLimit", newLimit);
        changes.put("adjustmentType", "DECREASE");
        changes.put("delta", delta.negate());
        if (loc.getSummary() != null) {
            changes.put("availableBalance", loc.getSummary().getAvailableBalance());
            changes.put("consumedAmount", loc.getSummary().getConsumedAmount());
        }
        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult undoCloseLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (loc.getStatus() != LocStatus.CLOSED) {
            throw new PlatformApiDataValidationException("error.msg.loc.undoclose.invalid.state",
                    "Can only undo close for LOC in CLOSED state", "state");
        }

        // Ensure it was actually marked closed (has closedOnDate)
        if (loc.getLineOfCreditStateChange() == null || loc.getLineOfCreditStateChange().getClosedOnDate() == null) {
            throw new PlatformApiDataValidationException("error.msg.loc.undoclose.no.closed.date",
                    "Cannot undo close when closed date not recorded", "closedOnDate");
        }

        LocStatus previousStatus = loc.getStatus();

        // Restore to ACTIVE per requirement "returning it to activated"
        loc.setStatus(LocStatus.ACTIVE);

        // If there was no activation date previously (possible if closed from SUBMITTED), assign now
        if (loc.getLineOfCreditStateChange().getActivateOnDate() == null) {
            LocalDate activationDate = command.localDateValueOfParameterNamed("actionDate");
            if (activationDate == null) {
                activationDate = DateUtils.getLocalDateOfTenant();
            }
            loc.getLineOfCreditStateChange().setActivateOnDate(activationDate);
            loc.getLineOfCreditStateChange().setActivatedBy(context.getAuthenticatedUserIfPresent());
        }

        // Clear closure metadata
        loc.getLineOfCreditStateChange().setClosedOnDate(null);
        loc.getLineOfCreditStateChange().setClosedBy(null);

        this.lineOfCreditRepository.saveAndFlush(loc);

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.UNDO_CLOSE);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousStatus", previousStatus.name());
        changes.put("newStatus", loc.getStatus().name());
        changes.put("activationDate", loc.getLineOfCreditStateChange().getActivateOnDate());

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deactivateLineOfCredit(Long lineOfCreditId, JsonCommand command) {

        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (!loc.isActive()) {
            throw new LineOfCreditInvalidStateException("Only ACTIVE LOC can be deactivated");
        }

        Integer totalActiveLoans = lineOfCreditReadPlatformService.getTotalOfActiveLoans(lineOfCreditId);

        if (totalActiveLoans > 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.deactivate.not.allowed", "Cannot deactivate LOC with active loans",
                    "state");
        }
        loc.deactivate();

        this.lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditDeactivatedBusinessEvent(loc));

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.DEACTIVATE);

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult reactivateLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (!loc.isDeactivated()) {
            throw new LineOfCreditInvalidStateException("Only DEACTIVATED LOC can be reactivated");
        }

        // Extract date from command
        LocalDate reactivationDate = command.localDateValueOfParameterNamed("actionDate");
        if (reactivationDate == null) {
            reactivationDate = DateUtils.getBusinessLocalDate();
        }

        // Validate reactivation date is not before deactivation date
        if (loc.getLineOfCreditStateChange().getDeactivatedOnDate() != null
                && reactivationDate.isBefore(loc.getLineOfCreditStateChange().getDeactivatedOnDate())) {
            throw new LineOfCreditInvalidStateException("Reactivation date cannot be before deactivation date");
        }

        loc.reactivate();
        loc.getLineOfCreditStateChange().setActivateOnDate(reactivationDate);
        loc.getLineOfCreditStateChange().setActivatedBy(context.authenticatedUser());

        this.lineOfCreditRepository.saveAndFlush(loc);
        businessEventNotifierService.notifyPostBusinessEvent(new LineOfCreditReactivatedBusinessEvent(loc));

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.RE_ACTIVATE);

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteLineOfCredit(Long lineOfCreditId) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (loc.getStatus() != LocStatus.SUBMITTED && loc.getStatus() != LocStatus.APPROVED) {
            throw new PlatformApiDataValidationException("error.msg.loc.delete.not.allowed", "Cannot delete an ACTIVE or APPROVED LOC",
                    "state");
        }

        // Note: For delete operation, we can't save a note since the entity will be deleted
        // If needed, we could save the note before deletion, but it would be orphaned

        this.lineOfCreditRepository.delete(loc);
        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();
    }

    private void applyInitialCharges(LineOfCredit loc) {

        List<LineOfCreditCharge> charges = locChargeRepository.findUnpaidOrdered(loc.getId());
        if (charges.isEmpty()) {
            log.info("No unpaid charges found for LOC {}", loc.getId());
            return;
        }

        if (loc.getSettlementSavingsAccount() == null) {
            throw new PlatformApiDataValidationException("error.msg.loc.settlement.savings.required",
                    "Settlement savings account required to deduct initial charges", "settlementSavingsAccountId");
        }

        MonetaryCurrency currency = loc.getSettlementSavingsAccount().getCurrency();

        if (!currency.getCode().equals(loc.getCurrency())) {
            throw new PlatformApiDataValidationException("error.msg.loc.currency.mismatch.settlement.savings",
                    "LOC currency must match settlement savings account currency", "currency");
        }

        // First compute amounts (and update percent-of-amount charges if needed) to know total
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;

        for (LineOfCreditCharge charge : charges) {
            if (!charge.isActive() || charge.isPaid() || charge.isWaived()) {
                continue;
            }

            BigDecimal outstanding = charge.getAmountOutstanding();
            if (outstanding != null && outstanding.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(outstanding);
                totalTaxes = totalTaxes.add(charge.getTaxAmountDefaulted());
                log.debug("Added charge {} outstanding amount {} to total", charge.getId(), outstanding);
            }

        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal totalWithdraw = total.add(totalTaxes);

        // Generate JSON command for savings account withdrawal
        JsonObject object = new JsonObject();
        object.addProperty("transactionAmount", totalWithdraw);
        object.addProperty("transactionDate", DateUtils.format(DateUtils.getBusinessLocalDate(), DateUtils.DEFAULT_DATE_FORMAT));
        object.addProperty("dateFormat", DateUtils.DEFAULT_DATE_FORMAT);
        object.addProperty("locale", "en");
        object.addProperty("paymentTypeId", 1); // Assuming 1 is a valid payment type ID
        object.addProperty("note", "LOC charges deduction for LOC ID: " + loc.getId());

        JsonCommand withdrawalCommand = JsonCommand.from(object.toString(), object, fromJsonHelper, null, null, null, null, null, null,
                loc.getSettlementSavingsAccount().getId(), null, null, null, null, null, null, null);

        // Execute savings withdrawal
        CommandProcessingResult withdrawalResult;
        try {
            withdrawalResult = savingsAccountWritePlatformService.withdrawal(loc.getSettlementSavingsAccount().getId(), withdrawalCommand);
        } catch (InsufficientAccountBalanceException ex) {
            throw new ActivationInsufficientBalanceException(total, ex);
        }

        // Get the created transaction for linking to charges
        SavingsAccountTransaction aggregateTxn = savingsAccountTransactionRepository.findById(withdrawalResult.getResourceId())
                .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.savings.transaction.not.found",
                        "Savings transaction not found", "transactionId"));

        for (LineOfCreditCharge charge : charges) {
            if (!charge.isActive() || charge.isPaid() || charge.isWaived()) {
                continue;
            }
            BigDecimal outstanding = charge.getAmountOutstanding();
            if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Pass skipTaxJournalEntries=true because the savings processor
            // (CustomCashBasedAccountingProcessorForSavings)
            // already handles the complete journal entries for LOC Activation including VAT
            locChargeDomainService.pay(charge, outstanding, false, aggregateTxn, true);
            locChargeRepository.save(charge);

            LineOfCreditChargePaidBy paidBy = LineOfCreditChargePaidBy.of(aggregateTxn, charge, outstanding);
            locChargePaidByRepository.save(paidBy);

        }

    }

    @Override
    @Transactional
    public CommandProcessingResult manageApprovedBuyers(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        // Use enhanced validation with credit limit checking
        this.dataValidator.validateForManageApprovedBuyersWithCreditLimit(command, lineOfCredit.getMaximumAmount());

        final Map<String, Object> changes = new LinkedHashMap<>();

        // Process approved buyers if provided
        if (command.hasParameter("approvedBuyers")) {
            JsonElement root = fromJsonHelper.parse(command.json());
            JsonArray approvedBuyersArray = fromJsonHelper.extractJsonArrayNamed("approvedBuyers", root);

            List<LineOfCreditApprovedBuyers> newApprovedBuyers = parseApprovedBuyersFromJson(approvedBuyersArray, lineOfCredit);

            // Handle updating/replacing buyers
            replaceApprovedBuyersWithValidation(lineOfCredit, newApprovedBuyers);
            changes.put("approvedBuyersCount", newApprovedBuyers.size());
        }

        if (!changes.isEmpty()) {
            this.lineOfCreditRepository.saveAndFlush(lineOfCredit);

            // Save note if provided
            saveNoteIfProvided(lineOfCredit, command, LineOfCreditNoteType.LOC_APPROVED_BUYERS_UPDATED);
        }

        // After processing, fetch and return all approved buyers with ID and name
        List<Map<String, Object>> approvedBuyersList = new ArrayList<>();
        if (lineOfCredit.getApprovedBuyers() != null) {
            for (LineOfCreditApprovedBuyers buyer : lineOfCredit.getApprovedBuyers()) {
                Map<String, Object> buyerMap = new HashMap<>();
                buyerMap.put("id", buyer.getId());
                buyerMap.put("name", buyer.getName());
                buyerMap.put("creditLimit", buyer.getCreditLimit());
                buyerMap.put("losExternalId", buyer.getLosExternalId());
                approvedBuyersList.add(buyerMap);
            }
        }
        changes.put("approvedBuyers", approvedBuyersList);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(lineOfCreditId).with(changes).build();
    }

    private List<LineOfCreditApprovedBuyers> parseApprovedBuyersFromJson(JsonArray approvedBuyersArray, LineOfCredit lineOfCredit) {
        List<LineOfCreditApprovedBuyers> newApprovedBuyers = new ArrayList<>();

        if (approvedBuyersArray != null) {
            approvedBuyersArray.forEach(buyerElement -> {
                if (buyerElement != null && !buyerElement.isJsonNull()) {
                    JsonObject buyerObj = buyerElement.getAsJsonObject();
                    String buyerName = buyerObj.get("name").getAsString();

                    BigDecimal creditLimit = BigDecimal.ZERO;
                    if (buyerObj.has("creditLimit")) {
                        creditLimit = buyerObj.get("creditLimit").getAsBigDecimal();
                    }

                    String losExternalId = null;
                    if (buyerObj.has("losExternalId") && !buyerObj.get("losExternalId").isJsonNull()) {
                        losExternalId = buyerObj.get("losExternalId").getAsString();
                    }

                    LineOfCreditApprovedBuyers buyer = new LineOfCreditApprovedBuyers(buyerName, creditLimit, losExternalId, lineOfCredit);
                    newApprovedBuyers.add(buyer);
                }
            });
        }

        return newApprovedBuyers;
    }

    private void replaceApprovedBuyersWithValidation(LineOfCredit lineOfCredit, List<LineOfCreditApprovedBuyers> newApprovedBuyers) {
        List<LineOfCreditApprovedBuyers> existingBuyers = lineOfCredit.getApprovedBuyers();

        if (existingBuyers == null || existingBuyers.isEmpty()) {
            // No existing buyers, safe to add new ones
            lineOfCredit.setApprovedBuyers(new ArrayList<>());
            for (LineOfCreditApprovedBuyers buyer : newApprovedBuyers) {
                buyer.setLineOfCredit(lineOfCredit);
                lineOfCredit.getApprovedBuyers().add(buyer);
            }
            return;
        }

        // Check which buyers are being deleted (exist in current list but not in new list)
        List<LineOfCreditApprovedBuyers> buyersToDelete = existingBuyers.stream().filter(
                existingBuyer -> newApprovedBuyers.stream().noneMatch(newBuyer -> newBuyer.getName().equals(existingBuyer.getName())))
                .toList();

        // Check if any buyers to be deleted are referenced by active loans
        for (LineOfCreditApprovedBuyers buyerToDelete : buyersToDelete) {
            if (buyerToDelete.getId() != null) {
                List<LoanStatus> activeStatuses = List.of(LoanStatus.ACTIVE, LoanStatus.APPROVED, LoanStatus.CLOSED_OBLIGATIONS_MET,
                        LoanStatus.CLOSED_WRITTEN_OFF);

                List<LineOfCreditLoanBuyerSupplierDetail> referencingLoans = loanBuyerSupplierDetailRepository
                        .findByApprovedBuyersIdsWithActiveLoans(List.of(buyerToDelete.getId()), activeStatuses);

                if (!referencingLoans.isEmpty()) {
                    // Get loan IDs for error message
                    String loanIds = referencingLoans.stream().map(detail -> detail.getLoan().getId().toString()).distinct()
                            .reduce((a, b) -> a + ", " + b).orElse("unknown");

                    throw new PlatformDataIntegrityException("error.msg.buyer.linked.to.active.loans",
                            String.format("Buyer '%s' is linked with active Line of Credit loans (ID: %s) and cannot be deleted",
                                    buyerToDelete.getName(), loanIds),
                            "buyer", buyerToDelete.getName(), "loanIds", loanIds);
                }
            }
        }

        // If we reach here, it's safe to update the buyers
        // Only remove buyers that are actually being deleted, preserve existing ones

        // Step 1: Remove only the buyers that are being deleted (already validated above)
        for (LineOfCreditApprovedBuyers buyerToDelete : buyersToDelete) {
            lineOfCredit.getApprovedBuyers().remove(buyerToDelete);
        }

        // Step 2: Update existing buyers or add new ones
        for (LineOfCreditApprovedBuyers newBuyer : newApprovedBuyers) {
            // Check if buyer with same name exists in original list
            LineOfCreditApprovedBuyers existingBuyerWithSameName = existingBuyers.stream()
                    .filter(existing -> existing.getName().equals(newBuyer.getName())).findFirst().orElse(null);

            if (existingBuyerWithSameName != null) {
                // Update existing buyer (keeping same ID to preserve foreign key references)
                existingBuyerWithSameName.setName(newBuyer.getName());
                existingBuyerWithSameName.setCreditLimit(newBuyer.getCreditLimit());
                existingBuyerWithSameName.setLosExternalId(newBuyer.getLosExternalId());
                // Note: existingBuyerWithSameName is already in the collection, no need to add again
            } else {
                // New buyer - add to collection
                newBuyer.setLineOfCredit(lineOfCredit);
                lineOfCredit.getApprovedBuyers().add(newBuyer);
            }
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult addVendor(Long lineOfCreditId, JsonCommand command) {
        // Parse JSON request body
        AddVendorRequest request;
        try {
            request = new ObjectMapper().readValue(command.json(), AddVendorRequest.class);
        } catch (Exception e) {
            throw new PlatformApiDataValidationException("error.msg.vendor.request.invalid", "Invalid request body: " + e.getMessage(),
                    List.of(), e);
        }

        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        // Validate request
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new PlatformDataIntegrityException("error.msg.vendor.name.required", "Vendor name is required");
        }

        // Check if vendor with same name already exists
        if (lineOfCredit.getApprovedBuyers() != null) {
            boolean vendorExists = lineOfCredit.getApprovedBuyers().stream()
                    .anyMatch(buyer -> buyer.getName().equals(request.getName().trim()));
            if (vendorExists) {
                throw new PlatformDataIntegrityException("error.msg.vendor.name.duplicate",
                        "A vendor with name '" + request.getName().trim() + "' already exists for this Line of Credit");
            }
        }

        // Validate credit limit
        BigDecimal creditLimit = request.getCreditLimit() != null ? request.getCreditLimit() : BigDecimal.ZERO;
        if (creditLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new PlatformDataIntegrityException("error.msg.vendor.credit.limit.invalid", "Credit limit cannot be negative");
        }

        // Create new vendor
        LineOfCreditApprovedBuyers vendor = new LineOfCreditApprovedBuyers(request.getName().trim(), creditLimit,
                request.getLosExternalId(), lineOfCredit);

        // Add to line of credit
        if (lineOfCredit.getApprovedBuyers() == null) {
            lineOfCredit.setApprovedBuyers(new ArrayList<>());
        }
        lineOfCredit.getApprovedBuyers().add(vendor);

        // Save and flush to get the generated ID
        LineOfCredit savedLineOfCredit = this.lineOfCreditRepository.saveAndFlush(lineOfCredit);

        // Get the vendor ID from the saved entity (it should now have an ID)
        Long vendorId = savedLineOfCredit.getApprovedBuyers().stream().filter(v -> v.getName().equals(vendor.getName())).findFirst()
                .map(LineOfCreditApprovedBuyers::getId).orElse(null);

        // Return command processing result with vendor ID, client ID, office ID, and resource ID
        return new CommandProcessingResultBuilder().withEntityId(vendorId).withClientId(lineOfCredit.getClient().getId())
                .withOfficeId(lineOfCredit.getClient().getOffice().getId())
                .withResourceIdAsString(vendorId != null ? String.valueOf(vendorId) : null).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateVendor(Long lineOfCreditId, Long vendorId, JsonCommand command) {
        // Parse JSON request body
        UpdateVendorRequest request;
        try {
            request = new ObjectMapper().readValue(command.json(), UpdateVendorRequest.class);
        } catch (Exception e) {
            throw new PlatformApiDataValidationException("error.msg.vendor.request.invalid", "Invalid request body: " + e.getMessage(),
                    List.of(), e);
        }

        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        // Find the vendor
        LineOfCreditApprovedBuyers vendor = lineOfCredit.getApprovedBuyers().stream().filter(v -> v.getId().equals(vendorId)).findFirst()
                .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.vendor.not.found",
                        "Vendor with id " + vendorId + " not found for this Line of Credit"));

        // Validate request
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new PlatformDataIntegrityException("error.msg.vendor.name.required", "Vendor name is required");
        }

        // Check if another vendor with same name already exists (excluding current vendor)
        boolean duplicateExists = lineOfCredit.getApprovedBuyers().stream()
                .anyMatch(v -> !v.getId().equals(vendorId) && v.getName().equals(request.getName().trim()));

        if (duplicateExists) {
            throw new PlatformDataIntegrityException("error.msg.vendor.name.duplicate",
                    "A vendor with name '" + request.getName().trim() + "' already exists for this Line of Credit");
        }

        // Update vendor name only
        vendor.setName(request.getName().trim());

        // Save changes
        this.lineOfCreditRepository.saveAndFlush(lineOfCredit);

        return new CommandProcessingResultBuilder().withEntityId(vendorId).withClientId(lineOfCredit.getClient().getId())
                .withOfficeId(lineOfCredit.getClient().getOffice().getId()).withResourceIdAsString(String.valueOf(vendorId)).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteVendor(Long lineOfCreditId, Long vendorId) {
        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        // Find the vendor
        LineOfCreditApprovedBuyers vendor = lineOfCredit.getApprovedBuyers().stream().filter(v -> v.getId().equals(vendorId)).findFirst()
                .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.vendor.not.found",
                        "Vendor with id " + vendorId + " not found for this Line of Credit"));

        // Check if vendor is associated with any active drawdowns (loans)
        // We prevent deletion if the vendor has loans that are ACTIVE, OVERPAID, or have closed but obligations are met
        Long activeDrawdownCount = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_loan_approver_buyers_suppliers lbsd " + "INNER JOIN m_loan l ON lbsd.loan_id = l.id "
                        + "WHERE lbsd.buyer_supplier_id = ? AND l.loan_status_id IN (?, ?, ?)",
                Long.class, vendorId, LoanStatus.ACTIVE.getValue(), LoanStatus.CLOSED_OBLIGATIONS_MET.getValue(),
                LoanStatus.OVERPAID.getValue());

        if (activeDrawdownCount != null && activeDrawdownCount > 0) {
            throw new PlatformDataIntegrityException("error.msg.vendor.has.active.drawdowns",
                    "Cannot delete vendor. It is associated with " + activeDrawdownCount + " active drawdown(s)");
        }

        // Remove vendor from collection
        lineOfCredit.getApprovedBuyers().remove(vendor);

        // Save changes
        this.lineOfCreditRepository.saveAndFlush(lineOfCredit);

        return new CommandProcessingResultBuilder().withEntityId(vendorId).withClientId(lineOfCredit.getClient().getId())
                .withOfficeId(lineOfCredit.getClient().getOffice().getId()).withResourceIdAsString(String.valueOf(vendorId)).build();
    }

    /**
     * Blocks (reserves) a specified amount from the LOC credit limit, reducing the available amount for borrowers.
     * <p>
     * Formula after blocking: Available Amount = Credit Limit − Blocked Amount − Consumed Amount
     *
     * @param lineOfCreditId
     *            the LOC identifier
     * @param command
     *            must contain {@code amount} (positive BigDecimal) and optionally {@code note}
     */
    @Override
    @Transactional
    public CommandProcessingResult blockAmount(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (loc.getStatus() != LocStatus.ACTIVE && loc.getStatus() != LocStatus.APPROVED) {
            throw new PlatformApiDataValidationException("error.msg.loc.block.amount.invalid.state",
                    "Blocked amount can only be set when LOC is ACTIVE or APPROVED", "state");
        }

        BigDecimal amountToBlock = command.bigDecimalValueOfParameterNamed(ADJUSTED_CREDIT_LIMIT);
        if (amountToBlock == null || amountToBlock.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.block.amount.must.be.positive",
                    "Amount to block must be greater than zero", ADJUSTED_CREDIT_LIMIT);
        }

        BigDecimal currentBlockedAmount = loc.getSummary().getBlockedAmount() != null ? loc.getSummary().getBlockedAmount()
                : BigDecimal.ZERO;
        BigDecimal newBlockedAmount = currentBlockedAmount.add(amountToBlock);

        // Ensure the new blocked amount does not exceed the drawable available balance
        // (i.e., we cannot block more than what is currently available for drawdown)
        BigDecimal currentAvailableBalance = loc.getSummary().getAvailableBalance();
        if (amountToBlock.compareTo(currentAvailableBalance) > 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.block.amount.exceeds.available",
                    "Amount to block (" + amountToBlock + ") exceeds current available balance (" + currentAvailableBalance + ")",
                    ADJUSTED_CREDIT_LIMIT);
        }

        // Update blocked amount and recalculate available balance
        loc.getSummary().setBlockedAmount(newBlockedAmount);
        BigDecimal newAvailableBalance = currentAvailableBalance.subtract(amountToBlock);
        loc.getSummary().setAvailableBalance(newAvailableBalance);

        this.lineOfCreditRepository.saveAndFlush(loc);

        saveNoteIfProvided(loc, command, LineOfCreditNoteType.BLOCK_AMOUNT);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousBlockedAmount", currentBlockedAmount);
        changes.put("newBlockedAmount", newBlockedAmount);
        changes.put("availableBalance", newAvailableBalance);

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
    }

    /**
     * Unblocks (releases) a specified amount from the LOC blocked reserve, restoring it to the drawable available
     * balance.
     * <p>
     * Formula after unblocking: Available Amount = Credit Limit − Blocked Amount − Consumed Amount
     *
     * @param lineOfCreditId
     *            the LOC identifier
     * @param command
     *            must contain {@code amount} (positive BigDecimal) and optionally {@code note}
     */
    @Override
    @Transactional
    public CommandProcessingResult unblockAmount(Long lineOfCreditId, JsonCommand command) {
        final LineOfCredit loc = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

        if (loc.getStatus() != LocStatus.ACTIVE && loc.getStatus() != LocStatus.APPROVED) {
            throw new PlatformApiDataValidationException("error.msg.loc.unblock.amount.invalid.state",
                    "Blocked amount can only be released when LOC is ACTIVE or APPROVED", "state");
        }

        BigDecimal amountToUnblock = command.bigDecimalValueOfParameterNamed(ADJUSTED_CREDIT_LIMIT);
        if (amountToUnblock == null || amountToUnblock.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.unblock.amount.must.be.positive",
                    "Amount to unblock must be greater than zero", ADJUSTED_CREDIT_LIMIT);
        }

        BigDecimal currentBlockedAmount = loc.getSummary().getBlockedAmount() != null ? loc.getSummary().getBlockedAmount()
                : BigDecimal.ZERO;

        if (amountToUnblock.compareTo(currentBlockedAmount) > 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.unblock.amount.exceeds.blocked",
                    "Amount to unblock (" + amountToUnblock + ") exceeds current blocked amount (" + currentBlockedAmount + ")",
                    ADJUSTED_CREDIT_LIMIT);
        }

        BigDecimal newBlockedAmount = currentBlockedAmount.subtract(amountToUnblock);

        // Update blocked amount and recalculate available balance
        loc.getSummary().setBlockedAmount(newBlockedAmount);
        BigDecimal currentAvailableBalance = loc.getSummary().getAvailableBalance();
        BigDecimal newAvailableBalance = currentAvailableBalance.add(amountToUnblock);
        loc.getSummary().setAvailableBalance(newAvailableBalance);

        this.lineOfCreditRepository.saveAndFlush(loc);

        saveNoteIfProvided(loc, command, LineOfCreditNoteType.UNBLOCK_AMOUNT);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousBlockedAmount", currentBlockedAmount);
        changes.put("newBlockedAmount", newBlockedAmount);
        changes.put("availableBalance", newAvailableBalance);

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
    }

}
