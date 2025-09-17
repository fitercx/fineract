package com.crediblex.fineract.portfolio.loc.charge.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineOfCreditChargePaidByRepository extends JpaRepository<LineOfCreditChargePaidBy, Long> {
    @Query("SELECT p FROM LineOfCreditChargePaidBy p WHERE p.savingsAccountTransaction.id = :txnId")
    List<LineOfCreditChargePaidBy> findBySavingsTxn(@Param("txnId") Long txnId);

}
