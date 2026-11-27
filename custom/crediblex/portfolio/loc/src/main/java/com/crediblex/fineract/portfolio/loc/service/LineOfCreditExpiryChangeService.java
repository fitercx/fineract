package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNote;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNoteRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditNoteType;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import com.crediblex.fineract.portfolio.loc.exception.LineOfCreditNotFoundException;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.exception.CommandNotAwaitingApprovalException;
import org.apache.fineract.commands.exception.CommandNotFoundException;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LineOfCreditExpiryChangeService {

    static final String ACTION = "CHANGEEXPIRYDATE";
    static final String ENTITY = "LINE_OF_CREDIT";
    static final int AWAITING_APPROVAL = 2;
    static final int MAX_COMMENT_LENGTH = 1000;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final LineOfCreditRepositoryWrapper lineOfCreditRepository;
    private final LineOfCreditNoteRepository lineOfCreditNoteRepository;
    private final CommandSourceRepository commandSourceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final FromJsonHelper fromJsonHelper;
    private final PlatformSecurityContext context;
    @Qualifier("portfolioCommandSourceWritePlatformServiceImpl")
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final NotificationWritePlatformService notificationWritePlatformService;
    private final LineOfCreditExpiryPendingLock expiryPendingLock;

    public String prepareCommandJson(final Long clientId, final Long lineOfCreditId, final String rawJson) {
        final LineOfCredit lineOfCredit = loadForClient(clientId, lineOfCreditId);
        assertEligible(lineOfCredit);
        assertNoOtherPending(lineOfCreditId, null);

        final JsonElement parsed = this.fromJsonHelper.parse(StringUtils.defaultIfBlank(rawJson, "{}"));
        if (parsed == null || !parsed.isJsonObject()) {
            throw validationError("validation.msg.loc.expiry.invalid", "Expiry change request is invalid.", "endDate");
        }
        final JsonObject request = parsed.getAsJsonObject();
        final String dateFormat = text(request, "dateFormat", "yyyy-MM-dd");
        final String locale = text(request, "locale", "en");
        final LocalDate newEndDate = parseDate(request, "endDate", dateFormat, locale);
        final LocalDate newReviewDate = request.has("interimReviewDate") ? parseDate(request, "interimReviewDate", dateFormat, locale)
                : lineOfCredit.getInterimReviewDate();
        final String comment = StringUtils.trimToNull(text(request, "comment", null));
        validate(lineOfCredit, newEndDate, comment);

        final String warning = LineOfCreditExpiryDateRules.nextReviewWarning(newReviewDate, newEndDate);
        final JsonObject enriched = new JsonObject();
        enriched.addProperty("dateFormat", "yyyy-MM-dd");
        enriched.addProperty("locale", "en");
        enriched.addProperty("endDate", newEndDate.format(ISO));
        addDate(enriched, "interimReviewDate", newReviewDate);
        addDate(enriched, "oldEndDate", lineOfCredit.getEndDate());
        addDate(enriched, "oldInterimReviewDate", lineOfCredit.getInterimReviewDate());
        enriched.addProperty("changeType", LineOfCreditExpiryDateRules.changeType(lineOfCredit.getEndDate(), newEndDate));
        enriched.addProperty("comment", comment);
        if (warning != null) {
            enriched.addProperty("nextReviewDateWarning", warning);
        }
        return enriched.toString();
    }

    @Transactional
    public CommandProcessingResult apply(final JsonCommand command) {
        final Long lineOfCreditId = command.entityId();
        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);
        if (command.getClientId() != null) {
            assertClient(lineOfCredit, command.getClientId());
        }
        assertEligible(lineOfCredit);
        assertNoOtherPending(lineOfCreditId, command.getCommandId());

        final LocalDate newEndDate = readIsoDate(command, "endDate");
        final LocalDate newReviewDate = readIsoDate(command, "interimReviewDate");
        final LocalDate oldEndDate = readIsoDate(command, "oldEndDate");
        final LocalDate oldReviewDate = readIsoDate(command, "oldInterimReviewDate");
        final String comment = StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull("comment"));
        validate(lineOfCredit, newEndDate, comment);
        if (oldEndDate != null && lineOfCredit.getEndDate() != null && !oldEndDate.equals(lineOfCredit.getEndDate())) {
            throw validationError("validation.msg.loc.expiry.stale",
                    "The facility End Date changed since this request was submitted. Withdraw it and submit a new one.", "endDate");
        }
        if (!sameDate(oldReviewDate, lineOfCredit.getInterimReviewDate())) {
            throw validationError("validation.msg.loc.expiry.review.stale",
                    "The Next Review Date changed since this request was submitted. Withdraw it and submit a new one.",
                    "interimReviewDate");
        }

        final LocalDate previousEndDate = lineOfCredit.getEndDate();
        final LocalDate previousReviewDate = lineOfCredit.getInterimReviewDate();
        final LocStatus status = lineOfCredit.getStatus();
        lineOfCredit.setEndDate(newEndDate);
        if (!sameDate(newReviewDate, previousReviewDate)) {
            lineOfCredit.setInterimReviewDate(newReviewDate);
        }
        lineOfCredit.setStatus(status);
        this.lineOfCreditRepository.saveAndFlush(lineOfCredit);
        this.lineOfCreditNoteRepository.save(new LineOfCreditNote(lineOfCredit, comment, LineOfCreditNoteType.EXPIRY_DATE_CHANGE));

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("endDate", newEndDate.format(ISO));
        changes.put("oldEndDate", previousEndDate == null ? null : previousEndDate.format(ISO));
        changes.put("interimReviewDate",
                lineOfCredit.getInterimReviewDate() == null ? null : lineOfCredit.getInterimReviewDate().format(ISO));
        changes.put("oldInterimReviewDate", previousReviewDate == null ? null : previousReviewDate.format(ISO));
        changes.put("changeType", LineOfCreditExpiryDateRules.changeType(previousEndDate, newEndDate));
        changes.put("comment", comment);
        changes.put("outcome", "APPROVED");
        final String message = status == LocStatus.INACTIVE ? LineOfCreditExpiryDateRules.INACTIVE_MESSAGE : null;
        if (message != null) {
            changes.put("message", message);
        }

        final Long makerId = makerId(command);
        final String notification = "Expiry date change approved: " + previousEndDate + " → " + newEndDate + "."
                + (message == null ? "" : " " + message);
        notifyUser(makerId, lineOfCredit.getId(), notification);
        if (command.getCommandId() != null) {
            this.expiryPendingLock.release(lineOfCredit.getId());
        }

        return new CommandProcessingResultBuilder().withCommandId(command.getCommandId()).withEntityId(lineOfCredit.getId())
                .withClientId(lineOfCredit.getClient() == null ? null : lineOfCredit.getClient().getId()).with(changes).build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pending(final Long clientId, final Long lineOfCreditId) {
        loadForClient(clientId, lineOfCreditId);
        final List<Map<String, Object>> rows = this.jdbcTemplate.query("""
                SELECT c.id, c.command_as_json, c.made_on_date_utc, u.id AS maker_id, u.username, u.firstname, u.lastname
                FROM m_portfolio_command_source c
                JOIN m_appuser u ON u.id = c.maker_id
                WHERE c.action_name = ? AND c.entity_name = ? AND c.resource_id = ? AND c.status = ?
                ORDER BY c.id DESC
                """, (rs, rowNum) -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("commandId", rs.getLong("id"));
            row.put("commandAsJson", rs.getString("command_as_json"));
            row.put("madeOn", rs.getObject("made_on_date_utc", OffsetDateTime.class));
            row.put("makerUserId", rs.getLong("maker_id"));
            final String name = StringUtils.trimToNull(
                    (StringUtils.defaultString(rs.getString("firstname")) + " " + StringUtils.defaultString(rs.getString("lastname")))
                            .trim());
            row.put("maker", name == null ? rs.getString("username") : name);
            return row;
        }, ACTION, ENTITY, lineOfCreditId, AWAITING_APPROVAL);
        if (rows.isEmpty()) {
            return Map.of();
        }
        final Map<String, Object> row = rows.get(0);
        final JsonObject payload = JsonParser.parseString(String.valueOf(row.get("commandAsJson"))).getAsJsonObject();
        final Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("commandId", row.get("commandId"));
        pending.put("oldEndDate", text(payload, "oldEndDate", null));
        pending.put("newEndDate", text(payload, "endDate", null));
        pending.put("oldNextReviewDate", text(payload, "oldInterimReviewDate", null));
        pending.put("newNextReviewDate", text(payload, "interimReviewDate", null));
        pending.put("changeType", text(payload, "changeType", null));
        pending.put("comment", text(payload, "comment", null));
        pending.put("nextReviewDateWarning", text(payload, "nextReviewDateWarning", null));
        pending.put("maker", row.get("maker"));
        pending.put("makerUserId", row.get("makerUserId"));
        final OffsetDateTime madeOn = (OffsetDateTime) row.get("madeOn");
        pending.put("requestedOn",
                madeOn == null ? null : madeOn.atZoneSameInstant(DateUtils.getDateTimeZoneOfTenant()).toLocalDate().format(ISO));
        return pending;
    }

    @Transactional
    public CommandProcessingResult reject(final Long clientId, final Long lineOfCreditId, final Long commandId,
            final String rejectionComment) {
        final String comment = StringUtils.trimToNull(rejectionComment);
        if (comment == null) {
            throw validationError("validation.msg.loc.expiry.rejection.comment.required", "A rejection requires a comment.",
                    "rejectionComment");
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw validationError("validation.msg.loc.expiry.comment.too.long", "Comment cannot be longer than 1000 characters.",
                    "rejectionComment");
        }
        final LineOfCredit lineOfCredit = loadForClient(clientId, lineOfCreditId);
        final CommandSource commandSource = awaitingCommand(lineOfCreditId, commandId);
        final JsonObject payload = JsonParser.parseString(StringUtils.defaultIfBlank(commandSource.getCommandAsJson(), "{}"))
                .getAsJsonObject();
        payload.addProperty("rejectionComment", comment);
        payload.addProperty("outcome", "REJECTED");
        commandSource.setCommandAsJson(payload.toString());
        this.commandSourceRepository.saveAndFlush(commandSource);

        final Long resultId = this.commandsSourceWritePlatformService.rejectEntry(commandId);
        this.lineOfCreditNoteRepository
                .save(new LineOfCreditNote(lineOfCredit, "Rejected: " + comment, LineOfCreditNoteType.EXPIRY_DATE_REJECTED));
        final Long makerId = commandSource.getMaker() == null ? null : commandSource.getMaker().getId();
        notifyUser(makerId, lineOfCreditId, "Expiry date change rejected. " + comment);
        this.expiryPendingLock.release(lineOfCreditId);
        return new CommandProcessingResultBuilder().withCommandId(resultId).withEntityId(lineOfCreditId).withClientId(clientId).build();
    }

    @Transactional
    public CommandProcessingResult withdraw(final Long clientId, final Long lineOfCreditId, final Long commandId) {
        loadForClient(clientId, lineOfCreditId);
        final CommandSource commandSource = awaitingCommand(lineOfCreditId, commandId);
        final AppUser currentUser = this.context.authenticatedUser();
        if (commandSource.getMaker() == null || !currentUser.getId().equals(commandSource.getMaker().getId())) {
            throw validationError("validation.msg.loc.expiry.withdraw.maker", "Only the maker can withdraw this expiry change.",
                    "commandId");
        }
        this.commandSourceRepository.delete(commandSource);
        this.expiryPendingLock.release(lineOfCreditId);
        return new CommandProcessingResultBuilder().withCommandId(commandId).withEntityId(lineOfCreditId).withClientId(clientId).build();
    }

    private CommandSource awaitingCommand(final Long lineOfCreditId, final Long commandId) {
        final CommandSource commandSource = this.commandSourceRepository.findById(commandId)
                .orElseThrow(() -> new CommandNotFoundException(commandId));
        if (!commandSource.isMarkedAsAwaitingApproval() || !ACTION.equals(commandSource.getActionName())
                || !ENTITY.equals(commandSource.getEntityName()) || !lineOfCreditId.equals(commandSource.getResourceId())) {
            throw new CommandNotAwaitingApprovalException(commandId);
        }
        return commandSource;
    }

    private void validate(final LineOfCredit lineOfCredit, final LocalDate newEndDate, final String comment) {
        final String dateError = LineOfCreditExpiryDateRules.newEndDateError(DateUtils.getLocalDateOfTenant(), lineOfCredit.getStartDate(),
                lineOfCredit.getEndDate(), newEndDate);
        if (dateError != null) {
            throw validationError("validation.msg.loc.expiry.end.date", dateError, "endDate");
        }
        if (comment == null) {
            throw validationError("validation.msg.loc.expiry.comment.required", "Comment is required.", "comment");
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw validationError("validation.msg.loc.expiry.comment.too.long", "Comment cannot be longer than 1000 characters.",
                    "comment");
        }
    }

    private void assertEligible(final LineOfCredit lineOfCredit) {
        final boolean activeOrInactive = lineOfCredit.getStatus() == LocStatus.ACTIVE || lineOfCredit.getStatus() == LocStatus.INACTIVE;
        if (!activeOrInactive && !lineOfCredit.isCustomExpired()) {
            throw validationError("validation.msg.loc.expiry.status", "Expiry date can be changed only on an active or inactive facility.",
                    "status");
        }
    }

    private void assertNoOtherPending(final Long lineOfCreditId, final Long currentCommandId) {
        final Integer count;
        if (currentCommandId == null) {
            count = this.jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM m_portfolio_command_source
                    WHERE action_name = ? AND entity_name = ? AND resource_id = ? AND status = ?
                    """, Integer.class, ACTION, ENTITY, lineOfCreditId, AWAITING_APPROVAL);
        } else {
            count = this.jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM m_portfolio_command_source
                    WHERE action_name = ? AND entity_name = ? AND resource_id = ? AND status = ? AND id <> ?
                    """, Integer.class, ACTION, ENTITY, lineOfCreditId, AWAITING_APPROVAL, currentCommandId);
        }
        if (count != null && count > 0) {
            throw validationError("validation.msg.loc.expiry.pending", LineOfCreditExpiryDateRules.PENDING_MESSAGE, "endDate");
        }
    }

    private LineOfCredit loadForClient(final Long clientId, final Long lineOfCreditId) {
        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);
        assertClient(lineOfCredit, clientId);
        return lineOfCredit;
    }

    private void assertClient(final LineOfCredit lineOfCredit, final Long clientId) {
        if (lineOfCredit.getClient() == null || !clientId.equals(lineOfCredit.getClient().getId())) {
            throw new LineOfCreditNotFoundException(lineOfCredit.getId());
        }
    }

    private Long makerId(final JsonCommand command) {
        if (command.getCommandId() != null) {
            final CommandSource commandSource = this.commandSourceRepository.findById(command.getCommandId()).orElse(null);
            if (commandSource != null && commandSource.getMaker() != null) {
                return commandSource.getMaker().getId();
            }
        }
        return this.context.authenticatedUser().getId();
    }

    private void notifyUser(final Long userId, final Long lineOfCreditId, final String content) {
        if (userId == null) {
            return;
        }
        final Long actorId = this.context.authenticatedUser().getId();
        this.notificationWritePlatformService.notify(List.of(userId), "lineOfCredit", lineOfCreditId, ACTION, actorId, content, false);
    }

    private static void addDate(final JsonObject target, final String name, final LocalDate date) {
        if (date == null) {
            target.add(name, JsonNull.INSTANCE);
        } else {
            target.addProperty(name, date.format(ISO));
        }
    }

    private static LocalDate parseDate(final JsonObject request, final String name, final String dateFormat, final String locale) {
        if (!request.has(name) || request.get(name).isJsonNull()) {
            return null;
        }
        final String value = StringUtils.trimToNull(request.get(name).getAsString());
        if (value == null) {
            return null;
        }
        return DateUtils.parseLocalDate(value, dateFormat, Locale.forLanguageTag(locale.replace('_', '-')));
    }

    private static LocalDate readIsoDate(final JsonCommand command, final String name) {
        if (!command.hasParameterValue(name)) {
            return null;
        }
        final String value = StringUtils.trimToNull(command.stringValueOfParameterNamed(name));
        if (value == null) {
            return null;
        }
        return LocalDate.parse(value, ISO);
    }

    private static boolean sameDate(final LocalDate left, final LocalDate right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equals(right);
    }

    private static String text(final JsonObject object, final String name, final String fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        return object.get(name).getAsString();
    }

    private static PlatformApiDataValidationException validationError(final String code, final String message, final String parameter) {
        return new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(code, message, parameter)));
    }
}
