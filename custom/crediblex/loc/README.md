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
                            ├── LocModule.java                    # Placeholder class
                            ├── domain/
                            │   ├── LineOfCredit.java             # Line of Credit entity
                            │   └── LineOfCreditTransaction.java  # Transaction entity
                            └── repository/
                                ├── LineOfCreditRepository.java           # Repository interface
                                └── LineOfCreditTransactionRepository.java # Transaction repository
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

## Dependencies

The module depends on:
- `fineract-core`
- `fineract-provider`
- `fineract-client`
- Spring Boot Data JPA
- Jakarta WS RS API

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