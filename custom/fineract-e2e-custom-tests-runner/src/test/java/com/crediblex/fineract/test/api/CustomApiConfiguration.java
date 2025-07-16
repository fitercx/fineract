package com.crediblex.fineract.test.api;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.client.services.TaxComponentsApi;
import org.apache.fineract.client.services.TaxGroupApi;
import org.apache.fineract.client.util.FineractClient;
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

}
