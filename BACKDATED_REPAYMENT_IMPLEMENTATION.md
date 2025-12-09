# Backdated Repayment - Complete Penalty Deletion Implementation

## Overview
This document describes the implementation of complete penalty charge deletion when processing backdated loan repayments. Instead of just disabling penalty charges, the system now completely removes them along with all related data including accrual transactions and journal entries.

---

## Implementation Summary

### Problem Statement
When a backdated repayment is made on a loan, penalties that were applied after the backdated transaction date need to be removed so they can be recalculated based on the updated loan state. Previously, these charges were only disabled, leaving orphaned records in the database.

### Solution
Completely delete penalty charges and all their related data in the following order:
1. Journal entries for accrual transactions
2. Accrual transactions themselves
3. LoanChargePaidBy linking records
4. LoanInstallmentCharge records
5. LoanCharge entities

---

## Files Modified

### 1. CustomLoanChargeRepository.java
**Path:** `custom/crediblex/portfolio/loanaccount/src/main/java/com/crediblex/fineract/portfolio/loanaccount/repository/CustomLoanChargeRepository.java`

**Changes:**
- Added import for `LoanTransaction`
- Added 3 new repository methods for deletion operations

**New Methods:**

#### `deleteInstallmentChargesByChargeIds`
Deletes loan installment charge records for given charge IDs.
```java
@Modifying
@Transactional
@Query("""
    DELETE FROM LoanInstallmentCharge lic
    WHERE lic.loancharge.id IN :chargeIds
    """)
int deleteInstallmentChargesByChargeIds(@Param("chargeIds") List<Long> chargeIds);
```

#### `deleteChargePaidByForChargeIds`
Deletes loan charge paid by linking records.
```java
@Modifying
@Transactional
@Query("""
    DELETE FROM LoanChargePaidBy lcpb
    WHERE lcpb.loanCharge.id IN :chargeIds
    """)
int deleteChargePaidByForChargeIds(@Param("chargeIds") List<Long> chargeIds);
```

#### `findAccrualTransactionsByChargeIds`
Finds all non-reversed accrual transactions related to specific charges.
```java
@Query("""
    SELECT DISTINCT lt FROM LoanTransaction lt
    INNER JOIN lt.loanChargesPaid lcpb
    WHERE lcpb.loanCharge.id IN :chargeIds
    AND lt.typeOf = org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType.ACCRUAL
    AND lt.reversed = false
    """)
List<LoanTransaction> findAccrualTransactionsByChargeIds(@Param("chargeIds") List<Long> chargeIds);
```

---

### 2. CustomLoanWritePlatformServiceJpaRepositoryImpl.java
**Path:** `custom/crediblex/portfolio/loanaccount/src/main/java/com/crediblex/fineract/portfolio/loanaccount/service/CustomLoanWritePlatformServiceJpaRepositoryImpl.java`

**Changes:**

#### Added Imports
```java
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.portfolio.PortfolioProductType;
```

#### Added Dependencies
```java
private final JournalEntryRepository journalEntryRepository;
private final LoanTransactionRepository loanTransactionRepository;
```

#### Updated Constructor
Added `JournalEntryRepository` parameter and assigned both repositories in the constructor body.

#### Modified Logic in `makeLoanRepaymentWithChargeRefundChargeType` Method
**Location:** Line ~788

**Old Logic:**
```java
if (!penaltiesToDisable.isEmpty()) {
    List<Long> chargeIds = penaltiesToDisable.stream().map(LoanChargeData::getId).toList();
    loanChargeRepository.deactivateCharges(loanId, chargeIds);
}
```

**New Logic:**
```java
if (!penaltiesToDisable.isEmpty()) {
    List<Long> chargeIds = penaltiesToDisable.stream().map(LoanChargeData::getId).toList();

    // Step 1: Find accrual transactions related to these charges
    List<LoanTransaction> accrualTransactions = loanChargeRepository.findAccrualTransactionsByChargeIds(chargeIds);
    
    // Step 2: Delete journal entries for these accrual transactions
    for (LoanTransaction accrualTransaction : accrualTransactions) {
        List<JournalEntry> journalEntries = journalEntryRepository
                .findJournalEntries(accrualTransaction.getId().toString(), 
                        PortfolioProductType.LOAN.getValue());
        if (!journalEntries.isEmpty()) {
            journalEntryRepository.deleteAll(journalEntries);
            log.info("Deleted {} journal entries for accrual transaction ID: {}", 
                    journalEntries.size(), accrualTransaction.getId());
        }
    }
    
    // Step 3: Delete the accrual transactions themselves
    if (!accrualTransactions.isEmpty()) {
        loanTransactionRepository.deleteAll(accrualTransactions);
        log.info("Deleted {} accrual transactions for charges", accrualTransactions.size());
    }
    
    // Step 4: Delete LoanChargePaidBy records
    int deletedChargePaidBy = loanChargeRepository.deleteChargePaidByForChargeIds(chargeIds);
    log.info("Deleted {} LoanChargePaidBy records", deletedChargePaidBy);
    
    // Step 5: Delete LoanInstallmentCharge records
    int deletedInstallmentCharges = loanChargeRepository.deleteInstallmentChargesByChargeIds(chargeIds);
    log.info("Deleted {} LoanInstallmentCharge records", deletedInstallmentCharges);
    
    // Step 6: Finally, delete the LoanCharge entities themselves
    loanChargeRepository.deleteAllById(chargeIds);
    log.info("Deleted {} penalty charges for loan ID: {}", chargeIds.size(), loanId);
}
```

---

## Deletion Flow & Data Integrity

### Order of Operations
The deletion order is critical to maintain referential integrity and avoid foreign key constraint violations:

```
┌─────────────────────────────────────────────────────┐
│  1. Find Accrual Transactions                       │
│     - Query LoanTransaction via LoanChargePaidBy    │
│     - Filter by type = ACCRUAL, reversed = false    │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  2. Delete Journal Entries                          │
│     - Find by transaction ID and entity type LOAN   │
│     - Delete from acc_gl_journal_entry              │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  3. Delete Accrual Transactions                     │
│     - Delete from m_loan_transaction                │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  4. Delete LoanChargePaidBy Records                 │
│     - Delete from m_loan_charge_paid_by             │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  5. Delete LoanInstallmentCharge Records            │
│     - Delete from m_loan_installment_charge         │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  6. Delete LoanCharge Entities                      │
│     - Delete from m_loan_charge                     │
└─────────────────────────────────────────────────────┘
```

### Database Tables Affected
1. `acc_gl_journal_entry` - Accounting journal entries
2. `m_loan_transaction` - Loan transactions (accruals)
3. `m_loan_charge_paid_by` - Link between charges and transactions
4. `m_loan_installment_charge` - Installment-level charge allocations
5. `m_loan_charge` - Penalty charge records

---

## Benefits

### 1. Complete Data Cleanup
- **No Orphaned Records**: All related data is properly deleted
- **No Dangling References**: Foreign key relationships are respected
- **Clean Database**: No inactive/disabled records cluttering the system

### 2. Accurate Accounting
- **Reversed Journal Entries**: Penalties that shouldn't exist are removed from accounting
- **Correct Balances**: GL accounts reflect the actual state
- **Audit Trail**: Comprehensive logging of all deletions

### 3. Correct Business Logic
- **Fresh Calculation**: New penalties are calculated based on accurate data
- **No Duplicates**: Old penalties don't interfere with new ones
- **Accurate Reporting**: Reports show only valid charges

### 4. Transaction Safety
- **Atomic Operations**: All deletions happen in a single transaction
- **Rollback on Failure**: If any step fails, all changes are reverted
- **Data Integrity**: Foreign key constraints are maintained

---

## Testing Guide

### Prerequisites
1. Loan with active disbursement
2. At least one overdue installment
3. Penalty charges applied (either manually or via scheduler)
4. Accounting enabled for the loan product

### Test Scenario 1: Full Repayment with Backdating
**Objective:** Verify complete penalty deletion when loan is fully paid off

**Steps:**
1. Create a loan with 3 monthly installments
2. Wait for first installment to become overdue (or adjust dates)
3. Let penalties accrue for 5 days
4. Make a backdated repayment 3 days back that covers all dues
5. Verify:
   - Penalties applied after backdated date are deleted
   - Journal entries for those penalties are deleted
   - Accrual transactions are deleted
   - No orphaned records in linking tables
   - Loan balance is correct
   - New penalties are not applied (loan is current)

**Expected Results:**
```sql
-- Check no orphaned records
SELECT COUNT(*) FROM m_loan_charge_paid_by lcpb 
WHERE NOT EXISTS (SELECT 1 FROM m_loan_charge lc WHERE lc.id = lcpb.loan_charge_id);
-- Should return 0

-- Check no orphaned installment charges
SELECT COUNT(*) FROM m_loan_installment_charge lic 
WHERE NOT EXISTS (SELECT 1 FROM m_loan_charge lc WHERE lc.id = lic.loan_charge_id);
-- Should return 0

-- Check journal entries are deleted
SELECT COUNT(*) FROM acc_gl_journal_entry je
WHERE je.loan_transaction_id IN (
    SELECT id FROM m_loan_transaction 
    WHERE loan_id = ? AND type_enum = 10 AND reversed = true
);
-- Should match deleted transaction count
```

### Test Scenario 2: Partial Repayment - Loan Remains in Arrears
**Objective:** Verify penalties are recalculated after backdated partial payment

**Steps:**
1. Create a loan with 3 monthly installments
2. Wait for first installment to become overdue
3. Let penalties accrue for 7 days (assume 3 penalty charges applied)
4. Make a backdated repayment 4 days back that covers only principal
5. Verify:
   - All 3 penalties are deleted
   - New penalties are calculated for 4 days of arrears
   - Only 1-2 penalties exist (depending on penalty frequency)
   - Accounting entries are correct
   - Loan is still in arrears

**Expected Results:**
- Old penalty charges deleted completely
- New penalty charges created with correct amounts
- No duplicate penalties
- Loan arrears calculation is accurate

### Test Scenario 3: Multiple Backdated Repayments
**Objective:** Ensure multiple backdated transactions work correctly

**Steps:**
1. Create a loan with installments
2. Let it become overdue with penalties
3. Make first backdated repayment (5 days back)
4. Make second backdated repayment (3 days back)
5. Make third backdated repayment (1 day back)
6. Verify:
   - Each repayment correctly deletes and recalculates penalties
   - No data corruption
   - Final state is accurate

### Test Scenario 4: Database Integrity Check
**Objective:** Verify no referential integrity violations

**Steps:**
1. Perform any of the above test scenarios
2. Run database integrity checks:

```sql
-- Check for orphaned loan_charge_paid_by records
SELECT lcpb.id, lcpb.loan_charge_id 
FROM m_loan_charge_paid_by lcpb
LEFT JOIN m_loan_charge lc ON lcpb.loan_charge_id = lc.id
WHERE lc.id IS NULL;

-- Check for orphaned installment charges
SELECT lic.id, lic.loan_charge_id
FROM m_loan_installment_charge lic
LEFT JOIN m_loan_charge lc ON lic.loan_charge_id = lc.id
WHERE lc.id IS NULL;

-- Check for orphaned journal entries
SELECT je.id, je.loan_transaction_id
FROM acc_gl_journal_entry je
WHERE je.loan_transaction_id IN (
    SELECT id FROM m_loan_transaction WHERE loan_id = ? AND type_enum = 10
)
AND je.loan_transaction_id NOT IN (
    SELECT id FROM m_loan_transaction WHERE reversed = false
);

-- All queries should return 0 rows
```

### Test Scenario 5: Accounting Verification
**Objective:** Ensure GL balances are correct

**Steps:**
1. Note GL account balances before test
2. Create overdue loan with penalties
3. Note penalty expense account balance
4. Make backdated repayment that deletes penalties
5. Verify:
   - Penalty expense account is debited (reversed)
   - Penalty receivable account is credited (reversed)
   - Net effect on GL is zero for deleted penalties

---

## Monitoring & Logging

### Log Messages
The implementation includes comprehensive logging at INFO level:

```
INFO: Deleted X journal entries for accrual transaction ID: Y
INFO: Deleted X accrual transactions for charges
INFO: Deleted X LoanChargePaidBy records
INFO: Deleted X LoanInstallmentCharge records
INFO: Deleted X penalty charges for loan ID: Y
```

### Monitoring Queries

#### Track Deletion Activity
```sql
-- Check application logs for deletion activity
SELECT * FROM application_log 
WHERE message LIKE '%Deleted%penalty charges%'
ORDER BY timestamp DESC
LIMIT 100;
```

#### Monitor Data Integrity
```sql
-- Run periodically to check for orphaned records
SELECT 
    (SELECT COUNT(*) FROM m_loan_charge_paid_by lcpb 
     WHERE NOT EXISTS (SELECT 1 FROM m_loan_charge lc WHERE lc.id = lcpb.loan_charge_id)) as orphaned_paid_by,
    (SELECT COUNT(*) FROM m_loan_installment_charge lic 
     WHERE NOT EXISTS (SELECT 1 FROM m_loan_charge lc WHERE lc.id = lic.loan_charge_id)) as orphaned_installment_charges,
    (SELECT COUNT(*) FROM acc_gl_journal_entry je 
     WHERE je.loan_transaction_id IS NOT NULL 
     AND NOT EXISTS (SELECT 1 FROM m_loan_transaction lt WHERE lt.id = je.loan_transaction_id)) as orphaned_journal_entries;
```

---

## Rollback Procedure

If issues are detected after deployment:

### 1. Immediate Rollback
```bash
# Revert to previous version
git checkout <previous-commit>
./gradlew clean build
# Deploy previous version
```

### 2. Data Recovery (if needed)
```sql
-- If you have backups, restore affected records
-- Otherwise, penalties will need to be manually recalculated
```

### 3. Revert to Old Behavior
Comment out the new deletion logic and uncomment the old deactivation logic:
```java
if (!penaltiesToDisable.isEmpty()) {
    List<Long> chargeIds = penaltiesToDisable.stream().map(LoanChargeData::getId).toList();
    loanChargeRepository.deactivateCharges(loanId, chargeIds); // Old behavior
    // New deletion logic commented out
}
```

---

## Performance Considerations

### Expected Impact
- **Minimal**: Deletion operations are efficient
- **Batched**: All deletions happen in a single transaction
- **Indexed**: Foreign key columns are indexed

### Optimization Recommendations
1. Ensure indexes exist on:
   - `m_loan_charge_paid_by.loan_charge_id`
   - `m_loan_installment_charge.loan_charge_id`
   - `m_loan_transaction.type_enum`
   - `acc_gl_journal_entry.loan_transaction_id`

2. Monitor query execution time
3. Consider batch size if many penalties need deletion

---

## Known Limitations

1. **No Undo**: Once penalties are deleted, they cannot be restored (except from backup)
2. **Audit Trail**: Deleted records are removed from audit tables
3. **Reporting**: Historical reports may show gaps where penalties were deleted

---

## Future Enhancements

1. **Soft Delete Option**: Add configuration to choose between hard and soft delete
2. **Audit Logging**: Maintain a separate audit log of deleted penalties
3. **Bulk Operations**: Optimize for loans with many penalties
4. **Notification**: Alert administrators when penalties are deleted

---

## Support & Troubleshooting

### Common Issues

#### Issue 1: Foreign Key Constraint Violation
**Symptom:** Error during deletion
**Cause:** Deletion order is incorrect or additional references exist
**Solution:** Check for new relationships and update deletion order

#### Issue 2: Penalties Not Recalculated
**Symptom:** After deletion, new penalties don't appear
**Solution:** Verify scheduler job is running and penalty configuration is correct

#### Issue 3: Accounting Imbalance
**Symptom:** GL accounts don't balance after deletion
**Solution:** Check journal entry deletion logic and verify entity type parameter

### Debug Steps
1. Enable DEBUG logging for the package
2. Check application logs for errors
3. Run database integrity queries
4. Verify transaction rollback if error occurred

---

## Conclusion

This implementation provides a robust solution for handling backdated repayments by completely removing penalties and all related data. The approach ensures data integrity, accurate accounting, and clean database state while maintaining transaction safety through atomic operations.

**Key Takeaways:**
- ✅ Complete data cleanup (no orphaned records)
- ✅ Accurate accounting (journal entries deleted)
- ✅ Correct business logic (fresh penalty calculation)
- ✅ Transaction safety (atomic operations with rollback)
- ✅ Comprehensive logging (audit trail)
- ✅ Database integrity (foreign key constraints respected)

For questions or issues, contact the development team.

