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

package com.crediblex.fineract.loc.service;

import com.crediblex.fineract.loc.data.LineOfCreditData;
import com.crediblex.fineract.loc.data.LineOfCreditTransactionData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface LineOfCreditReadPlatformService {

    Collection<LineOfCreditData> retrieveAllLineOfCredits();

    Page<LineOfCreditData> retrieveAllLineOfCredits(Pageable pageable);

    LineOfCreditData retrieveOne(Long lineOfCreditId);

    LineOfCreditData retrieveTemplate();

    Collection<LineOfCreditData> retrieveAllLineOfCreditsForClient(Long clientId);

    Collection<LineOfCreditData> retrieveActiveLineOfCreditsForClient(Long clientId);

    Collection<LineOfCreditTransactionData> retrieveAllTransactions(Long lineOfCreditId);

    Page<LineOfCreditTransactionData> retrieveAllTransactions(Long lineOfCreditId, Pageable pageable);

    LineOfCreditTransactionData retrieveTransaction(Long transactionId);

    LineOfCreditTransactionData retrieveTransactionTemplate();
} 