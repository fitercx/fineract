-- ## dynamic:Rapayment status query
WITH pending_installments
         as (select *
             from m_loan_repayment_schedule
             where loan_id = :loanId
               and duedate <= :now
             order by duedate desc),
     aggregated_balances
         as (select loan_id,
                    sum(coalesce(principal_amount, 0))                   as principal,
                    sum(coalesce(principal_writtenoff_derived, 0))       as principal_written_off,
                    sum(coalesce(principal_completed_derived, 0))        as principal_completed,

                    sum(coalesce(interest_amount, 0))                    as interest,
                    sum(coalesce(interest_writtenoff_derived, 0))        as interest_written_off,
                    sum(coalesce(interest_waived_derived, 0))            as interest_waived,
                    sum(coalesce(fee_charges_completed_derived, 0))      as interest_completed,

                    sum(coalesce(fee_charges_amount, 0))                 as fees,
                    sum(coalesce(fee_charges_writtenoff_derived, 0))     as fees_written_off,
                    sum(coalesce(fee_charges_waived_derived, 0))         as fees_waived,
                    sum(coalesce(fee_charges_completed_derived, 0))      as fees_completed,

                    sum(coalesce(penalty_charges_amount, 0))             as penalties,
                    sum(coalesce(penalty_charges_writtenoff_derived, 0)) as penalties_written_off,
                    sum(coalesce(penalty_charges_waived_derived, 0))     as penalties_waived,
                    sum(coalesce(penalty_charges_completed_derived, 0))  as penalties_completed
             from pending_installments
             group by loan_id),
     due_amounts
         as (select loan_id,
                    principal - principal_written_off - principal_completed                    as principal_due,
                    interest - interest_written_off - interest_waived - interest_completed     as interest_due,
                    fees - fees_written_off - fees_waived - fees_completed                     as fee_due,
                    penalties - penalties_written_off - penalties_waived - penalties_completed as penalty_due
             from aggregated_balances),
     earliest_due_schedule
         AS (SELECT ls.loan_id
                  , ls.duedate as datedue
             FROM m_loan_repayment_schedule ls
             WHERE ls.loan_id = :loanId
               AND ls.completed_derived = false
             ORDER BY ls.duedate
             LIMIT 1),
     latest_transaction
         AS (SELECT tr.loan_id
                  , MAX(tr.transaction_date) AS max_date
             FROM m_loan_transaction tr
             WHERE tr.loan_id = :loanId
               AND tr.is_reversed = false
               AND transaction_type_enum in (:transactionTypes)
             group by tr.loan_id)
SELECT (CASE
            WHEN lt.max_date > ed.datedue THEN lt.max_date
            ELSE ed.datedue END)      as transactionDate_date,

       ls.principal_due               as principalDue_decimal,
       ls.interest_due                as interestDue_decimal,
       ls.fee_due                     as feeDue_decimal,
       ls.penalty_due                 as penaltyDue_decimal,
       l.currency_code                as currencyCode_string,
       l.currency_digits              as currencyDigits_int,
       l.currency_multiplesof         as inMultiplesOf_int,
       l.net_disbursal_amount         as netDisbursalAmount_decimal,
       rc."name"                      as currencyName_string,
       rc.display_symbol              as currencyDisplaySymbol_string,
       rc.internationalized_name_code as currencyNameCode_string
FROM m_loan l
         JOIN m_currency rc on rc."code" = l.currency_code
         JOIN due_amounts ls ON ls.loan_id = l.id
         JOIN earliest_due_schedule ed on ed.loan_id = ls.loan_id
         LEFT JOIN latest_transaction lt on lt.loan_id = l.id
WHERE l.id = :loanId;
