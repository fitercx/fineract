package com.crediblex.fineract.portfolio.loc.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LineOfCreditRepositoryWrapper {

    private final LineOfCreditRepository lineOfCreditRepository;

    public LineOfCredit saveAndFlush(final LineOfCredit loc) {
        return this.lineOfCreditRepository.saveAndFlush(loc);
    }

    public LineOfCredit findOneWithNotFoundDetection(final Long locId) {
        return this.lineOfCreditRepository.findById(locId)
                .orElseThrow(() -> new IllegalArgumentException("Line of Credit with id " + locId + " not found"));
    }

}
