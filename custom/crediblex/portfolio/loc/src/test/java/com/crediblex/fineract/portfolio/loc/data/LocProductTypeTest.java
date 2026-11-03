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
package com.crediblex.fineract.portfolio.loc.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocProductTypeTest {

    @Test
    void fromIntResolvesAllTypes() {
        assertEquals(LocProductType.RECEIVABLE, LocProductType.fromInt(1));
        assertEquals(LocProductType.PAYABLE, LocProductType.fromInt(2));
        assertEquals(LocProductType.RECEIVABLE_NON_RECOURSE, LocProductType.fromInt(3));
        assertNull(LocProductType.fromInt(4));
        assertNull(LocProductType.fromInt(null));
    }

    @Test
    void receivableIsShortDisbursedReceivableFamily() {
        assertTrue(LocProductType.RECEIVABLE.isReceivable());
        assertTrue(LocProductType.RECEIVABLE.isReceivableFamily());
        assertTrue(LocProductType.RECEIVABLE.isShortDisbursed());
        assertFalse(LocProductType.RECEIVABLE.isReceivableNonRecourse());
        assertFalse(LocProductType.RECEIVABLE.isPayable());
    }

    @Test
    void nonRecourseIsReceivableFamilyWithoutShortDisbursal() {
        assertTrue(LocProductType.RECEIVABLE_NON_RECOURSE.isReceivableNonRecourse());
        assertTrue(LocProductType.RECEIVABLE_NON_RECOURSE.isReceivableFamily());
        assertFalse(LocProductType.RECEIVABLE_NON_RECOURSE.isReceivable());
        assertFalse(LocProductType.RECEIVABLE_NON_RECOURSE.isShortDisbursed());
        assertFalse(LocProductType.RECEIVABLE_NON_RECOURSE.isPayable());
    }

    @Test
    void payableIsNotReceivableFamily() {
        assertTrue(LocProductType.PAYABLE.isPayable());
        assertFalse(LocProductType.PAYABLE.isReceivableFamily());
        assertFalse(LocProductType.PAYABLE.isShortDisbursed());
    }
}
