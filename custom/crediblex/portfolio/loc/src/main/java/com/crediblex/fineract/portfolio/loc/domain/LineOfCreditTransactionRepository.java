package com.crediblex.fineract.portfolio.loc.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineOfCreditTransactionRepository extends JpaRepository<LineOfCreditTransaction, Long> {

    // Fetch all transactions for a LOC after (or on) a given date ordered ASC
    List<LineOfCreditTransaction> findByLineOfCreditIdAndTransactionDateGreaterThanEqualOrderByTransactionDateAsc(Long lineOfCreditId,
            LocalDate startDate);

    // Fetch all transactions for a LOC before a given date ordered ASC
    List<LineOfCreditTransaction> findByLineOfCreditIdAndTransactionDateLessThanOrderByTransactionDateAsc(Long lineOfCreditId,
            LocalDate beforeDate);

    // Latest transaction for a LOC
    Optional<LineOfCreditTransaction> findTopByLineOfCreditIdOrderByTransactionDateDesc(Long lineOfCreditId);
}
