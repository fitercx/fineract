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
package com.crediblex.fineract.test.api;

import com.crediblex.client.services.LineOfCreditApi;
import com.crediblex.client.services.LoanTransactionsApi;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.client.services.AccountTransfersApi;
import org.apache.fineract.client.services.SchedulerJobApi;
import org.apache.fineract.client.services.TaxComponentsApi;
import org.apache.fineract.client.services.TaxGroupApi;
import org.apache.fineract.client.util.FineractClient;
import org.apache.fineract.test.service.JobService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CustomApiConfiguration {

    private final FineractClient fineractClient;

    @Bean
    public TaxGroupApi taxGroupApi() {
        return fineractClient.createService(TaxGroupApi.class);
    }

    @Bean
    public TaxComponentsApi taxComponentApi() {
        return fineractClient.createService(TaxComponentsApi.class);
    }

    @Bean
    LineOfCreditApi lineOfCreditApi() {
        return fineractClient.createService(LineOfCreditApi.class);
    }

    @Bean
    public LoanTransactionsApi loanTransactionsApi() {return fineractClient.createService(LoanTransactionsApi.class);}

    @Bean
    public AccountTransfersApi accountTransfersApi() {return fineractClient.createService(AccountTransfersApi.class);
    }

    @Bean
    public SchedulerJobApi schedulerJobApi() {
        return fineractClient.createService(SchedulerJobApi.class);
    }
}


