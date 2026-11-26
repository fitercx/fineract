/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.jobs.accrualreports3export;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Remembers the business date of the last fully successful export, so loans that closed since then get exactly one
 * final file. A same-day re-run reuses that run's window instead of starting from today.
 */
@Service
@RequiredArgsConstructor
public class AccrualReportExportWatermarkService {

    private static final long WATERMARK_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    private record Watermark(LocalDate lastBusinessDate, LocalDate closedFromExclusive) {
    }

    /** Lower bound (exclusive) of the closure dates exported as final files on the run for businessDate. */
    public LocalDate closedFromExclusive(final LocalDate businessDate) {
        final Watermark watermark = read();
        if (watermark == null || watermark.lastBusinessDate() == null || watermark.lastBusinessDate().isAfter(businessDate)) {
            return businessDate.minusDays(1);
        }
        if (watermark.lastBusinessDate().isEqual(businessDate)) {
            return watermark.closedFromExclusive() != null ? watermark.closedFromExclusive() : businessDate.minusDays(1);
        }
        return watermark.lastBusinessDate();
    }

    public void markSuccess(final LocalDate businessDate, final LocalDate closedFromExclusive) {
        final Timestamp now = Timestamp.from(Instant.now());
        final int updated = jdbcTemplate.update(
                "UPDATE cx_accrual_report_export_watermark SET last_business_date = ?, closed_from_exclusive = ?, updated_at = ? WHERE id = ?",
                Date.valueOf(businessDate), Date.valueOf(closedFromExclusive), now, WATERMARK_ID);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO cx_accrual_report_export_watermark (id, last_business_date, closed_from_exclusive, updated_at) VALUES (?, ?, ?, ?)",
                    WATERMARK_ID, Date.valueOf(businessDate), Date.valueOf(closedFromExclusive), now);
        }
    }

    private Watermark read() {
        final List<Watermark> rows = jdbcTemplate.query(
                "SELECT last_business_date, closed_from_exclusive FROM cx_accrual_report_export_watermark WHERE id = ?",
                (rs, rowNum) -> new Watermark(toLocalDate(rs.getDate("last_business_date")),
                        toLocalDate(rs.getDate("closed_from_exclusive"))),
                WATERMARK_ID);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static LocalDate toLocalDate(final Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
