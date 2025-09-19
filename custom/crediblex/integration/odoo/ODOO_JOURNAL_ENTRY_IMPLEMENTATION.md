# Fineract-Odoo Journal Entries Integration

## Overview

This module provides seamless integration between Apache Fineract and Odoo ERP system for automated journal entries synchronization. The integration ensures that all accounting transactions recorded in Fineract are automatically synchronized to Odoo, maintaining consistency between both systems.

## Table of Contents

1. [Background & Problem Statement](#background--problem-statement)
2. [Solution Architecture](#solution-architecture)
3. [Event-Driven Flow](#event-driven-flow)
4. [Implementation Details](#implementation-details)
5. [Database Schema](#database-schema)
6. [Configuration](#configuration)
7. [Usage](#usage)
8. [Monitoring & Troubleshooting](#monitoring--troubleshooting)
9. [File Structure](#file-structure)

## Background & Problem Statement

### Before Integration

In the original Fineract system:

- Journal entries were created automatically for loan transactions (disbursements, repayments, etc.)
- These entries were stored in the `acc_gl_journal_entry` table
- No mechanism existed to track which entries were synced to external systems
- Manual intervention was required to synchronize accounting data to external ERPs

### Problems Addressed

1. **Data Synchronization**: Automatic sync of journal entries to Odoo
2. **Tracking**: Monitor which entries are successfully synced vs. failed
3. **Reliability**: Retry mechanism for failed syncs
4. **Auditability**: Complete audit trail of sync operations
5. **Performance**: Batch processing of pending entries

## Solution Architecture

### Core Components

1. **Event Listener**: Captures journal entry creation events
2. **Tracking System**: Records sync status for each journal entry
3. **Scheduled Job**: Processes pending entries in batches
4. **Error Handling**: Tracks and retries failed synchronizations

### Integration Points

- **Fineract Event System**: `LoanJournalEntryCreatedBusinessEvent`
- **Spring Batch Jobs**: Scheduled processing
- **Odoo REST API**: External system integration

## Event-Driven Flow

### 1. Journal Entry Creation (Existing Fineract Flow)

```
Loan Transaction → Accounting Processor → Journal Entry Creation → Database Storage
```

**Key Files Involved:**

- `CustomCashBasedAccountingProcessorForLoan.java` - Processes loan accounting transactions
- `AccountingProcessorHelper.java` - Core journal entry persistence logic

**Event Trigger Location:**

```java
// File: AccountingProcessorHelper.java
// Method: persistJournalEntry()
// Line: ~1284

public JournalEntry persistJournalEntry(JournalEntry journalEntry) {
    boolean isNew = journalEntry.isNew();
    JournalEntry savedJournalEntry = this.glJournalEntryRepository.saveAndFlush(journalEntry);
    if (isNew && journalEntry.getLoanTransactionId() != null) {
        businessEventNotifierService.notifyPostBusinessEvent(new LoanJournalEntryCreatedBusinessEvent(savedJournalEntry));
    }
    return savedJournalEntry;
}
```

### 2. Event Capture & Tracking (Our Integration)

```
Journal Entry Event → Event Listener → Create Tracking Record → Pending Sync
```

**Implementation:**

```java
// File: JournalEntryOdooTrackingService.java
// Listens to: LoanJournalEntryCreatedBusinessEvent

@PostConstruct
public void addListeners() {
    businessEventNotifierService.addPostBusinessEventListener(LoanJournalEntryCreatedBusinessEvent.class, event -> {
        JournalEntry journalEntry = event.get();
        createTrackingRecord(journalEntry);
    });
}
```

### 3. Scheduled Synchronization

```
Scheduled Job → Fetch Pending Entries → Sync to Odoo → Update Status
```

**Implementation:**

```java
// File: OdooJournalEntriesSyncJobTasklet.java
// Schedule: Every 1 hour (0 0 * * * ?)

List<JournalEntryOdooSync> pendingEntries = journalEntryOdooSyncRepository.findPendingEntries();
for (JournalEntryOdooSync sync : pendingEntries) {
    // Sync to Odoo and update status
}
```

## Implementation Details

### 1. Event System Integration

**Event Class Used:**

- `LoanJournalEntryCreatedBusinessEvent` (Fineract core)
- Location: `fineract-loan/src/main/java/org/apache/fineract/infrastructure/event/business/domain/journalentry/`

**Event Characteristics:**

- Triggered automatically when journal entries are persisted
- Provides access to the complete `JournalEntry` object
- Fires for loan-related transactions only
- Implements `NoExternalEvent` (internal use only)

**Our Event Listener:**

```java
// File: custom/crediblex/integration/odoo/src/main/java/com/crediblex/fineract/integration/odoo/service/JournalEntryOdooTrackingService.java

@Service
@ConditionalOnProperty(name = "odoo.enabled", havingValue = "true")
public class JournalEntryOdooTrackingService {
    // Automatically creates tracking records for all journal entries
}
```

### 2. Database Tracking System

**New Table: `journal_entry_odoo_sync`**

| Column            | Type          | Description                  |
| ----------------- | ------------- | ---------------------------- |
| id                | bigint        | Primary key                  |
| journal_entry_id  | bigint        | FK to `acc_gl_journal_entry` |
| is_posted_to_odoo | boolean       | Sync status                  |
| created_at        | timestamp     | Record creation time         |
| posted_at         | timestamp     | Successful sync time         |
| odoo_move_id      | bigint        | Odoo move reference          |
| error_message     | varchar(1000) | Failure details              |

**Entity Class:**

```java
// File: custom/crediblex/integration/odoo/src/main/java/com/crediblex/fineract/integration/odoo/domain/JournalEntryOdooSync.java

@Entity
@Table(name = "journal_entry_odoo_sync")
public class JournalEntryOdooSync extends AbstractPersistableCustom {
    // Complete tracking entity with lifecycle methods
}
```

### 3. Repository & Data Access

**Repository Interface:**

```java
// File: custom/crediblex/integration/odoo/src/main/java/com/crediblex/fineract/integration/odoo/domain/JournalEntryOdooSyncRepository.java

@Repository
public interface JournalEntryOdooSyncRepository extends JpaRepository<JournalEntryOdooSync, Long> {
    List<JournalEntryOdooSync> findPendingEntries();
    List<JournalEntryOdooSync> findFailedEntries();
    long countUnpostedEntries();
}
```

### 4. Scheduled Job Implementation

**Job Configuration:**

```java
// File: custom/crediblex/integration/job/src/main/java/com/crediblex/fineract/integration/job/OdooJournalEntriesSyncJobConfiguration.java

@Configuration
public class OdooJournalEntriesSyncJobConfiguration {
    // Spring Batch job definition with 1-hour schedule
}
```

**Job Registration:**

```xml
<!-- File: custom/crediblex/infrastructure/starter/src/main/resources/db/custom-changelog/0025-add-odoo-journal-entries-sync-job.xml -->
<insert tableName="job">
    <column name="cron_expression" value="0 0 * * * ?"/>
    <column name="short_name" value="ODOJSYNC"/>
</insert>
```

## Database Schema

### Migration Files

1. **Job Registration:**

   ```
   custom/crediblex/infrastructure/starter/src/main/resources/db/custom-changelog/0025-add-odoo-journal-entries-sync-job.xml
   ```

2. **Tracking Table:**
   ```
   custom/crediblex/infrastructure/starter/src/main/resources/db/custom-changelog/0026-create-journal-entry-odoo-sync-table.xml
   ```

### Schema Diagram

```
acc_gl_journal_entry (Existing)
        │
        │ (1:1)
        ▼
journal_entry_odoo_sync (New)
        │
        │ (Tracks sync status)
        ▼
Odoo ERP System
```

## Configuration

### Application Properties

```properties
# File: fineract-provider/src/main/resources/application.properties

# Odoo Integration Configuration
odoo.enabled=true
odoo.url=${ODOO_URL:http://localhost:8069}
odoo.database=${ODOO_DATABASE:odoo}
odoo.username=${ODOO_USERNAME:admin}
odoo.password=${ODOO_PASSWORD:admin}
odoo.connection-timeout=${ODOO_CONNECTION_TIMEOUT:30000}
odoo.read-timeout=${ODOO_READ_TIMEOUT:60000}
odoo.max-retries=${ODOO_MAX_RETRIES:3}
odoo.retry-delay=${ODOO_RETRY_DELAY:1000}
```

### Environment Variables

```bash
ODOO_URL=http://your-odoo-instance:8069
ODOO_DATABASE=your_database
ODOO_USERNAME=your_username
ODOO_PASSWORD=your_password
```

### ⚠️ Important Configuration Notes

1. **Balancing Account Setup**: Update the `getBalancingAccountId()` method in `OdooJournalEntryService.java`:

   ```java
   private Integer getBalancingAccountId(JournalEntry fineractEntry) {
       // Replace 999999 with actual account ID from your Odoo instance
       return 999999; // This should be a valid suspense/clearing account
   }
   ```

2. **Account Mapping**: Ensure Fineract GL account codes match or can be mapped to Odoo account codes

3. **Journal Setup**: Verify that appropriate journals exist in Odoo:

   - General journal for miscellaneous entries
   - Specific journals for different transaction types if needed

4. **User Permissions**: Ensure the Odoo user has proper permissions:
   - Create and post accounting entries
   - Read account and journal information
   - Access to relevant accounting features

## Usage

### 1. Automatic Tracking

Once deployed, the system automatically:

- Captures all new journal entry creations
- Creates tracking records in `journal_entry_odoo_sync`
- Marks entries as pending for synchronization

### 2. Scheduled Synchronization

The job runs every hour and:

- Fetches all pending entries
- Attempts to sync each to Odoo
- Updates status (success/failure)
- Logs results for monitoring

### 3. Manual Operations

**Check Pending Entries:**

```sql
SELECT COUNT(*) FROM journal_entry_odoo_sync WHERE is_posted_to_odoo = false;
```

**View Failed Entries:**

```sql
SELECT * FROM journal_entry_odoo_sync
WHERE is_posted_to_odoo = false AND error_message IS NOT NULL;
```

**Retry Failed Entries:**

```sql
UPDATE journal_entry_odoo_sync
SET error_message = NULL
WHERE is_posted_to_odoo = false AND error_message IS NOT NULL;
```

## Monitoring & Troubleshooting

### Log Messages

```
INFO  - Created Odoo sync tracking record for journal entry ID: 12345
INFO  - Found 25 pending journal entries to sync to Odoo
INFO  - Successfully posted journal entry 12345 to Odoo with move ID: 67890
WARN  - Marked journal entry 12346 as failed to post to Odoo: Connection timeout
```

### Key Metrics

- **Pending Entries**: `journalEntryOdooSyncRepository.countUnpostedEntries()`
- **Success Rate**: Ratio of successful vs. total sync attempts
- **Error Patterns**: Common failure reasons in `error_message`

### Common Issues

1. **Odoo Connection Failures**: Check network connectivity and credentials
2. **Data Validation Errors**: Verify journal entry data format
3. **Performance Issues**: Monitor batch size and processing time

## File Structure

```
custom/crediblex/
├── integration/
│   ├── odoo/src/main/java/com/crediblex/fineract/integration/odoo/
│   │   ├── client/
│   │   │   └── OdooApiClient.java                           # Enhanced Odoo API client
│   │   ├── domain/
│   │   │   ├── JournalEntryOdooSync.java                    # Tracking entity
│   │   │   └── JournalEntryOdooSyncRepository.java          # Data access
│   │   ├── service/
│   │   │   ├── JournalEntryOdooTrackingService.java         # Event listener
│   │   │   ├── OdooIntegrationReadPlatformService.java      # Service interface (enhanced)
│   │   │   ├── OdooIntegrationReadPlatformServiceImpl.java  # Service implementation (enhanced)
│   │   │   └── OdooJournalEntryService.java                 # Journal entry posting service
│   │   ├── config/
│   │   │   └── OdooProperties.java                          # Configuration properties
│   │   ├── exception/
│   │   │   └── [exception classes]                          # Odoo-specific exceptions
│   │   └── api/
│   │       └── OdooIntegrationApiResource.java              # REST endpoints
│   └── job/src/main/java/com/crediblex/fineract/integration/job/
│       ├── OdooJournalEntriesSyncJobTasklet.java           # Sync job implementation
│       └── OdooJournalEntriesSyncJobConfiguration.java     # Job configuration
├── infrastructure/starter/src/main/resources/db/custom-changelog/
│   ├── 0025-add-odoo-journal-entries-sync-job.xml         # Job registration
│   └── 0026-create-journal-entry-odoo-sync-table.xml      # Tracking table
└── accounting/journalentry/src/main/java/com/crediblex/fineract/accounting/journalentry/service/
    └── CustomCashBasedAccountingProcessorForLoan.java     # Custom accounting processor
```

### Core Fineract Files (Referenced)

```
fineract-provider/src/main/java/org/apache/fineract/
├── accounting/journalentry/service/
│   └── AccountingProcessorHelper.java                      # Event trigger point
└── infrastructure/event/business/domain/journalentry/
    └── LoanJournalEntryCreatedBusinessEvent.java          # Event class

fineract-loan/src/main/java/org/apache/fineract/
└── infrastructure/event/business/domain/journalentry/
    └── LoanJournalEntryCreatedBusinessEvent.java          # Event implementation
```

## Benefits

### 1. Reliability

- **Automatic Tracking**: No manual intervention required
- **Error Recovery**: Failed syncs are tracked and can be retried
- **Data Integrity**: Ensures all journal entries are accounted for

### 2. Performance

- **Batch Processing**: Efficient handling of multiple entries
- **Asynchronous**: Non-blocking journal entry creation
- **Configurable**: Adjustable sync frequency and batch sizes

### 3. Auditability

- **Complete Trail**: Full history of sync operations
- **Status Tracking**: Real-time visibility into sync status
- **Error Details**: Detailed failure information for troubleshooting

### 4. Maintainability

- **Non-Intrusive**: Minimal changes to core Fineract code
- **Configurable**: Easy to enable/disable via properties
- **Extensible**: Easy to add new sync targets or modify logic

## Next Steps

### ✅ Completed Implementation

1. **✅ Event-Driven Tracking**: Automatic capture of journal entry creation events
2. **✅ Database Schema**: Tracking table with proper relationships and indexes
3. **✅ Scheduled Jobs**: Hourly batch processing of pending entries
4. **✅ Odoo API Integration**: Complete Odoo service implementation with:
   - Authentication and session management
   - Account mapping between Fineract and Odoo
   - Journal entry posting with proper debit/credit balance
   - Account move creation and posting (draft → posted)
   - Error handling and retry mechanisms

### 🔧 Configuration Required

1. **Account Mapping**: Update `getBalancingAccountId()` method in `OdooJournalEntryService` with actual Odoo account IDs
2. **Journal Configuration**: Ensure appropriate journals exist in Odoo for different transaction types
3. **Environment Setup**: Configure Odoo connection properties in application.properties

### 🚀 Future Enhancements

1. **Advanced Account Mapping**: Implement business logic for automatic account mapping based on:
   - Transaction types (loan disbursement, repayment, interest, fees)
   - Office/branch specific accounts
   - Product-specific accounts
2. **Retry Logic**: Implement exponential backoff for failed syncs
3. **Monitoring Dashboard**: Create UI for tracking sync status and performance metrics
4. **Performance Optimization**:
   - Batch processing optimization
   - Account mapping cache improvements
   - Parallel processing for large volumes
5. **Error Notification**: Add alerts for sync failures and system issues

### 📋 Implementation Details

The complete Odoo integration now includes:

- **Enhanced OdooApiClient**: Extended existing client with executeKw, search, searchRead, create, and postAccountMove methods
- **Enhanced OdooIntegrationReadPlatformService**: Added account mapping functionality with caching to existing service
- **OdooJournalEntryService**: High-level service for posting journal entries with validation
- **Enhanced Job Tasklet**: Uses real Odoo services instead of simulation code

### 🏗️ Architecture Benefits

- **Reused Existing Components**: Enhanced existing services instead of creating duplicates
- **Clean Separation**: API client handles low-level operations, service handles business logic
- **Proper Caching**: Account mappings cached to reduce API calls
- **Error Handling**: Comprehensive error handling and logging at each level

---

**Last Updated**: September 2025  
**Version**: 1.0  
**Author**: Crediblex Development Team
