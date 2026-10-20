# LMS-150 local partial backdated repayment

Verified on the running local Fineract (`fineract-server`, tenant `default`) on 29 Sep 2026. The repayment went through the savings-to-loan transfer API, so `pro-rata-mifos-standard-strategy` allocated it. Daily LPI rows were inserted for this loan only. The penalty job was not run, because it would also charge the other overdue loans in this database.

## Loan

| | |
| --- | --- |
| Loan id | **10050** |
| Account | `000010050` |
| Client | 7489 (`ZZ-SUPERSEDED-7489`) |
| Product | Receivables Facility (67) |
| Strategy | `pro-rata-mifos-standard-strategy` — penalties, then fees, then interest, then principal |
| LOC | 2075, settlement savings 9540 |
| Disbursed | 01 Jun 2026, AED 100,000 |
| Due | 31 Jul 2026 (60 days) |
| Interest | 20% flat, AED 3,287.67 still outstanding before the payment |
| LPI | charge 3, 0.08219% of principal = AED 82.19 per day on 100,000 |
| Charges seeded | 31 Jul 2026 through 28 Sep 2026 (60 days, AED 4,931.40) |

## Payment

Savings 9540 to loan 10050, value date **10 Aug 2026**, amount **AED 15,000**. Transfer resource id 10554. Loan transaction **32451** (type 2, repayment). No waive transaction was created. Loan stayed active (`loan_status_id` 300).

Strategy allocation on transaction 32451:

| Portion | Amount |
| --- | --- |
| Penalty | 821.90 |
| Interest | 3,287.67 |
| Principal | 10,890.43 |
| Total | 15,000.00 |

821.90 is 10 × 82.19, the overdue days strictly before 10 Aug (31 Jul through 9 Aug). The previous behaviour collected only 31 Jul (82.19) and left 1–9 Aug outstanding.

## LPI after the payment

| Day | Amount | Base | State |
| --- | --- | --- | --- |
| 31 Jul and 9 Aug | 82.19 | 100,000 | Paid |
| 10 Aug and 28 Sep | 73.24 | 89,109.57 | Unpaid, not waived |

73.24 = 89,109.57 × 0.08219%, rounded to 2 decimals. 89,109.57 is the principal still outstanding (100,000 − 10,890.43). Fifty unpaid days × 73.24 = 3,662.00. Penalty charged on the loan is 4,483.90 (821.90 paid + 3,662.00 still due). Penalty waived is 0.

Receivables running balance on the transaction is 85,821.90. For a receivables loan that figure subtracts both the principal portion and the interest portion from the disbursed principal (100,000 − 10,890.43 − 3,287.67). Principal outstanding on `m_loan` stays 89,109.57.

## Related tickets

LMS-146 and LMS-147 are the same rule on the Fero dates: a partial dated 11 Sep collects 9 Sep and 10 Sep, and later LPI stays outstanding. The transfer screen hides the waive note unless the entered amount closes the loan.

LMS-132 acceptance text waives the value-date day and keeps the later days. This change follows LMS-150: the value-date day stays and is repriced from the reduced principal. Days before the value date keep the rate they already accrued.
