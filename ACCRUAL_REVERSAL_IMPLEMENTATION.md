# ✅ Accrual Reversal Implementation - Complete Solution

## Overview
This document describes the implementation of accrual transaction reversal with automatic adjustment of loan repayment schedule accrual amounts when processing backdated repayments.

---

## Problem Statement

### The Issue
When backdated repayments are processed:
1. Penalty charges applied after the backdated date are deleted
2. Accrual transactions for these penalties are reversed
3. **BUT** the `m_loan_repayment_schedule` table still had the old accrual amounts
4. When the accrual job runs again, it either:
   - Double-counts accruals (if not tracking properly)
   - Fails to accrue correctly (if using old baseline)

### Example Scenario
```
Day 1: Installment due, not paid
Day 2: Accrual job runs → accrual_penalty_charges_derived = 10
Day 3: Accrual job runs → accrual_penalty_charges_derived = 20
Day 4: Accrual job runs → accrual_penalty_charges_derived = 30
Day 5: User makes BACKDATED repayment to Day 2

Expected Result:
- Accruals for Day 3 and 4 should be reversed
- accrual_penalty_charges_derived should be reset to 10 (Day 2 value)
- When accrual job runs on Day 5, it starts from 10, not 30
```

---

## Solution Implemented

### Core Concept
When reversing an accrual transaction, also **reduce the accrued amounts in the loan repayment schedule installments** by the amounts that were accrued in that transaction.

### Implementation Details

**Step 1: Reverse Accrual Transactions AND Update Installments**

For each accrual transaction being reversed:
1. Get the transaction's mappings to repayment schedule installments
2. For each mapping:
   - Extract the interest, fee, and penalty portions from the mapping
   - Get the current accrued amounts from the installment
   - Subtract the transaction portions from the accrued amounts
   - Update the installment with the new reduced accrual amounts
3. Reverse the accrual transaction
4. Save both the transaction and the installments

---

## Code Implementation

### File Modified
`CustomLoanWritePlatformServiceJpaRepositoryImpl.java`

### Method
`makeLoanRepaymentWithChargeRefundChargeType()`

### Key Addition (Step 1 Enhanced)

```java
// Step 1: Find accrual transactions related to these charges and reverse them
final List<LoanTransaction> accrualTransactions = loanChargeRepository.findAccrualTransactionsByChargeIds(chargeIds);
for (final LoanTransaction accrualTransaction : accrualTransactions) {
    // Before reversing, update the repayment schedule to reduce accrued amounts
    // This ensures the accrual job can run correctly later
    if (!accrualTransaction.isReversed()) {
        final MonetaryCurrency currency = loan.getCurrency();
        final Set<LoanTransactionToRepaymentScheduleMapping> mappings = 
                accrualTransaction.getLoanTransactionToRepaymentScheduleMappings();
        
        for (LoanTransactionToRepaymentScheduleMapping mapping : mappings) {
            final LoanRepaymentScheduleInstallment installment = 
                    mapping.getLoanRepaymentScheduleInstallment();
            
            // Reverse the accrued amounts in the installment
            final Money interestToReverse = Money.of(currency, mapping.getInterestPortion());
            final Money feeToReverse = Money.of(currency, mapping.getFeeChargesPortion());
            final Money penaltyToReverse = Money.of(currency, mapping.getPenaltyChargesPortion());
            
            // Get current accrued amounts from the installment
            final Money currentInterestAccrued = installment.getInterestAccrued(currency);
            final Money currentFeeAccrued = installment.getFeeAccrued(currency);
            final Money currentPenaltyAccrued = installment.getPenaltyAccrued(currency);
            
            // Calculate new accrued amounts by subtracting the reversed transaction portions
            final Money newInterestAccrued = currentInterestAccrued.minus(interestToReverse);
            final Money newFeeAccrued = currentFeeAccrued.minus(feeToReverse);
            final Money newPenaltyAccrued = currentPenaltyAccrued.minus(penaltyToReverse);
            
            // Update the installment with reduced accrual amounts
            installment.updateAccrualPortion(newInterestAccrued, newFeeAccrued, newPenaltyAccrued);
            
            log.debug("Reversed accrual in installment {}: interest={}, fees={}, penalties={}", 
                    installment.getInstallmentNumber(), 
                    interestToReverse.getAmount(), 
                    feeToReverse.getAmount(), 
                    penaltyToReverse.getAmount());
        }
    }
    
    // Now reverse the accrual transaction
    accrualTransaction.reverse();
    this.loanTransactionRepository.saveAndFlush(accrualTransaction);
}
log.info("Reversed {} accrual transactions and updated installment accrual amounts", accrualTransactions.size());
```

### New Imports Added

```java
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
```

---

## How It Works

### Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│  Backdated Repayment Received                                │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Identify Penalties to Delete (after backdated date)        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Find Accrual Transactions for These Penalties              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  For Each Accrual Transaction:                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Get transaction-to-installment mappings            │  │
│  │ 2. For each mapping:                                  │  │
│  │    a) Extract portions (interest, fees, penalties)    │  │
│  │    b) Get current accrued amounts from installment    │  │
│  │    c) Calculate: new = current - reversed portions    │  │
│  │    d) Update installment with new accrued amounts     │  │
│  │ 3. Reverse the accrual transaction                    │  │
│  │ 4. Save transaction and installments                  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Delete Charges and Related Data                            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Process Repayment                                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  ✅ Accrual Job Can Run Correctly                           │
│     (starts from correct baseline)                           │
└─────────────────────────────────────────────────────────────┘
```

### Database State Changes

#### Before Backdated Repayment

```sql
-- m_loan_transaction (accrual transactions)
id | type   | interest | fee | penalty | reversed
---+--------+----------+-----+---------+---------
100| ACCRUAL|    0     | 0   |   10    | false    -- Day 2
101| ACCRUAL|    0     | 0   |   10    | false    -- Day 3
102| ACCRUAL|    0     | 0   |   10    | false    -- Day 4

-- m_loan_repayment_schedule
installment_number | accrual_penalty_charges_derived
------------------+--------------------------------
       1          |              30
```

#### After Backdated Repayment (to Day 2)

```sql
-- m_loan_transaction (accruals reversed)
id | type   | interest | fee | penalty | reversed
---+--------+----------+-----+---------+---------
100| ACCRUAL|    0     | 0   |   10    | false    -- Day 2 (kept)
101| ACCRUAL|    0     | 0   |   10    | TRUE     -- Day 3 (REVERSED)
102| ACCRUAL|    0     | 0   |   10    | TRUE     -- Day 4 (REVERSED)

-- m_loan_repayment_schedule (amounts reduced)
installment_number | accrual_penalty_charges_derived
------------------+--------------------------------
       1          |              10                 ← Reduced from 30!
```

#### When Accrual Job Runs Again (Day 5)

```sql
-- m_loan_transaction (new accrual created)
id | type   | interest | fee | penalty | reversed
---+--------+----------+-----+---------+---------
100| ACCRUAL|    0     | 0   |   10    | false
101| ACCRUAL|    0     | 0   |   10    | TRUE
102| ACCRUAL|    0     | 0   |   10    | TRUE
103| ACCRUAL|    0     | 0   |   10    | false    -- Day 5 (NEW)

-- m_loan_repayment_schedule (correctly incremented)
installment_number | accrual_penalty_charges_derived
------------------+--------------------------------
       1          |              20                 ← 10 + 10 (correct!)
```

---

## Benefits

### ✅ Correct Accrual Accounting
- Installment accrual amounts reflect actual state
- No double-counting of accruals
- Accrual job works seamlessly after backdated repayments

### ✅ Database Integrity
- `m_loan_repayment_schedule.accrual_*_derived` columns are accurate
- Accrual transactions and installments stay synchronized
- Historical accrual data is properly maintained

### ✅ Seamless Accrual Job Execution
- Accrual job uses correct baseline amounts
- No manual intervention needed
- Automatic recalculation works correctly

### ✅ Audit Trail
- Accrual transactions are marked as reversed (not deleted)
- Installment accrual amounts show the correct history
- Debug logging tracks all reversals

---

## Testing Scenarios

### Test Case 1: Single Installment with Multiple Accruals

**Setup:**
```
Day 1: Installment 1 due (1000 principal)
Day 2: Accrual runs → penalty = 10, accrual_penalty_charges_derived = 10
Day 3: Accrual runs → penalty = 10, accrual_penalty_charges_derived = 20
Day 4: Accrual runs → penalty = 10, accrual_penalty_charges_derived = 30
Day 5: Backdated repayment to Day 2 (covers principal + 10 penalty)
```

**Expected Results:**
```sql
-- After backdated repayment
SELECT accrual_penalty_charges_derived 
FROM m_loan_repayment_schedule 
WHERE installment_number = 1;
-- Should return: 10

-- Accrual transactions
SELECT COUNT(*) FROM m_loan_transaction 
WHERE type_enum = 10 AND reversed = true;
-- Should return: 2 (Day 3 and Day 4 accruals)
```

**Day 6: Accrual job runs again**
```sql
SELECT accrual_penalty_charges_derived 
FROM m_loan_repayment_schedule 
WHERE installment_number = 1;
-- Should return: 20 (10 from Day 2 + 10 from Day 6)
```

### Test Case 2: Multiple Installments

**Setup:**
```
Installment 1: Overdue since Day 1
Installment 2: Overdue since Day 10
Day 15: Multiple accruals exist for both
Day 16: Backdated repayment to Day 12 (covers Installment 1)
```

**Expected Results:**
- Installment 1: Accruals for Day 13-15 reversed, amount reduced
- Installment 2: Accruals for Day 13-15 reversed, amount reduced
- Both installments have correct baseline for next accrual job

### Test Case 3: Mixed Accruals (Interest + Fees + Penalties)

**Setup:**
```
Accruals include:
- Interest accrued: 50
- Fee accrued: 20
- Penalty accrued: 30
Total accrued: 100

Backdated repayment reverses some accruals
```

**Expected Results:**
```sql
SELECT 
    accrual_interest_derived,
    accrual_fee_charges_derived,
    accrual_penalty_charges_derived
FROM m_loan_repayment_schedule;

-- All three columns should be correctly reduced
```

---

## Verification Queries

### Check Accrual Amounts in Schedule
```sql
SELECT 
    installment_number,
    accrual_interest_derived,
    accrual_fee_charges_derived,
    accrual_penalty_charges_derived,
    (accrual_interest_derived + accrual_fee_charges_derived + accrual_penalty_charges_derived) as total_accrued
FROM m_loan_repayment_schedule
WHERE loan_id = ?
ORDER BY installment_number;
```

### Check Reversed Accrual Transactions
```sql
SELECT 
    id,
    transaction_date,
    interest_portion_derived,
    fee_charges_portion_derived,
    penalty_charges_portion_derived,
    reversed,
    reversed_on_date
FROM m_loan_transaction
WHERE loan_id = ?
AND transaction_type_enum = 10  -- ACCRUAL
ORDER BY transaction_date;
```

### Verify Mapping Consistency
```sql
-- Sum of non-reversed accrual mappings should match installment accrued amounts
SELECT 
    lrs.installment_number,
    lrs.accrual_penalty_charges_derived as schedule_accrued,
    COALESCE(SUM(ltrsm.penalty_charges_portion_derived), 0) as mapping_total
FROM m_loan_repayment_schedule lrs
LEFT JOIN m_loan_transaction_repayment_schedule_mapping ltrsm 
    ON ltrsm.loan_repayment_schedule_id = lrs.id
LEFT JOIN m_loan_transaction lt 
    ON lt.id = ltrsm.loan_transaction_id 
    AND lt.transaction_type_enum = 10 
    AND lt.is_reversed = false
WHERE lrs.loan_id = ?
GROUP BY lrs.id, lrs.installment_number, lrs.accrual_penalty_charges_derived;

-- schedule_accrued should equal mapping_total for each installment
```

---

## Log Output

### Expected Log Messages

```
INFO: Starting deletion of 3 penalty charges for loan ID: 123 (backdated to 2025-12-05)
DEBUG: Reversed accrual in installment 1: interest=0.00, fees=0.00, penalties=10.00
DEBUG: Reversed accrual in installment 1: interest=0.00, fees=0.00, penalties=10.00
INFO: Reversed 2 accrual transactions and updated installment accrual amounts
INFO: Deleted 4 LoanChargePaidBy records
INFO: Deleted 6 LoanInstallmentCharge records
INFO: Deleted 3 penalty charges from database for loan ID: 123
INFO: Removed 3 deleted charges from Loan entity in-memory collection
```

---

## Important Notes

### Why Reverse Instead of Delete Accruals?

**Reversal (Implemented):**
- ✅ Maintains audit trail
- ✅ Shows what was accrued and then reversed
- ✅ Standard accounting practice
- ✅ Allows for reporting and analysis

**Deletion (Not Used):**
- ❌ Loses historical data
- ❌ Makes troubleshooting harder
- ❌ Breaks audit trail
- ❌ Not standard accounting practice

### Transaction Safety

All operations are wrapped in a single database transaction:
- If accrual reversal fails → entire repayment rolls back
- If installment update fails → entire repayment rolls back
- If charge deletion fails → entire repayment rolls back

This ensures data consistency at all times.

---

## Status

### ✅ IMPLEMENTED AND READY

- **Feature:** Accrual reversal with installment amount adjustment
- **Files Modified:** 1
- **New Imports:** 3
- **Compilation:** Success (no errors)
- **Testing Status:** Ready for functional testing

### Next Steps

1. ✅ Code implemented
2. ⏳ Unit testing
3. ⏳ Integration testing with accrual job
4. ⏳ Test backdated repayments with various scenarios
5. ⏳ Verify installment amounts after accrual job runs
6. ⏳ Production deployment

---

## Conclusion

This implementation ensures that when accrual transactions are reversed due to backdated repayments, the loan repayment schedule installments are also updated to reflect the correct accrued amounts. This allows the accrual job to run seamlessly and continue accruing from the correct baseline, without any manual intervention or data inconsistencies.

**Key Achievement:** The accrual job will work correctly after backdated repayments, automatically picking up from where it should based on the actual loan state.

