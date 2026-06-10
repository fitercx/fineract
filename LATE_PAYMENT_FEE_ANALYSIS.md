# Late Payment Fee (LPI) Analysis - Fineract

## Simple explanation: overdue interest and late fees

### What shows up as “Overdue Interest”

On the repayment schedule, **overdue interest / LPI** is driven by **overdue-installment penalty charges** (Fineract: `ChargeTimeType.OVERDUE_INSTALLMENT`). Amounts are stored on the installment as **penalty** components and surfaced in the UI as overdue interest. They are **not** the same as the loan’s normal **installment interest** column; that is scheduled principal/interest. Penalties are **separate charges** applied when an installment is overdue.

### How **days** control **when** a charge can be applied

Two global settings matter most:

| Setting | Role |
|--------|------|
| **`penalty-wait-period`** | Number of **full calendar days after the installment due date** before the loan is treated as overdue for **penalty** purposes. |
| **`grace-on-penalty-posting`** | Used when **computing charge due dates** inside `applyChargeToOverdueLoanInstallment` (see below). It does **not** replace `penalty-wait-period`; it shifts how the first charge’s due date is aligned. |
| **`backdate-penalties`** | If **false**, only installments in a **narrow due-date window** are processed each job run (so penalties are not all applied in one shot for every past due). If **true**, all qualifying overdue installments are processed. |

**Batch job (what actually runs nightly):** `retrieveAllLoansWithOverdueInstallments` uses SQL equivalent to:

```text
currentBusinessDate - penaltyWaitPeriod > installment.dueDate
```

So the **first** business date on which an installment can appear in the batch is:

```text
firstEligibleBusinessDate = dueDate + penaltyWaitPeriod + 1   (calendar days)
```

Example: due **1 June**, `penaltyWaitPeriod = 3` → `currentBusinessDate - 3 > 1 June` first holds on **5 June**.

**Important:** `LoanRepaymentScheduleInstallment.isOverdueOn(date)` is simply `date > dueDate` (strict). The penalty job combines that with `penalty-wait-period` in the query above; it does **not** use the loan product’s repayment “grace days” field in this SQL—those affect **other** behaviours (e.g. schedule generation / NPA). Do not mix **product grace days** with **`penalty-wait-period`** unless you have custom code that ties them together.

### How the **amount** is calculated (not “interest × days” by default)

Late fees are **not** calculated as “annual rate × overdue days” in core unless you configure something that behaves that way. Standard behaviour:

1. **Flat charge**
   The charge carries a fixed **amount** (from the charge definition).

2. **Percentage charge**
   The charge defines a **percentage** and a **calculation type**. The base is taken from the **linked overdue installment’s outstanding amounts** at recalculation time:

   - `PERCENT_OF_AMOUNT` → **principal outstanding** on that installment
   - `PERCENT_OF_AMOUNT_AND_INTEREST` → **principal + interest outstanding**
   - `PERCENT_OF_INTEREST` → **interest outstanding**

   Formula: `percentage / 100 × base` (see `Loan.calculateOverdueAmountPercentageAppliedTo` / `LoanChargeService.recalculateLoanCharge`).

3. **Why overdue interest can grow over time**

   - **Outstanding base** can increase if the borrower underpays (more principal/interest left on that period).
   - **Fee frequency** on the charge (e.g. monthly): `applyChargeToOverdueLoanInstallment` can schedule **multiple** penalty occurrences over time (each with its own due date in the `scheduleDates` loop), so later periods can add more penalty.
   - **Partial payments** reduce some components but leave others; the next recalculation uses the new outstanding balances.

So growth in the schedule is **not** “one extra day always adds X to the same formula” in core—it is **charge definition + outstanding balances + frequency + when the job runs**.

### Charge due dates inside `applyChargeToOverdueLoanInstallment`

Rough structure (see `LoanChargeWritePlatformServiceImpl.applyChargeToOverdueLoanInstallment`):

```text
startDate = dueDate + (penaltyWaitPeriod + 1) days
diff = max(1, penaltyWaitPeriod + 1 - graceOnPenaltyPosting)
first charge due date key = startDate - diff days
```

If the charge has a **fee frequency** (e.g. monthly), additional `scheduleDates` are generated for subsequent periods until `startDate` is in the future. Already-applied frequencies are skipped via `retrieveOverdueInstallmentChargeFrequencyNumber`.

---

## Executive summary

This document describes how **late payment / overdue interest** charges are applied by the penalty job, how **`penalty-wait-period`** and **`backdate-penalties`** interact with **calendar days**, and how **amounts** are derived. It also notes **bulk charge removal** behaviour at a high level.

---

## Scenario (for illustration)

- **First repayment due date**: e.g. 1 June
- **`penalty-wait-period`**: from `ConfigurationDomainService.retrievePenaltyWaitPeriod()`
- **`grace-on-penalty-posting`**: from `ConfigurationDomainService.retrieveGraceOnPenaltyPostingPeriod()`

---

## Late payment fee application logic

### 1. When the batch considers an installment overdue

**Files**

- Job: `CustomApplyChargeToOverdueLoanInstallmentTasklet` → `retrieveAllLoansWithOverdueInstallments(penaltyWaitPeriod, backdatePenalties)`
- Per-loan API path: `LoanReadPlatformServiceImpl.retrieveAllOverdueInstallmentsForLoan` (in-memory checks; can differ slightly on boundary—**batch SQL is authoritative for the job**)

**SQL condition (core)** — `LoanReadPlatformServiceImpl.retrieveAllLoansWithOverdueInstallments`:

```text
DATE_SUB(currentBusinessDate, INTERVAL penaltyWaitPeriod DAY) > ls.duedate
```

So the installment is in scope only after **`dueDate + penaltyWaitPeriod`** has passed (strictly: first business day **after** that sum is **`dueDate + penaltyWaitPeriod + 1`**).

**When `backdatePenalties` is false**, an extra filter is applied:

```text
ls.duedate >= DATE_SUB(currentBusinessDate, INTERVAL (penaltyWaitPeriod + 1) DAY)
```

Together with the first condition, this restricts each run to installments whose **due date** falls in a **single-day band** relative to the business date (so penalties are rolled forward day-by-day rather than backfilling every historical overdue row in one run).

**When `backdatePenalties` is true**, that second filter is omitted, so **all** overdue installments matching the first condition are processed.

### 2. In-memory check (per loan)

```819:821:fineract/fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/domain/LoanRepaymentScheduleInstallment.java
    public boolean isOverdueOn(final LocalDate date) {
        return DateUtils.isAfter(date, getDueDate());
    }
```

```1772:1788:fineract/fineract-provider/src/main/java/org/apache/fineract/portfolio/loanaccount/service/LoanReadPlatformServiceImpl.java
        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (installment.isObligationsMet() || installment.isRecalculatedInterestComponent()) {
                continue;
            }

            boolean isPenaltyDue = installment.isOverdueOn(DateUtils.getBusinessLocalDate().minusDays(penaltyWaitPeriod).plusDays(1));
            boolean isDueToday = installment.getDueDate().equals(DateUtils.getBusinessLocalDate().minusDays(penaltyWaitPeriod));

            if (isPenaltyDue) {
                if (!backdatePenalties && !isDueToday) {
                    continue;
                }

                list.add(new OverdueLoanScheduleData(loan.getId(), penaltyCharge.getId(),
```

Use this when reasoning about **API** behaviour; for **job** date boundaries, prefer the SQL above.

### 3. “Grace” vs “penalty wait”

- **`penalty-wait-period`**: Global delay before overdue penalties are considered (see SQL).
- **Loan / product repayment grace** (e.g. days after due before arrears): May change **due dates** or **ageing** elsewhere; **not** the same variable as `penalty-wait-period` in the penalty SQL.
- **`grace-on-penalty-posting`**: Adjusts **internal charge scheduling** (`diff` in `applyChargeToOverdueLoanInstallment`), not the plain English “grace days” table unless your deployment maps them in config.

---

## Important configuration

| Key | Purpose |
|-----|---------|
| `penalty-wait-period` | Days after due before installment is eligible for penalty in batch SQL |
| `grace-on-penalty-posting` | Used in `diff` when building `scheduleDates` for overdue charges |
| `backdate-penalties` | Broad vs narrow selection of overdue installments per run |

**SQL pattern (active loans, overdue charge, not completed):**

```sql
-- Illustrative; exact SQL uses DatabaseSpecificSQLGenerator
WHERE DATE_SUB(current_business_date, INTERVAL ? DAY) > ls.duedate
  AND ls.completed_derived <> true
  AND mc.charge_time_enum = 9   -- OVERDUE_INSTALLMENT
  AND ml.loan_status_id = 300   -- ACTIVE
```

---

## Bulk charge removal (high level)

**Code locations:** `CredXLoanChargeWritePlatformService.deleteLoanCharges()` (custom), `LoanChargeWritePlatformServiceImpl`.

**Behaviour:** Charges can be deactivated; loan summary and installment charges should be recalculated. Verify in your environment: soft-delete vs hard-delete, `m_loan_overdue_installment_charge`, and transactions referencing removed charges.

---

## SQL verification (examples)

### Charges linked to schedule

```sql
SELECT
    l.account_no,
    ls.duedate,
    ls.installment,
    lc.charge_time_enum,
    lc.amount,
    lc.is_active,
    loic.id AS overdue_charge_id
FROM m_loan l
JOIN m_loan_repayment_schedule ls ON ls.loan_id = l.id
LEFT JOIN m_loan_charge lc ON lc.loan_id = l.id
    AND lc.charge_time_enum = 9
LEFT JOIN m_loan_overdue_installment_charge loic
    ON loic.loan_charge_id = lc.id
    AND loic.loan_schedule_id = ls.id
WHERE l.id = ?
ORDER BY ls.installment;
```

---

## Key files

| Area | File |
|------|------|
| Penalty job | `custom/.../CustomApplyChargeToOverdueLoanInstallmentTasklet.java` |
| Overdue rows (batch) | `LoanReadPlatformServiceImpl.retrieveAllLoansWithOverdueInstallments` |
| Apply charge | `LoanChargeWritePlatformServiceImpl.applyOverdueChargesForLoan`, `applyChargeToOverdueLoanInstallment` |
| Percentage base | `Loan.calculateOverdueAmountPercentageAppliedTo` |
| Recalculate charge amount | `LoanChargeService.recalculateLoanCharge` |
| Installment overdue? | `LoanRepaymentScheduleInstallment.isOverdueOn` |

---

## Conclusion

1. **Calendar / days:** The batch treats an installment as overdue for penalties when **`currentBusinessDate - penaltyWaitPeriod > dueDate`**. First calendar day that satisfies this is **`dueDate + penaltyWaitPeriod + 1`**.
2. **Amount:** Late charges are **flat** or **percentage of outstanding** (principal / principal+interest / interest) on the **overdue installment**, not an automatic “daily interest rate × days” unless the product is built that way. Repeated or growing amounts usually come from **percentage × outstanding**, **fee frequency**, and **job timing**.
3. **Bulk removal:** Expect summary and schedule updates; validate mappings and transactions in QA.

---

**Document status:** Updated to align with core Fineract paths in this repo (`LoanReadPlatformServiceImpl`, `LoanChargeWritePlatformServiceImpl`, `Loan`, `LoanRepaymentScheduleInstallment`).
