package com.crediblex.fineract.portfolio.loc.domain;

import com.crediblex.fineract.portfolio.loc.exception.LineOfCreditNotFoundException;
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
        return this.lineOfCreditRepository.findById(locId).orElseThrow(() -> new LineOfCreditNotFoundException(locId));
    }

    public LineOfCredit findByExternalIdWithNotFoundDetection(final String externalId) {
        return this.lineOfCreditRepository.findByExternalId(externalId)
                .orElseThrow(() -> new LineOfCreditNotFoundException("with externalId: " + externalId));
    }

    public void delete(final LineOfCredit loc) {
        this.lineOfCreditRepository.delete(loc);
    }

}
