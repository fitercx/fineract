# Fineract Odoo Integration Module

This module provides integration between Apache Fineract and Odoo ERP system for posting journal entries.

## Features

- **Journal Entry Posting**: Post journal entries from Fineract to Odoo
- **Connection Testing**: Test connectivity to Odoo server
- **Error Handling**: Comprehensive error handling with detailed logging
- **RESTful API**: Simple REST API for journal entry operations

## Quick Start

### 1. Configuration

Add to your `application.yml`:

```yaml
odoo:
  url: "http://your-odoo-server:8069"
  database: "your_database_name"
  username: "your_username"
  password: "your_password"
  enabled: true
```

### 2. Test Connection

```bash
curl -X GET http://localhost:8443/fineract-provider/api/v1/odoo/test
```

### 3. Post Journal Entry

```bash
curl -X POST http://localhost:8443/fineract-provider/api/v1/odoo/journal-entry \
  -H "Content-Type: application/json" \
  -d '{
    "reference": "LOAN-001-DISBURSEMENT",
    "date": "2024-01-15",
    "description": "Loan disbursement for client John Doe",
    "amount": 10000.00,
    "debitAccount": "1200",
    "creditAccount": "1000"
  }'
```

## API Endpoints

- `GET /api/v1/odoo/test` - Test Odoo connection
- `POST /api/v1/odoo/journal-entry` - Post journal entry to Odoo

## Usage Example

```java
@Autowired
private OdooJournalService odooJournalService;

public void postLoanDisbursement(Long loanId, BigDecimal amount) {
    Map<String, Object> result = odooJournalService.postJournalEntry(
        "LOAN-" + loanId + "-DISBURSEMENT",
        LocalDate.now(),
        "Loan disbursement",
        amount,
        "1200", // Loans Receivable
        "1000"  // Cash Account
    );

    if ((Boolean) result.get("success")) {
        log.info("Journal entry posted successfully: {}", result.get("odoo_journal_entry_id"));
    } else {
        log.error("Failed to post journal entry: {}", result.get("error"));
    }
}
```

## Architecture

The integration follows a clean architecture pattern with the following components:

```
odoo/
├── client/           # HTTP client for Odoo API communication
├── config/           # Configuration classes and properties
├── controller/       # REST API endpoints
├── exception/        # Custom exception hierarchy
└── service/          # Business logic and synchronization services
```

## Configuration

### 1. Add Configuration Properties

Add the following properties to your `application.yml`:

```yaml
odoo:
  url: "http://your-odoo-server:8069"
  database: "your_database_name"
  username: "your_username"
  password: "your_password"
  timeout: 30000
  max-retries: 3
  retry-delay: 1000
  enabled: true
```

### 2. Environment Variables (Recommended for Production)

```bash
export ODOO_URL=http://your-odoo-server:8069
export ODOO_DATABASE=your_database_name
export ODOO_USERNAME=your_username
export ODOO_PASSWORD=your_password
```

## API Endpoints

### Testing Endpoints

- `GET /api/v1/odoo/test/connection` - Test Odoo connection
- `GET /api/v1/odoo/test/integration` - Run comprehensive integration test
- `GET /api/v1/odoo/system/info` - Get system information

### Client Management

- `POST /api/v1/odoo/clients/sync` - Sync client to Odoo
- `DELETE /api/v1/odoo/clients/{clientId}` - Delete client from Odoo

### Loan Management

- `POST /api/v1/odoo/loans/disbursement` - Sync loan disbursement
- `POST /api/v1/odoo/loans/repayment` - Sync loan repayment
- `GET /api/v1/odoo/loans/{loanId}/transactions` - Get loan transaction history
- `DELETE /api/v1/odoo/transactions/{transactionId}` - Cancel transaction

## Usage Examples

### 1. Test Connection

```bash
curl -X GET http://localhost:8443/fineract-provider/api/v1/odoo/test/connection
```

### 2. Sync Client

```bash
curl -X POST http://localhost:8443/fineract-provider/api/v1/odoo/clients/sync \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": 123,
    "firstName": "John",
    "lastName": "Doe",
    "mobileNo": "+1234567890",
    "email": "john.doe@example.com",
    "address": "123 Main Street"
  }'
```

### 3. Sync Loan Disbursement

```bash
curl -X POST http://localhost:8443/fineract-provider/api/v1/odoo/loans/disbursement \
  -H "Content-Type: application/json" \
  -d '{
    "loanId": 456,
    "transactionId": 789,
    "amount": 10000.00,
    "disbursementDate": "2024-01-15",
    "clientName": "John Doe",
    "loanProductName": "Personal Loan"
  }'
```

## Integration Flow

### Client Synchronization

1. Fineract creates/updates a client
2. Webhook triggers client sync to Odoo
3. Service checks if client exists in Odoo
4. Creates new partner or updates existing one
5. Returns synchronization result

### Loan Transaction Synchronization

1. Loan transaction occurs in Fineract (disbursement/repayment)
2. Webhook triggers transaction sync to Odoo
3. Service creates appropriate journal entry in Odoo
4. Proper accounting entries are made (debit/credit)
5. Returns synchronization result

## Odoo Setup Requirements

### 1. Required Odoo Modules

Ensure the following modules are installed in your Odoo instance:

- `account` - Accounting module (core)
- `base` - Base module (core)
- `contacts` - Contacts/Partners management

### 2. Custom Fields

The integration creates custom fields in Odoo to track Fineract IDs:

- `x_fineract_client_id` - Stores Fineract client ID
- `x_fineract_loan_id` - Stores Fineract loan ID
- `x_fineract_transaction_id` - Stores Fineract transaction ID

### 3. User Permissions

The Odoo user should have the following permissions:

- Read/Write access to `res.partner` (clients/customers)
- Read/Write access to `account.move` (journal entries)
- Read/Write access to `account.move.line` (journal entry lines)

## Error Handling

The integration includes comprehensive error handling:

- **Connection Errors**: Automatic retry with exponential backoff
- **Authentication Errors**: Clear error messages and logging
- **Data Validation Errors**: Detailed validation failure information
- **Timeout Handling**: Configurable timeout settings

## Monitoring and Logging

### Log Levels

- `DEBUG`: Detailed API communication logs
- `INFO`: Synchronization events and results
- `WARN`: Non-fatal issues and retries
- `ERROR`: Synchronization failures and exceptions

### Metrics

The integration provides metrics for monitoring:

- Sync success/failure rates
- Response times
- Error counts by type

## Testing

### Unit Tests

Run unit tests for the integration:

```bash
./gradlew :custom:crediblex:integration:odoo:test
```

### Integration Tests

The module includes built-in integration tests accessible via REST API:

```bash
# Run full integration test
curl -X GET http://localhost:8443/fineract-provider/api/v1/odoo/test/integration
```

## Troubleshooting

### Common Issues

1. **Connection Refused**

   - Check Odoo server URL and port
   - Verify network connectivity
   - Check firewall settings

2. **Authentication Failed**

   - Verify username and password
   - Check user permissions in Odoo
   - Ensure database name is correct

3. **Sync Failures**
   - Check Odoo logs for detailed error messages
   - Verify required modules are installed
   - Check custom field creation permissions

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.crediblex.fineract.integration.odoo: DEBUG
```

## Security Considerations

1. **Credentials**: Store Odoo credentials securely using environment variables
2. **Network**: Use HTTPS for production deployments
3. **Authentication**: Consider implementing OAuth2 for enhanced security
4. **Data Validation**: All data is validated before sending to Odoo

## Contributing

When contributing to this module:

1. Follow the existing code patterns
2. Add appropriate unit tests
3. Update documentation
4. Ensure all lint checks pass
5. Test against a real Odoo instance

## Support

For issues and questions:

1. Check the troubleshooting section
2. Review Fineract and Odoo documentation
3. Check logs for detailed error information
4. Open an issue in the project repository
