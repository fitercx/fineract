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
package com.crediblex.fineract.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in, self-contained "real fire" test for manual verification against a Beeceptor (or any HTTP) endpoint.
 *
 * <p>
 * It is <b>disabled by default</b> and only runs when the {@code WEBHOOK_TEST_URL} environment variable is set. It does
 * not boot Spring or touch the database; it reproduces the exact payload shape that {@link LoanStatusWebhookPublisher}
 * and {@link LineOfCreditStatusWebhookPublisher} build (same {@code X-Fineract-Entity} / {@code X-Fineract-Action}
 * headers the real {@code WebHookProcessor} sends) and POSTs one request per in-scope status transition, so you can
 * watch each webhook land in your Beeceptor inbox.
 *
 * <p>
 * Run it, for example, with:
 *
 * <pre>
 *   WEBHOOK_TEST_URL="https://your-inbox.free.beeceptor.com" \
 *     ./gradlew :custom:crediblex:infrastructure:commands:test \
 *       --tests '*LoanStatusWebhookBeeceptorFireTest'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "WEBHOOK_TEST_URL", matches = ".+")
class LoanStatusWebhookBeeceptorFireTest {

    private static final Gson GSON = new Gson();
    private static final long LOAN_ID = 999001L;
    private static final long LOC_ID = 888001L;

    private record Scenario(String label, String entity, Map<String, Object> changes) {
    }

    @Test
    void fireEveryStatusTransitionToBeeceptor() throws Exception {
        final String url = System.getenv("WEBHOOK_TEST_URL");
        final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        final List<Scenario> scenarios = scenarios();
        final List<String> results = new ArrayList<>();
        boolean allOk = true;

        for (final Scenario s : scenarios) {
            final Map<String, Object> payload = payload(s);
            final HttpResponse<String> response = client.send(HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("X-Fineract-Entity", s.entity())
                    .header("X-Fineract-Action", "STATUS_CHANGED").header("Fineract-Platform-TenantId", "default")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload))).build(), HttpResponse.BodyHandlers.ofString());

            final boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            allOk = allOk && ok;
            results.add(String.format("%-45s %s -> HTTP %d", s.label(), s.entity(), response.statusCode()));
        }

        System.out.println("=== Beeceptor webhook fire results (" + url + ") ===");
        results.forEach(System.out::println);

        assertTrue(allOk, "One or more webhook POSTs did not return 2xx. Results:\n" + String.join("\n", results));
    }

    /** All in-scope transitions: origination, disbursal, post-active closures, overlay-only, and LOC. */
    private List<Scenario> scenarios() {
        final List<Scenario> list = new ArrayList<>();
        // Origination
        list.add(loanCore("approve", "SUBMITTED_AND_PENDING_APPROVAL", 100, "APPROVED", 200));
        list.add(loanCore("reject", "SUBMITTED_AND_PENDING_APPROVAL", 100, "REJECTED", 500));
        list.add(loanCore("withdraw", "SUBMITTED_AND_PENDING_APPROVAL", 100, "WITHDRAWN_BY_CLIENT", 400));
        // Disbursal
        list.add(loanCore("disburse", "APPROVED", 200, "ACTIVE", 300));
        // Post-active closures (the LOS gap)
        list.add(loanCore("obligations-met", "ACTIVE", 300, "CLOSED_OBLIGATIONS_MET", 600));
        list.add(loanCore("overpaid", "ACTIVE", 300, "OVERPAID", 700));
        list.add(loanCore("written-off", "ACTIVE", 300, "CLOSED_WRITTEN_OFF", 601));
        list.add(loanCore("rescheduled", "ACTIVE", 300, "CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT", 602));
        // Overlay-only (delinquency; core unchanged)
        list.add(loanOverlay("delinquent (INVALID->PAST_DUE)", "INVALID", "PAST_DUE"));
        list.add(loanOverlay("past-maturity (PAST_DUE->PAST_MATURITY)", "PAST_DUE", "PAST_MATURITY"));
        // Line of credit
        list.add(locStatus("loc-active->closed", "ACTIVE", "CLOSED"));
        return list;
    }

    private Scenario loanCore(final String label, final String oldStatus, final int oldCode, final String newStatus, final int newCode) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        final Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("newStatus", null);
        custom.put("oldStatus", null);
        changes.put("customStatus", custom);
        changes.put("defaultStatus", newStatus);
        changes.put("defaultStatusCode", newCode);
        changes.put("oldDefaultStatus", oldStatus);
        changes.put("oldDefaultStatusCode", oldCode);
        return new Scenario("core:" + label, "LOAN", changes);
    }

    private Scenario loanOverlay(final String label, final String oldCustom, final String newCustom) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        final Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("newStatus", newCustom);
        custom.put("oldStatus", oldCustom);
        changes.put("customStatus", custom);
        changes.put("defaultStatus", "ACTIVE");
        changes.put("defaultStatusCode", 300);
        return new Scenario("overlay:" + label, "LOAN", changes);
    }

    private Scenario locStatus(final String label, final String oldCustom, final String newCustom) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        final Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("newStatus", newCustom);
        custom.put("oldStatus", oldCustom);
        changes.put("customStatus", custom);
        changes.put("defaultStatus", newCustom);
        changes.put("locId", LOC_ID);
        return new Scenario("loc:" + label, "LINE_OF_CREDIT", changes);
    }

    private Map<String, Object> payload(final Scenario s) {
        final boolean isLoc = "LINE_OF_CREDIT".equals(s.entity());
        final long resourceId = isLoc ? LOC_ID : LOAN_ID;

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("changes", s.changes());
        response.put("loanId", LOAN_ID);
        response.put("clientId", 11L);
        response.put("officeId", 1L);
        response.put("resourceId", resourceId);
        response.put("isDrawdown", isLoc);
        if (isLoc) {
            response.put("locId", LOC_ID);
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("response", response);
        payload.put("entityName", s.entity());
        payload.put("actionName", "STATUS_CHANGED");
        payload.put("resourceIdentifier", String.valueOf(resourceId));
        return payload;
    }
}
