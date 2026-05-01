package com.crediblex.fineract.portfolio.loanaccount.configuration;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for LPI (Late Payment Interest) same-month assignment.
 *
 * Only loans disbursed on or after {@code enabled-from-date} use the CredX wrapper (LPI shown on the same month's
 * schedule row). Loans disbursed before this date keep the default behavior (LPI on next month's row). This avoids
 * disturbing existing loans after deployment: their schedule already has penalties on "next month" rows; switching them
 * to same-month mid-life would mix behaviors.
 *
 * <p>
 * Set this to your deployment date (or the first disbursement date that should get same-month LPI). Format: yyyy-MM-dd
 * (e.g. 2025-03-10).
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "custom.lpi-same-month")
public class LpiSameMonthProperties {

    /**
     * Loans disbursed on or after this date get LPI same-month. Loans disbursed before keep next-month behavior. If
     * null/empty, same-month is disabled for all loans (safe default). Format: yyyy-MM-dd
     */
    private String enabledFromDate;

    /**
     * True if the loan qualifies for LPI same-month (disbursement on or after the configured date). Used during the
     * penalty job run when updating the repayment schedule.
     */
    public boolean isEnabledForDisbursementDate(LocalDate disbursementDate) {
        if (disbursementDate == null || enabledFromDate == null || enabledFromDate.isBlank()) {
            if (log.isDebugEnabled()) {
                log.debug("LPI same-month: disabled (disbursementDate={}, enabledFromDate={})", disbursementDate, enabledFromDate);
            }
            return false;
        }
        try {
            LocalDate cutoff = LocalDate.parse(enabledFromDate);
            boolean enabled = !disbursementDate.isBefore(cutoff);
            if (log.isDebugEnabled()) {
                log.debug("LPI same-month: loan disbursementDate={}, cutoff={}, enabled={}", disbursementDate, cutoff, enabled);
            }
            return enabled;
        } catch (DateTimeParseException e) {
            log.warn("LPI same-month: invalid enabled-from-date '{}', treating as disabled", enabledFromDate);
            return false;
        }
    }
}
