package com.crediblex.fineract.portfolio.loc.charge.service;

import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditChargeRepository;
import com.crediblex.fineract.portfolio.loc.charge.validation.LocChargeValidator;

import java.time.LocalDate;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class LineOfCreditChargeWriteServiceImpl implements LineOfCreditChargeWriteService {

    private final LineOfCreditChargeRepository locChargeRepository;
    private final LineOfCreditChargeDomainService domainService;
    private final LocChargeValidator validator;
    private final FromJsonHelper fromJsonHelper;

    @Override
    public LocChargeData waiveCharge(Long locId, Long chargeInstanceId) {
        LineOfCreditCharge charge = fetchOwned(locId, chargeInstanceId);
        domainService.waive(charge); // returns outstanding if needed
        locChargeRepository.save(charge);
        return map(charge);
    }

    @Override
    public void deleteCharge(Long locId, Long chargeInstanceId) {
        LineOfCreditCharge charge = fetchOwned(locId, chargeInstanceId);
        charge.setActive(false);
        charge.setInactivationDate(LocalDate.now());
        locChargeRepository.save(charge);
    }

    private LineOfCreditCharge fetchOwned(Long locId, Long chargeInstanceId) {
        Optional<LineOfCreditCharge> opt = locChargeRepository.findByIdAndLineOfCredit_Id(chargeInstanceId, locId);
        return opt.orElseThrow(() -> new IllegalArgumentException("Charge instance not found for LOC"));
    }

    private LocChargeData map(LineOfCreditCharge c) {
        return LocChargeData.builder()
                .id(c.getId())
                .chargeDefinitionId(c.getChargeDefinition().getId())
                .penalty(c.isPenaltyCharge())
                .chargeTime(c.getChargeTime())
                .chargeCalculation(c.getChargeCalculation())
                .dueDate(c.getChargeDueDate())
                .feeOnMonth(c.getFeeOnMonth())
                .feeOnDay(c.getFeeOnDay())
                .feeInterval(c.getFeeInterval())
                .amount(c.getAmount())
                .amountOutstanding(c.getAmountOutstanding())
                .amountPaid(c.getAmountPaid())
                .amountWaived(c.getAmountWaived())
                .paid(c.isPaid())
                .waived(c.isWaived())
                .active(c.isActive())
                .build();
    }


    @Override
    public CommandProcessingResult waive(Long chargeInstanceId, JsonCommand command) {
        var element = fromJsonHelper.parse(command.json());
        Long locId = fromJsonHelper.extractLongNamed("locId", element);
        LocChargeData waived = waiveCharge(locId, chargeInstanceId);
        return new CommandProcessingResultBuilder().withEntityId(waived.getId()).build();
    }

    @Override
    public CommandProcessingResult delete(Long chargeInstanceId, JsonCommand command) {
        var element = fromJsonHelper.parse(command.json());
        Long locId = fromJsonHelper.extractLongNamed("locId", element);
        deleteCharge(locId, chargeInstanceId);
        return new CommandProcessingResultBuilder().withEntityId(chargeInstanceId).build();
    }

}
