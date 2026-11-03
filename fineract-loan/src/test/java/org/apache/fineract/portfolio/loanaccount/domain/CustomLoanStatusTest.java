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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CustomLoanStatusTest {

    @Test
    void forForeclosure_forcedClosureTakesPrecedence() {
        assertEquals(CustomLoanStatus.FORCED_CLOSURE, CustomLoanStatus.forForeclosure(true, false));
        assertEquals(CustomLoanStatus.FORCED_CLOSURE, CustomLoanStatus.forForeclosure(true, true));
    }

    @Test
    void forForeclosure_restructuredWhenNotForced() {
        assertEquals(CustomLoanStatus.RESTRUCTURED, CustomLoanStatus.forForeclosure(false, true));
        assertEquals(CustomLoanStatus.RESTRUCTURED, CustomLoanStatus.forForeclosure(null, true));
    }

    @Test
    void forForeclosure_earlyClosureWhenNeitherFlagSet() {
        assertEquals(CustomLoanStatus.EARLY_CLOSURE, CustomLoanStatus.forForeclosure(false, false));
        assertEquals(CustomLoanStatus.EARLY_CLOSURE, CustomLoanStatus.forForeclosure(null, null));
    }

    @Test
    void fromInt_roundTripsRestructured() {
        assertEquals(CustomLoanStatus.RESTRUCTURED, CustomLoanStatus.fromInt(9004));
        assertEquals(9004, CustomLoanStatus.RESTRUCTURED.getValue());
        assertTrue(CustomLoanStatus.RESTRUCTURED.isRestructured());
    }
}
