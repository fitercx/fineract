package com.crediblex.fineract.portfolio.loc.data;

import java.time.LocalDate;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
public class LineOfCreditTimeLineData {

    private final LocalDate submittedOnDate;
    private final String submittedByFirstname;
    private final String submittedByLastname;

    private final LocalDate approvedOnDate;
    private final String approvedByFirstname;
    private final String approvedByLastname;

    private final LocalDate activatedOnDate;
    private final String activatedByFirstname;
    private final String activatedByLastname;

    private final LocalDate closedOnDate;
    private final String closedByFirstname;
    private final String closedByLastname;

    private final LocalDate updatedOnDate;
    private final String updatedByFirstname;
    private final String updatedByLastname;
}
