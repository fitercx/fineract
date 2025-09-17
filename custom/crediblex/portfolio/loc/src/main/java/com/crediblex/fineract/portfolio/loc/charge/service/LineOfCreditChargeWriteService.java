package com.crediblex.fineract.portfolio.loc.charge.service;

import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;

public interface LineOfCreditChargeWriteService {
    // existing granular methods (retained for internal reuse)
    LocChargeData waiveCharge(Long locId, Long chargeInstanceId);
    void deleteCharge(Long locId, Long chargeInstanceId);

    CommandProcessingResult waive(Long chargeInstanceId, JsonCommand command);
    CommandProcessingResult delete(Long chargeInstanceId, JsonCommand command);
}
