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
                    sum(coalesce(interest_completed_derived, 0))         as interest_completed,

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
     latest_transaction
         AS (SELECT tr.loan_id
                  , MAX(tr.transaction_date) AS max_date
             FROM m_loan_transaction tr
             WHERE tr.loan_id = :loanId
               AND tr.is_reversed = false
               AND transaction_type_enum in (:transactionTypes)
             group by tr.loan_id),
     earliest_unpaid_schedule
         AS (
         -- this table will take care of the situation where we do not have a pending schedule in the past
         -- but have a pending schedule in the future. in this case the due amount will be the earliest repayment
         -- schedule in the future.
         SELECT loan_id                                        as loan_id,
                duedate                                        as due_date,
                coalesce(principal_amount, 0) -
                coalesce(principal_writtenoff_derived, 0) -
                coalesce(principal_completed_derived, 0)       as principal_due,

                coalesce(interest_amount, 0) -
                coalesce(interest_writtenoff_derived, 0) -
                coalesce(interest_waived_derived, 0) -
                coalesce(interest_completed_derived, 0)        as interest_due,

                coalesce(fee_charges_amount, 0) -
                coalesce(fee_charges_writtenoff_derived, 0) -
                coalesce(fee_charges_waived_derived, 0) -
                coalesce(fee_charges_completed_derived, 0)     as fees_due,

                coalesce(penalty_charges_amount, 0) -
                coalesce(penalty_charges_writtenoff_derived, 0) -
                coalesce(penalty_charges_waived_derived, 0) -
                coalesce(penalty_charges_completed_derived, 0) as penalties_due
         FROM m_loan_repayment_schedule ls
         WHERE loan_id = :loanId
           AND completed_derived = false
         ORDER BY ls.duedate
         LIMIT 1)
SELECT (CASE
            WHEN coalesce(lt.max_date, DATE '1970-01-01') > eus.due_date THEN lt.max_date
            ELSE eus.due_date END)                   as transactionDate_date,
    eus.due_date as eusDueDate_date,
    eus.principal_due as eusPrincipal_decimal,
       coalesce(ls.principal_due, eus.principal_due) as principalDue_decimal,
       coalesce(ls.interest_due, eus.interest_due)   as interestDue_decimal,
       coalesce(ls.fee_due, eus.fees_due)            as feeDue_decimal,
       coalesce(ls.penalty_due, eus.penalties_due)   as penaltyDue_decimal,
       l.currency_code                               as currencyCode_string,
       l.currency_digits                             as currencyDigits_int,
       l.currency_multiplesof                        as inMultiplesOf_int,
       l.net_disbursal_amount                        as netDisbursalAmount_decimal,
       rc."name"                                     as currencyName_string,
       rc.display_symbol                             as currencyDisplaySymbol_string,
       rc.internationalized_name_code                as currencyNameCode_string
FROM m_loan l
         JOIN m_currency rc on rc."code" = l.currency_code
         LEFT JOIN due_amounts ls ON ls.loan_id = l.id
         LEFT JOIN earliest_unpaid_schedule eus on eus.loan_id = l.id
         LEFT JOIN latest_transaction lt on lt.loan_id = l.id
WHERE l.id = :loanId;
