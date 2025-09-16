package com.crediblex.fineract.portfolio.loc.charge.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineOfCreditChargeRepository extends JpaRepository<LineOfCreditCharge, Long> {

    @Query("SELECT c FROM LineOfCreditCharge c WHERE c.lineOfCredit.id = :locId AND c.active = true AND c.paid = false AND c.waived = false AND c.amountOutstanding > 0 ORDER BY c.chargeDueDate NULLS LAST, c.id")
    List<LineOfCreditCharge> findUnpaidOrdered(@Param("locId") Long locId);

    Optional<LineOfCreditCharge> findByIdAndLineOfCredit_Id(Long id, Long locId);
}
