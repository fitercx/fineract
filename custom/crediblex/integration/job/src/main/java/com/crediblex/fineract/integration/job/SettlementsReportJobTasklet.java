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
package com.crediblex.fineract.integration.job;

import com.crediblex.fineract.integration.odoo.settlements.SettlementsExcelGenerator;
import com.crediblex.fineract.integration.odoo.settlements.SettlementsReportReadService;
import com.crediblex.fineract.integration.odoo.settlements.SettlementsReportRow;
import com.crediblex.fineract.integration.odoo.settlements.SettlementsReportWatermarkService;
import com.crediblex.fineract.integration.odoo.settlements.SettlementsSlackNotifier;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Twice-daily settlements report: Excel of disbursement/refund events since the last successful run, posted to Slack.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementsReportJobTasklet implements Tasklet {

    private static final ZoneId UAE = ZoneId.of("Asia/Dubai");

    private final SettlementsReportReadService settlementsReportReadService;
    private final SettlementsExcelGenerator settlementsExcelGenerator;
    private final SettlementsReportWatermarkService watermarkService;
    private final SettlementsSlackNotifier settlementsSlackNotifier;

    @Override
    public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) {
        final ZonedDateTime runTimeUae = ZonedDateTime.now(UAE);
        final OffsetDateTime toInclusive = runTimeUae.toOffsetDateTime();

        OffsetDateTime fromExclusive = watermarkService.getLastSuccessfulRunAt();
        if (fromExclusive == null) {
            // First run: last 24 hours so we do not dump full history
            fromExclusive = toInclusive.minusHours(24);
            log.info("No settlements report watermark found; using last 24 hours from {}", fromExclusive);
        }

        log.info("Settlements Report Job window: ({}, {}]", fromExclusive, toInclusive);

        final List<SettlementsReportRow> rows = settlementsReportReadService.findSinceLastRun(fromExclusive, toInclusive);
        final byte[] excelBytes = rows.isEmpty() ? null : settlementsExcelGenerator.generate(rows);

        final boolean sent = settlementsSlackNotifier.sendReport(rows, excelBytes, runTimeUae);
        if (sent) {
            watermarkService.updateLastSuccessfulRunAt(toInclusive);
            log.info("Settlements report sent ({} rows); watermark updated to {}", rows.size(), toInclusive);
        } else {
            log.error("Settlements report Slack send failed; watermark NOT updated so the next run will retry this window");
        }

        return RepeatStatus.FINISHED;
    }
}
