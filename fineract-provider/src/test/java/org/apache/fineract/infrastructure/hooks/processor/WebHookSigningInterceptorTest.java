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
package org.apache.fineract.infrastructure.hooks.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

class WebHookSigningInterceptorTest {

    private static final String SECRET = "test-signing-secret";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
    private static final String BODY = "{\"entityName\":\"LOAN\",\"actionName\":\"STATUS_CHANGED\"}";
    // printf '1700000000.<BODY>' | openssl dgst -sha256 -hmac test-signing-secret — same vector as loan-service's test.
    private static final String EXPECTED_SIGNATURE = "v1=cda419db45ba4659306e6c8da0011ae106951aa7f1d7b869c80753f1adedb05f";

    @Test
    void signsJsonBodyWithTimestampAndForwardsTheSignedBytes() throws IOException {
        final Request sent = send(RequestBody.create(BODY, MediaType.get("application/json; charset=UTF-8")));

        assertEquals("1700000000", sent.header(WebHookSigningInterceptor.TIMESTAMP_HEADER));
        assertEquals(EXPECTED_SIGNATURE, sent.header(WebHookSigningInterceptor.SIGNATURE_HEADER));
        assertEquals(BODY, bodyOf(sent));
        assertEquals("application/json; charset=UTF-8", sent.body().contentType().toString());
    }

    @Test
    void signsFormBodyOverItsEncodedBytes() throws IOException {
        final Request sent = send(new FormBody.Builder().add("entityName", "LOAN").add("actionName", "STATUS_CHANGED").build());

        final String encoded = bodyOf(sent);
        assertEquals("entityName=LOAN&actionName=STATUS_CHANGED", encoded);
        final String expected = "v1="
                + new WebHookSigningInterceptor(SECRET, CLOCK).sign("1700000000", encoded.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, sent.header(WebHookSigningInterceptor.SIGNATURE_HEADER));
    }

    @Test
    void rejectsBlankSecret() {
        assertThrows(IllegalArgumentException.class, () -> new WebHookSigningInterceptor(" ", CLOCK));
        assertThrows(IllegalArgumentException.class, () -> new WebHookSigningInterceptor(null, CLOCK));
    }

    private static Request send(final RequestBody body) throws IOException {
        final AtomicReference<Request> captured = new AtomicReference<>();
        final OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new WebHookSigningInterceptor(SECRET, CLOCK))
                .addInterceptor(chain -> {
                    captured.set(chain.request());
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body(ResponseBody.create("", null)).build();
                }).build();
        try (Response ignored = client.newCall(new Request.Builder().url("http://loan-service.local/v1/lms/webhook").post(body).build())
                .execute()) {
            return captured.get();
        }
    }

    private static String bodyOf(final Request request) throws IOException {
        final Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}
