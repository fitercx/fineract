package com.crediblex.fineract.portfolio.dpdrepayment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.dpdrepayment.DpdRepaymentConstants;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DpdRepaymentGlobalConfigServiceTest {

    @Mock
    private ConfigurationReadPlatformService configurationReadPlatformService;

    @InjectMocks
    private DpdRepaymentGlobalConfigService globalConfigService;

    @Test
    void getPrincipalOnlyThreshold_returnsConfiguredValue() {
        final GlobalConfigurationPropertyData property = new GlobalConfigurationPropertyData();
        property.setValue(45L);
        when(configurationReadPlatformService.retrieveGlobalConfiguration(DpdRepaymentConstants.GLOBAL_CONFIG_DPD_PRINCIPAL_ONLY_THRESHOLD))
                .thenReturn(property);

        assertEquals(45, globalConfigService.getPrincipalOnlyThreshold());
    }

    @Test
    void getPrincipalOnlyThreshold_defaultsToSixtyWhenMissing() {
        when(configurationReadPlatformService.retrieveGlobalConfiguration(DpdRepaymentConstants.GLOBAL_CONFIG_DPD_PRINCIPAL_ONLY_THRESHOLD))
                .thenReturn(new GlobalConfigurationPropertyData());

        assertEquals(DpdRepaymentConstants.DEFAULT_DPD_THRESHOLD, globalConfigService.getPrincipalOnlyThreshold());
    }
}
