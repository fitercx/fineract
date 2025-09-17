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

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.accountdetails.data.LoanAccountSummaryData;

/**
 * Data object representing a Line of Credit with its associated loans.
 * This is used by the ResultSetExtractor to efficiently group LOC data with loan data.
 */
@Getter
@RequiredArgsConstructor
public final class LineOfCreditWithLoansData implements Serializable {

    private final LineOfCreditData lineOfCredit;
    private final List<LoanAccountSummaryData> loans;

    /**
     * Returns the count of loans associated with this Line of Credit
     */
    public int getLoanCount() {
        return loans != null ? loans.size() : 0;
    }

    /**
     * Checks if this Line of Credit has any associated loans
     */
    public boolean hasLoans() {
        return loans != null && !loans.isEmpty();
    }
}
