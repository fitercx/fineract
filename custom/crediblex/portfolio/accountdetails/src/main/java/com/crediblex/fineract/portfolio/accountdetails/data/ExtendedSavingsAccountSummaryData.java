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

package com.crediblex.fineract.portfolio.accountdetails.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.accountdetails.data.SavingsAccountSummaryData;
import org.apache.fineract.portfolio.savings.data.SavingsAccountApplicationTimelineData;
import org.apache.fineract.portfolio.savings.data.SavingsAccountStatusEnumData;
import org.apache.fineract.portfolio.savings.data.SavingsAccountSubStatusEnumData;

public class ExtendedSavingsAccountSummaryData extends SavingsAccountSummaryData {

    protected Map<String, Object> additionalProperties = new HashMap<>();

    public ExtendedSavingsAccountSummaryData(Long id, String accountNo, String externalId, Long productId, String productName,
            String shortProductName, SavingsAccountStatusEnumData status, CurrencyData currency, BigDecimal accountBalance,
            EnumOptionData accountType, SavingsAccountApplicationTimelineData timeline, EnumOptionData depositType,
            SavingsAccountSubStatusEnumData subStatus, LocalDate lastActiveTransactionDate) {
        super(id, accountNo, externalId, productId, productName, shortProductName, status, currency, accountBalance, accountType, timeline,
                depositType, subStatus, lastActiveTransactionDate);
    }

    public void addCustomParameter(String name, Object value) {
        additionalProperties.put(name, value);
    }
}
