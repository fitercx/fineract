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
package org.apache.fineract.infrastructure.jobs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;

class LastDayOfMonthCronTriggerTest {

    private static final String LAST_DAY_OF_MONTH_CRON = "0 0 0 L * ?";
    private static final ZoneId TENANT_ZONE = ZoneId.of("Asia/Kolkata");

    @Test
    void firesOnLastDayOfEveryMonthIncludingShortMonths() throws ParseException {
        List<LocalDateTime> fireTimes = fireTimesAfter(LocalDate.of(2025, 12, 31).atTime(1, 0), 12);

        assertThat(fireTimes).containsExactly(LocalDate.of(2026, 1, 31).atStartOfDay(), LocalDate.of(2026, 2, 28).atStartOfDay(),
                LocalDate.of(2026, 3, 31).atStartOfDay(), LocalDate.of(2026, 4, 30).atStartOfDay(),
                LocalDate.of(2026, 5, 31).atStartOfDay(), LocalDate.of(2026, 6, 30).atStartOfDay(),
                LocalDate.of(2026, 7, 31).atStartOfDay(), LocalDate.of(2026, 8, 31).atStartOfDay(),
                LocalDate.of(2026, 9, 30).atStartOfDay(), LocalDate.of(2026, 10, 31).atStartOfDay(),
                LocalDate.of(2026, 11, 30).atStartOfDay(), LocalDate.of(2026, 12, 31).atStartOfDay());
    }

    @Test
    void firesOnLeapDayInLeapYearFebruary() throws ParseException {
        List<LocalDateTime> fireTimes = fireTimesAfter(LocalDate.of(2028, 1, 31).atTime(1, 0), 1);

        assertThat(fireTimes).containsExactly(LocalDate.of(2028, 2, 29).atStartOfDay());
    }

    private static List<LocalDateTime> fireTimesAfter(LocalDateTime start, int count) throws ParseException {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setName("lastDayOfMonthTrigger");
        factory.setCronExpression(LAST_DAY_OF_MONTH_CRON);
        factory.setTimeZone(TimeZone.getTimeZone(TENANT_ZONE));
        factory.setStartTime(Date.from(start.atZone(TENANT_ZONE).toInstant()));
        factory.afterPropertiesSet();
        CronTrigger trigger = factory.getObject();

        List<LocalDateTime> fireTimes = new ArrayList<>();
        Date previous = Date.from(start.atZone(TENANT_ZONE).toInstant());
        for (int i = 0; i < count; i++) {
            Date next = trigger.getFireTimeAfter(previous);
            assertThat(next).isNotNull();
            fireTimes.add(ZonedDateTime.ofInstant(next.toInstant(), TENANT_ZONE).toLocalDateTime());
            previous = next;
        }
        return fireTimes;
    }
}
