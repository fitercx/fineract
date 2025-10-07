package com.crediblex.fineract.portfolio.loc.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineOfCreditTransactionRepository extends JpaRepository<LineOfCreditTransaction, Long> {

    @Query("SELECT t FROM LineOfCreditTransaction t WHERE t.lineOfCredit.id = :lineOfCreditId ORDER BY t.transactionDate DESC, t.id DESC")
    List<LineOfCreditTransaction> findLatestTransaction(@Param("lineOfCreditId") Long lineOfCreditId, Pageable pageable);

    @Query("SELECT t FROM LineOfCreditTransaction t WHERE t.lineOfCredit.id = :lineOfCreditId and t.transactionDate >= :fetchFromDate ORDER BY t.transactionDate DESC, t.id DESC")
    List<LineOfCreditTransaction> findByLineOfCreditIdAndTransactionDateGreaterThanOrEqualTo(@Param("lineOfCreditId") Long lineOfCreditId,
            @Param("fetchFromDate") LocalDate fetchFromDate);

    @Query("SELECT COUNT(t) FROM LineOfCreditTransaction t WHERE t.lineOfCredit.id = :lineOfCreditId AND t.transactionDate < :transactionDate AND t.transactionType = :transactionType")
    Long countByLineOfCreditIdAndTransactionDateLessThanAndTransactionType(@Param("lineOfCreditId") Long lineOfCreditId,
            @Param("transactionDate") LocalDate transactionDate, @Param("transactionType") LineOfCreditTransactionType transactionType);

    @Query("SELECT t FROM LineOfCreditTransaction t WHERE t.lineOfCredit.id = :lineOfCreditId AND t.transactionDate < :fetchFromDate ORDER BY t.transactionDate DESC, t.id DESC")
    List<LineOfCreditTransaction> findLastTransactionBeforeDate(@Param("lineOfCreditId") Long lineOfCreditId,
            @Param("fetchFromDate") LocalDate fetchFromDate, Pageable pageable);
}
