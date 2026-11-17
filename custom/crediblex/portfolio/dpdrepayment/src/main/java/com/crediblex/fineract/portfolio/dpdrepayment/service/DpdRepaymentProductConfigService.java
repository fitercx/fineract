package com.crediblex.fineract.portfolio.dpdrepayment.service;

import com.crediblex.fineract.portfolio.dpdrepayment.DpdRepaymentConstants;
import com.crediblex.fineract.portfolio.dpdrepayment.data.DpdRepaymentProductConfigData;
import com.crediblex.fineract.portfolio.dpdrepayment.domain.DpdRepaymentProductConfig;
import com.crediblex.fineract.portfolio.dpdrepayment.domain.DpdRepaymentProductConfigRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DpdRepaymentProductConfigService {

    private final DpdRepaymentProductConfigRepository configRepository;
    private final DpdRepaymentGlobalConfigService globalConfigService;

    public Optional<DpdRepaymentProductConfigData> findByLoanProductId(final Long loanProductId) {
        return configRepository.findByLoanProductId(loanProductId).map(this::toData);
    }

    public void enrichProductAdditionalProperties(final Long loanProductId, final Map<String, Object> additionalProperties) {
        final DpdRepaymentProductConfigData config = findByLoanProductId(loanProductId).orElse(defaultConfig(loanProductId));
        additionalProperties.put(DpdRepaymentConstants.ENABLE_DPD_PRINCIPAL_ONLY_REPAYMENT, config.isEnableDpdPrincipalOnlyRepayment());
        additionalProperties.put(DpdRepaymentConstants.DPD_PRINCIPAL_ONLY_THRESHOLD, globalConfigService.getPrincipalOnlyThreshold());
    }

    @Transactional
    public Map<String, Object> upsertFromCommand(final Long loanProductId, final JsonCommand command) {
        final Map<String, Object> changes = new HashMap<>();
        if (!command.parameterExists(DpdRepaymentConstants.ENABLE_DPD_PRINCIPAL_ONLY_REPAYMENT)) {
            return changes;
        }

        DpdRepaymentProductConfig config = configRepository.findByLoanProductId(loanProductId)
                .orElseGet(() -> DpdRepaymentProductConfig.createDefault(loanProductId));

        if (command.parameterExists(DpdRepaymentConstants.ENABLE_DPD_PRINCIPAL_ONLY_REPAYMENT)) {
            final boolean newValue = command
                    .booleanPrimitiveValueOfParameterNamed(DpdRepaymentConstants.ENABLE_DPD_PRINCIPAL_ONLY_REPAYMENT);
            if (config.isEnableDpdPrincipalOnly() != newValue) {
                changes.put(DpdRepaymentConstants.ENABLE_DPD_PRINCIPAL_ONLY_REPAYMENT, newValue);
                config.setEnableDpdPrincipalOnly(newValue);
            }
        }

        if (!changes.isEmpty()) {
            configRepository.saveAndFlush(config);
        }
        return changes;
    }

    private DpdRepaymentProductConfigData toData(final DpdRepaymentProductConfig config) {
        return DpdRepaymentProductConfigData.builder().loanProductId(config.getLoanProductId())
                .enableDpdPrincipalOnlyRepayment(config.isEnableDpdPrincipalOnly())
                .dpdPrincipalOnlyThreshold(globalConfigService.getPrincipalOnlyThreshold()).build();
    }

    private DpdRepaymentProductConfigData defaultConfig(final Long loanProductId) {
        return DpdRepaymentProductConfigData.builder().loanProductId(loanProductId).enableDpdPrincipalOnlyRepayment(false)
                .dpdPrincipalOnlyThreshold(globalConfigService.getPrincipalOnlyThreshold()).build();
    }
}
