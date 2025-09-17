package com.crediblex.fineract.portfolio.loc.charge.service;

import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;

import java.time.LocalDate;
import java.util.List;

public interface LineOfCreditChargeReadService {
    List<LocChargeData> listActive(Long locId);

    List<LocChargeData> listDueOrOverdue(Long locId, LocalDate asOfDate);

    LocChargeData getOne(Long locId, Long chargeInstanceId);
}

