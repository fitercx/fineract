# CredibleX Fineract Loc Module

This module contains the Line of Credit (Loc) functionality for the CredibleX Fineract platform.

## Module Structure

```
custom/crediblex/loc/
├── build.gradle              # Module build configuration
├── dependencies.gradle        # Module dependencies
├── README.md                 # This file
└── src/
    └── main/
        └── java/
            └── com/
                └── crediblex/
                    └── fineract/
                        └── loc/
                            ├── api/
                            │   └── LineOfCreditApiResource.java
                            ├── data/
                            │   ├── LineOfCreditData.java
                            │   └── LineOfCreditTransactionData.java
                            ├── domain/
                            │   ├── LineOfCredit.java
                            │   └── LineOfCreditTransaction.java
                            ├── repository/
                            │   ├── LineOfCreditRepository.java
                            │   └── LineOfCreditTransactionRepository.java
                            └── service/
                                ├── LineOfCreditDataValidator.java
                                ├── LineOfCreditReadPlatformService.java
                                └── LineOfCreditWritePlatformServiceImpl.java
```

## Database Schema

### m_line_of_credit Table
- `id` - Primary key (BIGINT, auto-increment)
- `client_id` - Foreign key to m_client (BIGINT, not null)
- `name` - Line of credit name (VARCHAR(100), not null)
- `product_type` - Type of credit product (VARCHAR(50), not null)
- `maximum_amount` - Maximum credit limit (DECIMAL(19,6), not null)
- `available_balance` - Current available balance (DECIMAL(19,6), not null)
- `consumed_amount` - Amount already used (DECIMAL(19,6), not null)
- `activation_status` - Status enum (VARCHAR(20), not null)
- `start_date` - Start date (DATE, not null)
- `end_date` - End date (DATE, not null)
- Audit fields (created_on_utc, created_by, last_modified_on_utc, last_modified_by)

### m_line_of_credit_transactions Table
- `id` - Primary key (BIGINT, auto-increment)
- `line_of_credit_id` - Foreign key to m_line_of_credit (BIGINT, not null)
- `transaction_type` - Type of transaction (VARCHAR(50), not null)
- `amount` - Transaction amount (DECIMAL(19,6), not null)
- `balance_before` - Balance before transaction (DECIMAL(19,6), not null)
- `balance_after` - Balance after transaction (DECIMAL(19,6), not null)
- `transaction_date` - Transaction timestamp (DATETIME/TIMESTAMP, not null)
- `reference_number` - Reference number (VARCHAR(100))
- `description` - Transaction description (VARCHAR(500))
- Audit fields (created_on_utc, created_by, last_modified_on_utc, last_modified_by)

## Foreign Key Constraints
- `FK_line_of_credit_client` - Links client_id to m_client.id
- `FK_line_of_credit_transactions_line_of_credit` - Links line_of_credit_id to m_line_of_credit.id
- Audit field constraints linking to m_appuser.id

## Indexes
- `idx_line_of_credit_client_id` - On client_id for performance
- `idx_line_of_credit_activation_status` - On activation_status for filtering
- `idx_line_of_credit_transactions_line_of_credit_id` - On line_of_credit_id for performance
- `idx_line_of_credit_transactions_transaction_date` - On transaction_date for date range queries
- `idx_line_of_credit_transactions_transaction_type` - On transaction_type for filtering

## Features

### Complete CRUD Operations
- **Create** - Create new line of credit for clients
- **Read** - Retrieve line of credit details and lists
- **Update** - Modify line of credit parameters
- **Delete** - Remove line of credit records

### Transaction Management
- **Activate/Deactivate** - Change line of credit status
- **Draw Amount** - Withdraw funds from line of credit
- **Repay Amount** - Return funds to line of credit
- **Transaction History** - Complete audit trail of all operations

### API Endpoints

#### Line of Credit Management
- `GET /v1/lineofcredit` - List all line of credits (with optional clientId filter)
- `GET /v1/lineofcredit/template` - Get template for creating line of credit
- `GET /v1/lineofcredit/{id}` - Get specific line of credit details
- `POST /v1/lineofcredit` - Create new line of credit
- `PUT /v1/lineofcredit/{id}` - Update line of credit
- `DELETE /v1/lineofcredit/{id}` - Delete line of credit

#### Line of Credit Operations
- `POST /v1/lineofcredit/{id}` - Activate line of credit
- `POST /v1/lineofcredit/{id}/draw` - Draw amount from line of credit
- `POST /v1/lineofcredit/{id}/repay` - Repay amount to line of credit

#### Transaction History
- `GET /v1/lineofcredit/{id}/transactions` - Get transaction history for line of credit

## Dependencies

The module depends on:
- `fineract-core`
- `fineract-provider`
- `fineract-client`
- Spring Boot Data JPA
- Jakarta WS RS API
- Gson for JSON processing
- Apache Commons Lang
- Swagger annotations

## Database Migration

The database migration scripts are located in:
```
custom/crediblex/infrastructure/starter/src/main/resources/db/custom-changelog/0010-create-line-of-credit-tables.xml
```

This migration creates both tables with proper foreign key constraints and indexes.

## Usage

This module is designed to be built independently as part of the CredibleX Fineract platform. It follows the same structure and conventions as other modules in the `custom/crediblex` directory.

## Building

To build this module independently:

```bash
cd custom/crediblex/loc
./gradlew build
```

Or from the root directory:

```bash
./gradlew :custom:crediblex:loc:build
```

## Example API Usage

### Create Line of Credit
```json
POST /v1/lineofcredit
{
  "clientId": 1,
  "name": "Business Credit Line",
  "productType": "BUSINESS",
  "maximumAmount": 50000.00,
  "startDate": "2024-01-01",
  "endDate": "2024-12-31"
}
```

### Draw Amount
```json
POST /v1/lineofcredit/1/draw
{
  "amount": 10000.00,
  "referenceNumber": "DRAW-001",
  "description": "Business expansion funding"
}
```

### Repay Amount
```json
POST /v1/lineofcredit/1/repay
{
  "amount": 5000.00,
  "referenceNumber": "REPAY-001",
  "description": "Monthly repayment"
}
```

## Transaction Logging

Every operation on the line of credit is automatically logged in the `m_line_of_credit_transactions` table, providing a complete audit trail of:
- Balance changes
- Transaction types
- Amounts involved
- Before and after balances
- Reference numbers and descriptions
- Timestamps and user information 