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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * Signs outgoing web hook requests so receivers can verify they came from Fineract.
 *
 * <p>
 * Adds {@value #TIMESTAMP_HEADER} (Unix epoch seconds) and {@value #SIGNATURE_HEADER} ({@code v1=<hex>}), where
 * {@code hex = HMAC-SHA256(secret, timestamp + "." + rawBody)}. The body is re-attached as the exact bytes that were
 * signed, so the receiver can verify against the raw request body it gets.
 */
public final class WebHookSigningInterceptor implements Interceptor {

    public static final String TIMESTAMP_HEADER = "X-Fineract-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Fineract-Signature";
    static final String SIGNATURE_VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final Clock clock;

    public WebHookSigningInterceptor(final String secret, final Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Web hook signing secret must not be blank");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.clock = clock;
    }

    @Override
    public Response intercept(final Chain chain) throws IOException {
        final Request original = chain.request();
        final byte[] body = readBody(original.body());
        final String timestamp = String.valueOf(clock.instant().getEpochSecond());

        final Request.Builder signed = original.newBuilder().header(TIMESTAMP_HEADER, timestamp).header(SIGNATURE_HEADER,
                SIGNATURE_VERSION + "=" + sign(timestamp, body));
        if (original.body() != null) {
            signed.method(original.method(), RequestBody.create(body, original.body().contentType()));
        }
        return chain.proceed(signed.build());
    }

    String sign(final String timestamp, final byte[] body) {
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to sign web hook request", e);
        }
    }

    private static byte[] readBody(final RequestBody body) throws IOException {
        if (body == null) {
            return new byte[0];
        }
        final Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readByteArray();
    }
}
