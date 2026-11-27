package com.crediblex.fineract.portfolio.dpdrepayment.service;

import com.crediblex.fineract.portfolio.dpdrepayment.DpdRepaymentConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.springframework.stereotype.Service;

/**
 * Reads the fleet-wide DPD principal-only threshold from {@code c_configuration} (name
 * {@code dpd-principal-only-threshold}). Editable from System &gt; Global Configurations in the UI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DpdRepaymentGlobalConfigService {

    private final ConfigurationReadPlatformService configurationReadPlatformService;

    public int getPrincipalOnlyThreshold() {
        try {
            final GlobalConfigurationPropertyData property = configurationReadPlatformService
                    .retrieveGlobalConfiguration(DpdRepaymentConstants.GLOBAL_CONFIG_DPD_PRINCIPAL_ONLY_THRESHOLD);
            if (property != null && property.getValue() != null && property.getValue() > 0) {
                return property.getValue().intValue();
            }
        } catch (Exception e) {
            log.warn("Could not read '{}' global config, defaulting to {} days: {}",
                    DpdRepaymentConstants.GLOBAL_CONFIG_DPD_PRINCIPAL_ONLY_THRESHOLD, DpdRepaymentConstants.DEFAULT_DPD_THRESHOLD,
                    e.getMessage());
        }
        return DpdRepaymentConstants.DEFAULT_DPD_THRESHOLD;
    }
}
