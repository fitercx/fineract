package com.crediblex.fineract.accounting.journalentry.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.data.JournalEntryAssociationParametersData;
import org.apache.fineract.accounting.journalentry.data.JournalEntryData;
import org.apache.fineract.accounting.journalentry.service.JournalEntryReadPlatformService;
import org.apache.fineract.accounting.journalentry.service.JournalEntryReadPlatformServiceImpl;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Custom Journal Entry Read Platform Service to fix duplicate rows issue.
 *
 * The issue: When transactionDetails=true, the LEFT JOIN with m_note table can create duplicate rows if a transaction
 * has multiple notes. This causes the same Entry ID to appear multiple times in the UI.
 *
 * Fix: Use a subquery to get only the first/most recent note per transaction, preventing duplicate rows in the result
 * set.
 */
@Slf4j
@Service
@Primary
public class CustomJournalEntryReadPlatformServiceImpl extends JournalEntryReadPlatformServiceImpl
        implements JournalEntryReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final org.apache.fineract.infrastructure.core.service.PaginationHelper paginationHelper;
    private final org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator sqlGenerator;

    public CustomJournalEntryReadPlatformServiceImpl(JdbcTemplate jdbcTemplate,
            org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService glAccountReadPlatformService,
            org.apache.fineract.organisation.office.service.OfficeReadPlatformService officeReadPlatformService,
            org.apache.fineract.infrastructure.security.utils.ColumnValidator columnValidator,
            org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper,
            org.apache.fineract.infrastructure.core.service.PaginationHelper paginationHelper,
            org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator sqlGenerator) {
        super(jdbcTemplate, glAccountReadPlatformService, officeReadPlatformService, columnValidator,
                financialActivityAccountRepositoryWrapper, paginationHelper, sqlGenerator);
        this.jdbcTemplate = jdbcTemplate;
        this.paginationHelper = paginationHelper;
        this.sqlGenerator = sqlGenerator;
    }

    /**
     * Custom GLJournalEntryMapper that fixes the duplicate note issue by using a subquery to get only one note per
     * transaction.
     */
    private static final class CustomGLJournalEntryMapper implements RowMapper<JournalEntryData> {

        private final JournalEntryAssociationParametersData associationParametersData;

        CustomGLJournalEntryMapper(final JournalEntryAssociationParametersData associationParametersData) {
            this.associationParametersData = Objects.requireNonNullElseGet(associationParametersData,
                    JournalEntryAssociationParametersData::new);
        }

        public String schema() {
            StringBuilder sb = new StringBuilder();
            sb.append(" journalEntry.id as id, glAccount.classification_enum as classification ,").append("journalEntry.transaction_id,")
                    .append(" glAccount.name as glAccountName, glAccount.gl_code as glAccountCode,glAccount.id as glAccountId, ")
                    .append(" journalEntry.office_id as officeId, office.name as officeName, journalEntry.ref_num as referenceNumber, ")
                    .append(" journalEntry.manual_entry as manualEntry,journalEntry.entry_date as transactionDate, ")
                    .append(" journalEntry.type_enum as entryType,journalEntry.amount as amount, journalEntry.transaction_id as transactionId,")
                    .append(" journalEntry.entity_type_enum as entityType, journalEntry.entity_id as entityId, creatingUser.id as createdByUserId, ")
                    .append(" creatingUser.username as createdByUserName, journalEntry.description as comments, ")
                    .append(" journalEntry.submitted_on_date as submittedOnDate, journalEntry.reversed as reversed, ")
                    .append(" journalEntry.currency_code as currencyCode, curr.name as currencyName, curr.internationalized_name_code as currencyNameCode, ")
                    .append(" curr.display_symbol as currencyDisplaySymbol, curr.decimal_places as currencyDigits, curr.currency_multiplesof as inMultiplesOf ");
            if (associationParametersData.isRunningBalanceRequired()) {
                sb.append(" ,journalEntry.is_running_balance_calculated as runningBalanceComputed, ")
                        .append(" journalEntry.office_running_balance as officeRunningBalance, ")
                        .append(" journalEntry.organization_running_balance as organizationRunningBalance ");
            }
            if (associationParametersData.isTransactionDetailsRequired()) {
                sb.append(" ,pd.receipt_number as receiptNumber, ").append(" pd.check_number as checkNumber, ")
                        .append(" pd.account_number as accountNumber, ").append(" pt.value as paymentTypeName, ")
                        .append(" pd.payment_type_id as paymentTypeId,").append(" pd.bank_number as bankNumber, ")
                        .append(" pd.routing_code as routingCode, ").append(" note.id as noteId, ")
                        .append(" note.note as transactionNote, ").append(" lt.transaction_type_enum as loanTransactionType, ")
                        .append(" st.transaction_type_enum as savingsTransactionType ");
            }
            sb.append(" from acc_gl_journal_entry as journalEntry ")
                    .append(" left join acc_gl_account as glAccount on glAccount.id = journalEntry.account_id")
                    .append(" left join m_office as office on office.id = journalEntry.office_id")
                    .append(" left join m_appuser as creatingUser on creatingUser.id = journalEntry.created_by ")
                    .append(" join m_currency curr on curr.code = journalEntry.currency_code ");
            if (associationParametersData.isTransactionDetailsRequired()) {
                sb.append(" left join m_loan_transaction as lt on journalEntry.loan_transaction_id = lt.id ")
                        .append(" left join m_savings_account_transaction as st on journalEntry.savings_transaction_id = st.id ")
                        .append(" left join m_payment_detail as pd on lt.payment_detail_id = pd.id or st.payment_detail_id = pd.id or journalEntry.payment_details_id = pd.id")
                        .append(" left join m_payment_type as pt on pt.id = pd.payment_type_id ");

                // FIX: Use a subquery to get only the most recent note per transaction to prevent duplicates
                // This ensures each journal entry appears only once, even if there are multiple notes
                // The subquery gets the note with the maximum ID (most recent) for each transaction
                // This works for both PostgreSQL and MySQL
                // We use separate subqueries for loan and savings transactions to handle NULLs correctly
                sb.append(" left join (").append("   SELECT id, note, loan_transaction_id, savings_account_transaction_id")
                        .append("   FROM m_note n1").append("   WHERE n1.id IN (").append("     SELECT MAX(id)").append("     FROM m_note")
                        .append("     WHERE (loan_transaction_id IS NOT NULL OR savings_account_transaction_id IS NOT NULL)")
                        .append("     GROUP BY ").append("       COALESCE(loan_transaction_id, -1),")
                        .append("       COALESCE(savings_account_transaction_id, -1)").append("   )").append(" ) as note on (")
                        .append("   (lt.id IS NOT NULL AND lt.id = note.loan_transaction_id)").append("   OR")
                        .append("   (st.id IS NOT NULL AND st.id = note.savings_account_transaction_id)").append(" ) ");
            }
            return sb.toString();
        }

        @Override
        public JournalEntryData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            // Delegate to parent's mapper logic
            // We need to use the same mapping logic as the parent class
            final Long id = rs.getLong("id");
            final Long officeId = rs.getLong("officeId");
            final String officeName = rs.getString("officeName");
            final String glCode = rs.getString("glAccountCode");
            final String glAccountName = rs.getString("glAccountName");
            final Long glAccountId = rs.getLong("glAccountId");
            final int accountTypeId = JdbcSupport.getInteger(rs, "classification");
            final org.apache.fineract.infrastructure.core.data.EnumOptionData accountType = org.apache.fineract.accounting.common.AccountingEnumerations
                    .gLAccountType(accountTypeId);
            final LocalDate transactionDate = JdbcSupport.getLocalDate(rs, "transactionDate");
            final Boolean manualEntry = rs.getBoolean("manualEntry");
            final BigDecimal amount = rs.getBigDecimal("amount");
            final int entryTypeId = JdbcSupport.getInteger(rs, "entryType");
            final org.apache.fineract.infrastructure.core.data.EnumOptionData entryType = org.apache.fineract.accounting.common.AccountingEnumerations
                    .journalEntryType(entryTypeId);
            final String transactionId = rs.getString("transactionId");
            final Integer entityTypeId = JdbcSupport.getInteger(rs, "entityType");
            org.apache.fineract.infrastructure.core.data.EnumOptionData entityType = null;
            if (entityTypeId != null) {
                entityType = org.apache.fineract.accounting.common.AccountingEnumerations.portfolioProductType(entityTypeId);
            }

            final Long entityId = JdbcSupport.getLong(rs, "entityId");
            final Long createdByUserId = rs.getLong("createdByUserId");
            final LocalDate submittedOnDate = JdbcSupport.getLocalDate(rs, "submittedOnDate");
            final String createdByUserName = rs.getString("createdByUserName");
            final String comments = rs.getString("comments");
            final Boolean reversed = rs.getBoolean("reversed");
            final String referenceNumber = rs.getString("referenceNumber");
            BigDecimal officeRunningBalance = null;
            BigDecimal organizationRunningBalance = null;
            Boolean runningBalanceComputed = null;

            final String currencyCode = rs.getString("currencyCode");
            final String currencyName = rs.getString("currencyName");
            final String currencyNameCode = rs.getString("currencyNameCode");
            final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
            final Integer currencyDigits = JdbcSupport.getInteger(rs, "currencyDigits");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");
            final org.apache.fineract.organisation.monetary.data.CurrencyData currency = new org.apache.fineract.organisation.monetary.data.CurrencyData(
                    currencyCode, currencyName, currencyDigits, inMultiplesOf, currencyDisplaySymbol, currencyNameCode);

            if (associationParametersData.isRunningBalanceRequired()) {
                officeRunningBalance = rs.getBigDecimal("officeRunningBalance");
                organizationRunningBalance = rs.getBigDecimal("organizationRunningBalance");
                runningBalanceComputed = rs.getBoolean("runningBalanceComputed");
            }
            org.apache.fineract.accounting.journalentry.data.TransactionDetailData transactionDetailData = null;

            if (associationParametersData.isTransactionDetailsRequired()) {
                org.apache.fineract.portfolio.paymentdetail.data.PaymentDetailData paymentDetailData = null;
                final Long paymentTypeId = JdbcSupport.getLong(rs, "paymentTypeId");
                if (paymentTypeId != null) {
                    final String typeName = rs.getString("paymentTypeName");
                    final org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData paymentType = org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData
                            .instance(paymentTypeId, typeName);
                    final String accountNumber = rs.getString("accountNumber");
                    final String checkNumber = rs.getString("checkNumber");
                    final String routingCode = rs.getString("routingCode");
                    final String receiptNumber = rs.getString("receiptNumber");
                    final String bankNumber = rs.getString("bankNumber");
                    paymentDetailData = new org.apache.fineract.portfolio.paymentdetail.data.PaymentDetailData(id, paymentType,
                            accountNumber, checkNumber, routingCode, receiptNumber, bankNumber);
                }
                org.apache.fineract.portfolio.note.data.NoteData noteData = null;
                final Long noteId = JdbcSupport.getLong(rs, "noteId");
                if (noteId != null) {
                    final String note = rs.getString("transactionNote");
                    noteData = org.apache.fineract.portfolio.note.data.NoteData.builder().id(noteId).note(note).build();
                }
                Long transaction = null;
                if (entityType != null && transactionId != null) {
                    String numericPart = transactionId.replaceAll("[^\\d]", "");
                    if (!numericPart.isEmpty()) {
                        transaction = Long.parseLong(numericPart);
                    }
                }

                org.apache.fineract.accounting.journalentry.data.TransactionTypeEnumData transactionTypeEnumData = null;

                if (org.apache.fineract.portfolio.account.PortfolioAccountType.fromInt(entityTypeId).isLoanAccount()) {
                    final org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData loanTransactionType = org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations
                            .transactionType(JdbcSupport.getInteger(rs, "loanTransactionType"));
                    transactionTypeEnumData = new org.apache.fineract.accounting.journalentry.data.TransactionTypeEnumData(
                            loanTransactionType.getId(), loanTransactionType.getCode(), loanTransactionType.getValue());
                } else if (org.apache.fineract.portfolio.account.PortfolioAccountType.fromInt(entityTypeId).isSavingsAccount()) {
                    final org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionEnumData savingsTransactionType = org.apache.fineract.portfolio.savings.service.SavingsEnumerations
                            .transactionType(JdbcSupport.getInteger(rs, "savingsTransactionType"));
                    transactionTypeEnumData = new org.apache.fineract.accounting.journalentry.data.TransactionTypeEnumData(
                            savingsTransactionType.getId(), savingsTransactionType.getCode(), savingsTransactionType.getValue());
                }

                transactionDetailData = new org.apache.fineract.accounting.journalentry.data.TransactionDetailData(transaction,
                        paymentDetailData, noteData, transactionTypeEnumData);
            }
            return new JournalEntryData(id, officeId, officeName, glAccountName, glAccountId, glCode, accountType, transactionDate,
                    entryType, amount, transactionId, manualEntry, entityType, entityId, createdByUserId, submittedOnDate,
                    createdByUserName, comments, reversed, referenceNumber, officeRunningBalance, organizationRunningBalance,
                    runningBalanceComputed, transactionDetailData, currency);
        }
    }

    @Override
    public Page<JournalEntryData> retrieveAll(final SearchParameters searchParameters, final Long glAccountId,
            final Boolean onlyManualEntries, final LocalDate fromDate, final LocalDate toDate, final LocalDate submittedOnDateFrom,
            final LocalDate submittedOnDateTo, final String transactionId, final Integer entityType,
            final JournalEntryAssociationParametersData associationParametersData) {

        CustomGLJournalEntryMapper rm = new CustomGLJournalEntryMapper(associationParametersData);
        final StringBuilder sqlBuilder = new StringBuilder(200);
        sqlBuilder.append("select ").append(sqlGenerator.calcFoundRows()).append(" ");
        sqlBuilder.append(rm.schema());

        final Object[] objectArray = new Object[15];
        int arrayPos = 0;
        String whereClose = " where ";

        if (org.apache.commons.lang3.StringUtils.isNotBlank(transactionId)) {
            sqlBuilder.append(whereClose).append(" journalEntry.transaction_id = ?");
            objectArray[arrayPos] = transactionId;
            arrayPos = arrayPos + 1;

            whereClose = " and ";
        }

        if (entityType != null && entityType != 0 && (onlyManualEntries == null)) {
            sqlBuilder.append(whereClose).append(" journalEntry.entity_type_enum = ?");
            objectArray[arrayPos] = entityType;
            arrayPos = arrayPos + 1;
            whereClose = " and ";
        }

        if (searchParameters.hasOfficeId()) {
            sqlBuilder.append(whereClose).append(" journalEntry.office_id = ?");
            objectArray[arrayPos] = searchParameters.getOfficeId();
            arrayPos = arrayPos + 1;
            whereClose = " and ";
        }

        if (searchParameters.hasCurrencyCode()) {
            sqlBuilder.append(whereClose).append(" journalEntry.currency_code = ?");
            objectArray[arrayPos] = searchParameters.getCurrencyCode();
            arrayPos = arrayPos + 1;
            whereClose = " and ";
        }

        if (glAccountId != null && glAccountId != 0) {
            sqlBuilder.append(whereClose).append(" journalEntry.account_id = ?");
            objectArray[arrayPos] = glAccountId;
            arrayPos = arrayPos + 1;
            whereClose = " and ";
        }

        if (fromDate != null || toDate != null) {
            if (fromDate != null && toDate != null) {
                sqlBuilder.append(whereClose).append(" journalEntry.entry_date between ? and ? ");
                whereClose = " and ";
                objectArray[arrayPos] = fromDate;
                arrayPos = arrayPos + 1;
                objectArray[arrayPos] = toDate;
                arrayPos = arrayPos + 1;
            } else if (fromDate != null) {
                sqlBuilder.append(whereClose).append(" journalEntry.entry_date >= ? ");
                whereClose = " and ";
                objectArray[arrayPos] = fromDate;
                arrayPos = arrayPos + 1;
            } else {
                sqlBuilder.append(whereClose).append(" journalEntry.entry_date <= ? ");
                whereClose = " and ";
                objectArray[arrayPos] = toDate;
                arrayPos = arrayPos + 1;
            }
        }

        if (submittedOnDateFrom != null || submittedOnDateTo != null) {
            if (submittedOnDateFrom != null && submittedOnDateTo != null) {
                sqlBuilder.append(whereClose).append(" journalEntry.submitted_on_date between ? and ? ");
                whereClose = " and ";
                objectArray[arrayPos] = submittedOnDateFrom;
                arrayPos = arrayPos + 1;
                objectArray[arrayPos] = submittedOnDateTo;
                arrayPos = arrayPos + 1;
            } else if (submittedOnDateFrom != null) {
                sqlBuilder.append(whereClose).append(" journalEntry.submitted_on_date >= ? ");
                whereClose = " and ";
                objectArray[arrayPos] = submittedOnDateFrom;
                arrayPos = arrayPos + 1;
            } else {
                sqlBuilder.append(whereClose).append(" journalEntry.submitted_on_date <= ? ");
                whereClose = " and ";
                objectArray[arrayPos] = submittedOnDateTo;
                arrayPos = arrayPos + 1;
            }
        }

        if (onlyManualEntries != null && onlyManualEntries) {
            sqlBuilder.append(whereClose).append(" journalEntry.manual_entry = true ");
            whereClose = " and ";
        }

        sqlBuilder.append(" order by journalEntry.entry_date DESC, journalEntry.id DESC ");

        final Object[] finalObjectArray = new Object[arrayPos];
        System.arraycopy(objectArray, 0, finalObjectArray, 0, arrayPos);

        return paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), finalObjectArray, rm);
    }

    @Override
    public JournalEntryData retrieveGLJournalEntryById(final long glJournalEntryId,
            final JournalEntryAssociationParametersData associationParametersData) {
        try {
            CustomGLJournalEntryMapper rm = new CustomGLJournalEntryMapper(associationParametersData);
            final String sql = "select " + rm.schema() + " where journalEntry.id = ?";

            return this.jdbcTemplate.queryForObject(sql, rm, glJournalEntryId); // NOSONAR
        } catch (final EmptyResultDataAccessException e) {
            throw new org.apache.fineract.accounting.journalentry.exception.JournalEntriesNotFoundException(glJournalEntryId, e);
        }
    }
}
