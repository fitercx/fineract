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

import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidBy;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargePaidByRepository;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargeRepository;
import com.crediblex.fineract.portfolio.loc.charge.service.LineOfCreditChargeDomainService;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditApprovedBuyers;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import com.crediblex.fineract.portfolio.loc.exception.LineOfCreditInvalidStateException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
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

    @Override
    @Transactional
    public CommandProcessingResult createLineOfCredit(JsonCommand command, Long clientId) {

        LineOfCreditRequest lcr = this.dataValidator.validateForCreate(command.json());
        LineOfCredit lineOfCredit = this.lineOfCreditAssembler.assembleFrom(lcr, clientId);

        lineOfCreditRepository.findByExternalIdWithNotFoundDetection(lineOfCredit.getExternalId());

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

                    Boolean isActive = fromJsonHelper.extractBooleanNamed("active", charge);
                    LineOfCreditCharge instance = locChargeDomainService.create(lineOfCredit, chargeDefinition, overrideAmount, isActive);
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
        // Validate limit reduction if maximumAmount is being updated (do this first for business rule validation)
        if (command.hasParameter("maximumAmount")) {
            final BigDecimal newMaximumAmount = command.bigDecimalValueOfParameterNamed("maximumAmount");
            this.dataValidator.validateForLimitReduction(lineOfCreditId, newMaximumAmount);
        }

        this.dataValidator.validateForUpdate(command.json());

        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);

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
                    Long chargeId = fromJsonHelper.extractLongNamed("chargeId", chargeElem);
                    if (chargeId == null) {
                        throw new PlatformApiDataValidationException("error.msg.loc.charge.chargeId.required", "Charge chargeId required",
                                "charges[" + idx + "]");
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
                    BigDecimal overrideAmount = null;
                    if (fromJsonHelper.parameterExists("overrideAmount", chargeElem)
                            && fromJsonHelper.parameterHasValue("overrideAmount", chargeElem)) {
                        overrideAmount = new BigDecimal(fromJsonHelper.extractStringNamed("overrideAmount", chargeElem));
                    }

                    Boolean isActive = fromJsonHelper.extractBooleanNamed("active", chargeElem);
                    LineOfCreditCharge newCharge = locChargeDomainService.create(lineOfCredit, chargeDefinition, overrideAmount, isActive);
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

        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();

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

        this.lineOfCreditRepository.delete(loc);
        return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).build();
    }

    private void applyInitialCharges(LineOfCredit loc) {

        if (loc.getSettlementSavingsAccount() == null) {
            log.warn("No settlement savings account configured for LOC {}, skipping charge application", loc.getId());
            return;
        }

        List<LineOfCreditCharge> charges = locChargeRepository.findUnpaidOrdered(loc.getId());
        if (charges.isEmpty()) {
            log.info("No unpaid charges found for LOC {}", loc.getId());
            return;
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
            log.info("No outstanding amount to charge for LOC {}", loc.getId());
            return;
        }

        log.info("Total charge amount {} for LOC {}", total, loc.getId());

        try {
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
            CommandProcessingResult withdrawalResult = savingsAccountWritePlatformService
                    .withdrawal(loc.getSettlementSavingsAccount().getId(), withdrawalCommand);

            // Get the created transaction for linking to charges
            SavingsAccountTransaction aggregateTxn = savingsAccountTransactionRepository.findById(withdrawalResult.getResourceId())
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.savings.transaction.not.found",
                            "Savings transaction not found", "transactionId"));

            log.info("Created savings withdrawal transaction {} for amount {} on LOC {}", aggregateTxn.getId(), total, loc.getId());

            // Allocate to each charge
            int chargesProcessed = 0;
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

                log.debug("Processed payment for charge {} with amount {}", charge.getId(), outstanding);
                chargesProcessed++;
            }

            log.info("Successfully processed {} charges for LOC {}", chargesProcessed, loc.getId());

        } catch (Exception e) {
            throw new PlatformApiDataValidationException("error.msg.loc.charge.application.failed", "Failed to apply initial charges",
                    "charges", e);
        }
    }
}
