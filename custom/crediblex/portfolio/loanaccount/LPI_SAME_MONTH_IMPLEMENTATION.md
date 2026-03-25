# CredX LPI Same-Month Implementation - Summary

## Overview
This implementation ensures that Late Payment Interest (LPI) charges are assigned to the same month's installment row (the one that became overdue) instead of the next month's row. This is achieved by extending community transaction processors in the custom folder.

## Changes Made

### 1. Community Code Changes (Minimal)
**File:** `fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/domain/transactionprocessor/AbstractLoanRepaymentScheduleTransactionProcessor.java`

Added a factory method that allows subclasses to provide custom wrapper implementations:
```java
protected LoanRepaymentScheduleProcessingWrapper createLoanRepaymentScheduleProcessingWrapper(
        List<LoanRepaymentScheduleInstallment> installments) {
    return new LoanRepaymentScheduleProcessingWrapper();
}
```

This method is called during `reprocessLoanTransactions()` instead of directly instantiating the wrapper.

### 2. Custom CredX Transaction Processors Created

Created 8 custom transaction processor classes in `custom/crediblex/portfolio/loanaccount/src/main/java/com/crediblex/fineract/portfolio/loanaccount/domain/transactionprocessor/impl/`:

1. **CredXInterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor**
2. **CredXPrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor**
3. **CredXEarlyPaymentLoanRepaymentScheduleTransactionProcessor**
4. **CredXCreocoreLoanRepaymentScheduleTransactionProcessor**
5. **CredXDuePenFeeIntPriInAdvancePriPenFeeIntLoanRepaymentScheduleTransactionProcessor**
6. **CredXDuePenIntPriFeeInAdvancePenIntPriFeeLoanRepaymentScheduleTransactionProcessor**
7. **CredXHeavensFamilyLoanRepaymentScheduleTransactionProcessor**
8. **CredXRBILoanRepaymentScheduleTransactionProcessor**

Each class:
- Extends the corresponding community transaction processor
- Uses `@Order(HIGHEST_PRECEDENCE)` to ensure Spring picks them up first
- Overrides `createLoanRepaymentScheduleProcessingWrapper()` to conditionally return `CredXLoanRepaymentScheduleProcessingWrapper`
- Checks the loan's disbursement date against `LpiSameMonthProperties` configuration to determine which wrapper to use

### 3. Existing Custom Components (Already Present)

**CredXLoanRepaymentScheduleProcessingWrapper**
- Custom wrapper that implements same-month LPI assignment logic
- Overrides the `reprocess()` method to use `cumulativePenaltyChargesDueWithinCredX()`
- Assigns overdue charges to the linked installment (same month) instead of the period containing the charge due date

**LpiSameMonthProperties**
- Configuration class that determines which loans should use the new behavior
- Checks if a loan's disbursement date is on or after the configured cutoff date

## How It Works

1. When a transaction processor is needed, Spring's dependency injection looks for beans
2. Due to `@Order(HIGHEST_PRECEDENCE)`, CredX custom processors are selected first
3. During loan transaction reprocessing, the processor calls `createLoanRepaymentScheduleProcessingWrapper()`
4. The custom processor checks if the loan qualifies for same-month LPI (based on disbursement date)
5. If qualified, it returns `CredXLoanRepaymentScheduleProcessingWrapper`, otherwise the standard wrapper
6. The wrapper's `reprocess()` method assigns charges to the appropriate installment

## Benefits

1. **No Community Code Impact**: Only one minimal factory method added to community code
2. **Backward Compatible**: Existing loans (disbursed before cutoff date) continue with old behavior
3. **Easy to Maintain**: When pulling updates from community, only the factory method needs to be preserved
4. **Extensible**: New transaction processors can be easily added following the same pattern
5. **Configuration-Driven**: Behavior can be controlled via `LpiSameMonthProperties` configuration

## Testing Considerations

- Test loans disbursed before and after the cutoff date
- Verify LPI appears on correct installment row
- Test all transaction processor strategies
- Verify backward compatibility with existing loans
- Test schedule recalculation scenarios

## Future Maintenance

When pulling updates from the Apache Fineract community repository:
1. Ensure the `createLoanRepaymentScheduleProcessingWrapper()` factory method remains in `AbstractLoanRepaymentScheduleTransactionProcessor`
2. Ensure the call to this factory method remains in `reprocessLoanTransactions()`
3. All custom CredX processors in the custom folder will continue to work without modification
