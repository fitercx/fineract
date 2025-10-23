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
import com.crediblex.fineract.portfolio.loc.charge.service.LineOfCreditChargeDomainService;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditApprovedBuyers;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNote;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNoteRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNoteType;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import com.crediblex.fineract.portfolio.loc.exception.LineOfCreditInvalidStateException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.Pageable;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.data.domain.PageRequest;
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
    private final SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    private final ChargeRepositoryWrapper chargeRepository;
    private final FromJsonHelper fromJsonHelper;
    private final LineOfCreditAssembler lineOfCreditAssembler;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final PlatformSecurityContext context;
    private final LineOfCreditNoteRepository lineOfCreditNoteRepository;
    private final LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService;
    private final LineOfCreditTransactionRepository lineOfCreditTransactionRepository;

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
                    Long chargeId = null;
                    if (fromJsonHelper.parameterExists("chargeId", charge)) {
                        chargeId = fromJsonHelper.extractLongNamed("chargeId", charge);
                    } else if (fromJsonHelper.parameterExists("id", charge)) {
                        chargeId = fromJsonHelper.extractLongNamed("id", charge);
                    }

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
                    if (fromJsonHelper.parameterExists("overrideAmount", charge)
                            && fromJsonHelper.parameterHasValue("overrideAmount", charge)) {
                        overrideAmount = fromJsonHelper.extractBigDecimalNamed("overrideAmount", charge, Locale.ENGLISH);
                    } else if (fromJsonHelper.parameterExists("editableAmount", charge)
                            && fromJsonHelper.parameterHasValue("editableAmount", charge)) {
                        overrideAmount = fromJsonHelper.extractBigDecimalNamed("editableAmount", charge, Locale.ENGLISH);
                    } else if (fromJsonHelper.parameterExists("amount", charge) && fromJsonHelper.parameterHasValue("amount", charge)) {
                        overrideAmount = fromJsonHelper.extractBigDecimalNamed("amount", charge, Locale.ENGLISH);
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
            JsonElement root = fromJsonHelper.parse(command.json());
            JsonArray chargesArray = fromJsonHelper.extractJsonArrayNamed("charges", root);
            List<LineOfCreditCharge> newCharges = new ArrayList<>();
            if (chargesArray != null) {
                int idx = 0;
                for (JsonElement chargeElem : chargesArray) {
                    if (chargeElem == null || chargeElem.isJsonNull()) {
                        idx++;
                        continue;
                    }
                    Long chargeId = chargeElem.getAsJsonObject().get("id").getAsLong();

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
                    if (fromJsonHelper.parameterExists("overrideAmount", chargeElem)
                            && fromJsonHelper.parameterHasValue("overrideAmount", chargeElem)) {
                        overrideAmount = new BigDecimal(fromJsonHelper.extractStringNamed("overrideAmount", chargeElem));
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
            JsonElement root = fromJsonHelper.parse(command.json());
            JsonArray approvedBuyersArray = fromJsonHelper.extractJsonArrayNamed("approvedBuyers", root);
            List<LineOfCreditApprovedBuyers> newApprovedBuyers = new ArrayList<>();
            if (approvedBuyersArray != null) {
                approvedBuyersArray.forEach(buyerElement -> {
                    if (buyerElement != null && !buyerElement.isJsonNull()) {
                        String buyerName = buyerElement.getAsJsonObject().get("name").getAsString();
                        newApprovedBuyers.add(new LineOfCreditApprovedBuyers(buyerName, null));
                    }
                });
            }
            // Use the helper method to properly establish bidirectional relationship
            lineOfCredit.replaceApprovedBuyers(newApprovedBuyers);
            changes.put("approvedBuyersCount", newApprovedBuyers.size());
        }

        if (!changes.isEmpty()) {
            this.lineOfCreditRepository.saveAndFlush(lineOfCredit);
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

       Optional<LineOfCreditTransaction> lastTransaction = lineOfCreditTransactionRepository.findLastTransactionBeforeDate(lineOfCreditId,transactionDate.plusDays(1),
               PageRequest.of(0, 1)).stream().findFirst();

       BigDecimal currentBalance = BigDecimal.ZERO;
       if(lastTransaction.isPresent()){
           currentBalance = lastTransaction.get().getBalanceAfter();
       }

        //This is wrong, comparison should be with transaction of same date.
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
        lineOfCreditBalanceUpdateService.computeLocBalance(lineOfCreditId, delta, loc, transactionDate,
                LineOfCreditTransactionType.INCREMENT);
        loc.setMaximumAmount(loc.getMaximumAmount().add(delta));
        this.lineOfCreditRepository.saveAndFlush(loc);

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
        BigDecimal newLimit = command.bigDecimalValueOfParameterNamed(ADJUSTED_CREDIT_LIMIT);
        LocalDate transactionDate = command.dateValueOfParameterNamed("actionDate");

        BigDecimal currentLimit = loc.getMaximumAmount();
        if (currentLimit == null) {
            currentLimit = BigDecimal.ZERO;
        }

        if (newLimit.compareTo(currentLimit) >= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.reduce.limit.must.be.less",
                    "New credit limit must be less than existing limit", ADJUSTED_CREDIT_LIMIT);
        }

        if (newLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.reduce.limit.amount.must.be.positive",
                    "New credit limit must be greater than zero", ADJUSTED_CREDIT_LIMIT);
        }

        BigDecimal consumed = (loc.getSummary() != null && loc.getSummary().getConsumedAmount() != null)
                ? loc.getSummary().getConsumedAmount()
                : BigDecimal.ZERO;
        if (newLimit.compareTo(consumed) < 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.reduce.limit.consumed.exceeds.new.limit",
                    "Consumed amount exceeds the proposed new credit limit", ADJUSTED_CREDIT_LIMIT);
        }

        BigDecimal delta = currentLimit.subtract(newLimit); // negative

        lineOfCreditBalanceUpdateService.computeLocBalance(lineOfCreditId, delta, loc, transactionDate,
                LineOfCreditTransactionType.DECREMENT);
        loc.setMaximumAmount(loc.getMaximumAmount().subtract(delta));
        this.lineOfCreditRepository.saveAndFlush(loc);

        // Save note if provided
        saveNoteIfProvided(loc, command, LineOfCreditNoteType.REDUCE_CREDIT_LIMIT);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousLimit", currentLimit);
        changes.put("newLimit", newLimit);
        changes.put("delta", delta);
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
        List<LineOfCreditCharge> chargesToUpdate = new ArrayList<>();

        for (LineOfCreditCharge charge : charges) {
            if (!charge.isActive() || charge.isPaid() || charge.isWaived()) {
                continue;
            }

            // Handle percentage-based charges
            if (charge.getChargeCalculation() != null
                    && Objects.equals(charge.getChargeCalculation(), ChargeCalculationType.PERCENT_OF_AMOUNT.getValue())
                    && (charge.getAmount() == null || charge.getAmount().compareTo(BigDecimal.ZERO) == 0)) {
                BigDecimal base = loc.getMaximumAmount();

                log.debug("Applying percentage base {} to charge {}", base, charge.getId());
                locChargeDomainService.applyPercentBase(charge, base);
                chargesToUpdate.add(charge); // Collect for batch save
            }

            BigDecimal outstanding = charge.getAmountOutstanding();
            if (outstanding != null && outstanding.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(outstanding);
                log.debug("Added charge {} outstanding amount {} to total", charge.getId(), outstanding);
            }
        }

        // Batch save percentage-updated charges to avoid duplicate saves
        if (!chargesToUpdate.isEmpty()) {
            locChargeRepository.saveAll(chargesToUpdate);
            log.debug("Batch saved {} percentage-updated charges", chargesToUpdate.size());
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Generate JSON command for savings account withdrawal
        JsonObject object = new JsonObject();
        object.addProperty("transactionAmount", total);
        object.addProperty("transactionDate", DateUtils.format(DateUtils.getBusinessLocalDate(), DateUtils.DEFAULT_DATE_FORMAT));
        object.addProperty("dateFormat", DateUtils.DEFAULT_DATE_FORMAT);
        object.addProperty("locale", "en");
        object.addProperty("paymentTypeId", 1); // Assuming 1 is a valid payment type ID
        object.addProperty("note", "LOC charges deduction for LOC ID: " + loc.getId());

        JsonCommand withdrawalCommand = JsonCommand.from(object.toString(), object, fromJsonHelper, null, null, null, null, null, null,
                loc.getSettlementSavingsAccount().getId(), null, null, null, null, null, null, null);

        // Execute savings withdrawal
        CommandProcessingResult withdrawalResult = savingsAccountWritePlatformService.withdrawal(loc.getSettlementSavingsAccount().getId(),
                withdrawalCommand);

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

            locChargeDomainService.pay(charge, outstanding, false);
            locChargeRepository.save(charge);

            LineOfCreditChargePaidBy paidBy = LineOfCreditChargePaidBy.of(aggregateTxn, charge, outstanding);
            locChargePaidByRepository.save(paidBy);

        }

    }
}
