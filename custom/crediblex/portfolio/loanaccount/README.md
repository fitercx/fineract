# CredibleX Loan Product Customizations

This module contains customizations for loan products and loans in the CredibleX implementation of Fineract.

## New Fields

### is_loc_enable (Loan Product)

A boolean field that indicates whether a loan product supports Line of Credit (LOC) functionality.

### line_of_credit_id (Loan)

A foreign key field that links a loan to a specific Line of Credit, allowing loans to be associated with credit lines.

**Automatic Handling**: The `line_of_credit_id` field is automatically processed by the custom services when included in API requests. No additional integration code is required - simply include the field in your JSON payload and it will be validated and stored appropriately.

## Database Schema

### Table Relationships

```
m_product_loan (Loan Products)
├── is_loc_enable: BOOLEAN (indicates if product supports LOC)

m_loan (Loans)
├── line_of_credit_id: BIGINT (foreign key to m_line_of_credit.id)
└── FK_loan_line_of_credit → m_line_of_credit.id

m_line_of_credit (Line of Credit)
├── id: BIGINT (primary key)
├── client_id: BIGINT (foreign key to m_client.id)
└── ... (other LOC fields)
```

### Foreign Key Constraints

- `FK_loan_line_of_credit`: Links `m_loan.line_of_credit_id` to `m_line_of_credit.id`
  - `ON DELETE SET NULL`: If a line of credit is deleted, the loan's reference is set to NULL
  - `ON UPDATE CASCADE`: If a line of credit ID is updated, the loan reference is updated accordingly

#### Database Migration

The field is added via Liquibase migration:
- File: `custom/crediblex/infrastructure/starter/src/main/resources/db/custom-changelog/0011-add-is-loc-enable-to-product-loan.xml`
- Column: `is_loc_enable` (BOOLEAN, NOT NULL, DEFAULT FALSE)

#### Database Migration

The field is added via Liquibase migration:
- File: `custom/crediblex/infrastructure/starter/src/main/resources/db/custom-changelog/0012-add-line-of-credit-id-to-loan.xml`
- Column: `line_of_credit_id` (BIGINT, NULLABLE)
- Foreign Key: `FK_loan_line_of_credit` → `m_line_of_credit.id`
- Index: `idx_loan_line_of_credit_id` for performance
- Constraints: `ON DELETE SET NULL`, `ON UPDATE CASCADE`

#### Usage

When creating or updating a loan product via the API, you can include the `isLocEnable` field:

```json
{
  "name": "Sample Loan Product",
  "shortName": "SLP",
  "currencyCode": "USD",
  "digitsAfterDecimal": 2,
  "inMultiplesOf": 1,
  "principal": 10000,
  "numberOfRepayments": 12,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 10,
  "interestRateFrequencyType": 2,
  "amortizationType": 1,
  "interestType": 1,
  "interestCalculationPeriodType": 1,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "accountingRule": 1,
  "isInterestRecalculationEnabled": false,
  "daysInYearType": 1,
  "daysInMonthType": 1,
  "isLocEnable": true
}
```

When creating or updating a loan via the API, you can include the `lineOfCreditId` field:

```json
{
  "clientId": 1,
  "productId": 1,
  "principal": 5000,
  "loanTermFrequency": 12,
  "loanTermFrequencyType": 2,
  "loanType": "individual",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "submittedOnDate": "01 January 2024",
  "lineOfCreditId": 123
}
```

#### Implementation

The fields are handled by:
- `CustomLoanProductService`: Provides methods to update and retrieve the `is_loc_enable` field
- `CustomLoanLineOfCreditService`: Provides methods to manage loan-line of credit relationships
- `CustomLoanApplicationWritePlatformServiceJpaRepositoryImpl`: Handles `line_of_credit_id` during loan creation and updates
- `CustomLoanWritePlatformServiceJpaRepositoryImpl`: Handles `line_of_credit_id` during loan operations (disbursement, foreclosure, etc.)
- Database operations use direct JDBC to avoid conflicts with the core Fineract entity
- Foreign key relationships are properly managed with appropriate constraints

#### Integration

To integrate these fields into your application:

1. **For Loan Product Creation**: Call `CustomLoanProductService.updateLocEnableField()` after the main loan product creation
2. **For Loan Product Updates**: Call `CustomLoanProductService.updateLocEnableField()` after the main loan product update
3. **For Loan Product Reading**: Call `CustomLoanProductService.getLocEnableField()` to retrieve the field value
4. **For Loan Creation/Updates**: The `line_of_credit_id` field is automatically handled by the custom services when included in the API request
5. **For Loan Operations**: The `line_of_credit_id` field is automatically handled during disbursement, foreclosure, and other loan operations
6. **For Loan-Line of Credit Relationship Management**: Use `CustomLoanLineOfCreditService` methods for linking, unlinking, and querying relationships

#### Example Integration

```java
@Service
public class LoanProductService {
    
    private final CustomLoanProductService customLoanProductService;
    
    public void createLoanProduct(JsonCommand command) {
        // Standard loan product creation logic
        CommandProcessingResult result = standardCreateLoanProduct(command);
        
        // Update the custom field
        if (result.getResourceId() != null) {
            customLoanProductService.updateLocEnableField(result.getResourceId(), command);
        }
    }
}

@Service
public class LoanService {
    
    private final CustomLoanLineOfCreditService customLoanLineOfCreditService;
    
    public void createLoan(JsonCommand command) {
        // Standard loan creation logic - line_of_credit_id is automatically handled
        CommandProcessingResult result = standardCreateLoan(command);
    }
    
    public Long getLineOfCreditForLoan(Long loanId) {
        return customLoanLineOfCreditService.getLineOfCreditIdForLoan(loanId);
    }
    
    public List<Long> getLoansForLineOfCredit(Long lineOfCreditId) {
        return customLoanLineOfCreditService.getLoansForLineOfCredit(lineOfCreditId);
    }
    
    public void updateLoanLineOfCredit(Long loanId, Long lineOfCreditId) {
        // The line_of_credit_id field is automatically handled by the custom services
        // Just include it in the API request and it will be processed
    }
}
``` 