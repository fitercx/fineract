#@LineOfCreditDrawdown
#Feature: Line Of Credit Receivable Drawdown Tests
#
#  @LOC_DRAW1 @TestRailId:LOCRD1
#  Scenario: Drawdown disbursed and fully repaid on due date with closure
#    Given A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account for LOC and disbursement
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown - interest will be deducted at disbursement
#    When Client makes a drawdown with the following details:
#      | amount            | 10000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW001        |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 10000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 1                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 90000
#
#    # Approve and disburse - interest deducted at disbursement (100 EUR)
#    And Admin approves the loan on "01 January 2025" date and principal amount of 10000
#    And Admin disburses the loan on "01 January 2025" date and principal of 10000 to savings account
#    Then Line of credit available balance should be 90000
#    Then Net disbursed amount is 9900
#
#    # Move to due date
#    When Admin sets the business date to "31 January 2025"
#
#    # Make full repayment on due date
#    When Client makes a repayment of 10000 EUR on "31 January 2025" date
#    Then Loan status is "closed.obligations.met"
#    And Loan outstanding balance is 0
#    And Overpaid amount is 0
#    And Repayment schedule status is "paid"
#    And Line of credit available balance should be 100000
#
#  @LOC_DRAW2 @TestRailId:LOCRD2
#  Scenario: Drawdown closed on exact due date with different amount
#    Given A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown with different amount (12000 EUR)
#    When Client makes a drawdown with the following details:
#      | amount            | 12000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW002        |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 12000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 12                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 88000
#
#    # Approve and disburse - interest deducted: (12000 × 12 × 30) / 360 / 100 = 120 EUR
#    And Admin approves the loan on "01 January 2025" date and principal amount of 12000
#    And Admin disburses the loan on "01 January 2025" date and principal of 12000 to savings account
#    Then Line of credit available balance should be 88000
#    Then Net disbursed amount is 11880
#
#    # Move to due date
#    When Admin sets the business date to "31 January 2025"
#
#    # Make full repayment on due date
#    When Client makes a repayment of 12000 EUR on "31 January 2025" date
#    Then Loan status is "closed.obligations.met"
#    And Loan outstanding balance is 0
#    And Overpaid amount is 0
#    And Repayment schedule status is "paid"
#    And Line of credit available balance should be 100000
#
#  @LOC_DRAW4 @TestRailId:LOCRD4
#  Scenario: Drawdown becomes overdue with penalty charges
#    Given An overdue penalty charge "Overdue Penalty" with percentage 5 exists
#    And A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown
#    When Client makes a drawdown with the following details:
#      | amount            | 10000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW004        |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 10000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 12                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 90000
#
#    # Approve and disburse
#    And Admin approves the loan on "01 January 2025" date and principal amount of 10000
#    And Admin disburses the loan on "01 January 2025" date and principal of 10000 to savings account
#    Then Line of credit available balance should be 90000
#    Then Net disbursed amount is 9900
#
#    # Move past due date (5 days overdue)
#    When Admin sets the business date to "05 February 2025"
#
#    # Verify overdue status and penalties
#    Then Loan schedule status is "Overdue"
#    And Loan penalties outstanding is greater than 0
#    And Loan principal outstanding is 10000
#    And Loan interest outstanding is 100
#
#  @LOC_DRAW1A @TestRailId:LOCRD1A
#  Scenario: Drawdown disbursed and repaid on same day results in overpaid status
#    Given A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account for LOC and disbursement
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown
#    When Client makes a drawdown with the following details:
#      | amount            | 10000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW001A       |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 10000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 12                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 90000
#
#    # Approve and disburse - interest deducted at disbursement (100 EUR)
#    And Admin approves the loan on "01 January 2025" date and principal amount of 10000
#    And Admin disburses the loan on "01 January 2025" date and principal of 10000 to savings account
#    Then Line of credit available balance should be 90000
#    Then Net disbursed amount is 9900
#
#    # Make full repayment on SAME DAY (not due date) - interest refunded
#    When Client makes a repayment of 10000 EUR on "01 January 2025" date
#    Then Loan status is "Overpaid"
#    And Loan outstanding balance is 0
#    And Overpaid amount is 100
#    And Repayment schedule status is "paid"
#    And Line of credit available balance should be 100000
#
#  @LOC_DRAW3 @TestRailId:LOCRD3
#  Scenario: Drawdown foreclosed after 15 days with interest refund
#    Given A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown
#    When Client makes a drawdown with the following details:
#      | amount            | 10000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW003        |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 10000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 1                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 90000
#
#    # Approve and disburse - interest deducted at disbursement (100 EUR for 30 days)
#    And Admin approves the loan on "01 January 2025" date and principal amount of 10000
#    And Admin disburses the loan on "01 January 2025" date and principal of 10000 to savings account
#    Then Line of credit available balance should be 90000
#    Then Net disbursed amount is 9900
#
#    # Move to day 15 and foreclose (only 15 days used, 50 EUR interest, 50 EUR refunded)
#    When Admin sets the business date to "16 January 2025"
#    And Admin forecloses the drawdown loan on "16 January 2025" date
#
#    Then Loan status is "overpaid"
#    And Loan outstanding balance is 0
#    And Overpaid amount is 50
#    And Repayment schedule status is "paid"
#    And Line of credit available balance should be 100000
#
#  @LOC_DRAW5 @TestRailId:LOCRD5
#  Scenario: Overdue drawdown paid with multiple repayments
#    Given An overdue penalty charge "Overdue Penalty" with percentage 5 exists
#    And A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown
#    When Client makes a drawdown with the following details:
#      | amount            | 10000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW005        |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 10000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 12                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 90000
#
#    # Approve and disburse
#    And Admin approves the loan on "01 January 2025" date and principal amount of 10000
#    And Admin disburses the loan on "01 January 2025" date and principal of 10000 to savings account
#    Then Line of credit available balance should be 90000
#    Then Net disbursed amount is 9900
#
#    # Move past due date (5 days overdue)
#    When Admin sets the business date to "05 February 2025"
#    Then Loan schedule status is "Overdue"
#
#    # Make first partial repayment - pays penalties (500) + interest (100)
#    When Client makes a partial repayment of 600 EUR on "05 February 2025" date
#    Then Loan penalties outstanding is 0
#    And Loan interest outstanding is 0
#    And Loan principal outstanding is 10000
#    And Line of credit available balance should be 90000
#
#    # Make second partial repayment - pays part of principal
#    When Client makes a partial repayment of 5000 EUR on "10 February 2025" date
#    Then Loan principal outstanding is 5000
#    And Line of credit available balance should be 95000
#
#    # Make final repayment - pays remaining principal
#    When Client makes a final repayment of 5000 EUR on "15 February 2025" date
#    Then Loan status is "closed.obligations.met"
#    And Loan outstanding balance is 0
#    And Overpaid amount is 0
#    And Repayment schedule status is "paid"
#    And Line of credit available balance should be 100000
#
#  @LOC_DRAW6 @TestRailId:LOCRD6
#  Scenario: Overdue drawdown written off after partial repayments
#    Given An overdue penalty charge "Overdue Penalty" with percentage 5 exists
#    And A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown
#    When Client makes a drawdown with the following details:
#      | amount            | 10000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW006        |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 10000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 12                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 90000
#
#    # Approve and disburse
#    And Admin approves the loan on "01 January 2025" date and principal amount of 10000
#    And Admin disburses the loan on "01 January 2025" date and principal of 10000 to savings account
#    Then Line of credit available balance should be 90000
#    Then Net disbursed amount is 9900
#
#    # Move past due date
#    When Admin sets the business date to "05 February 2025"
#
#    # Make partial repayments
#    When Client makes a partial repayment of 600 EUR on "08 February 2025" date
#    When Client makes a partial repayment of 3000 EUR on "12 February 2025" date
#    Then Loan principal outstanding is 7000
#
#    # Write off after 10 days overdue
#    When Admin sets the business date to "15 February 2025"
#    And Admin writes off the drawdown loan on "15 February 2025" date
#
#    Then Loan status is "closed.written.off"
#    And Line of credit available balance should be 93000
#
#  @LOC_DRAW7 @TestRailId:LOCRD7
#  Scenario: Drawdown with multiple repayments before due date
#    Given A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
#    And A Custom EUR savings product exists
#    When Admin sets the business date to "01 January 2025"
#    And Admin creates a client with random data
#
#    # Setup savings account
#    And Client creates a new EUR savings account with "01 January 2025" submitted on date
#    And Approve EUR savings account on "01 January 2025" date
#    And Activate EUR savings account on "01 January 2025" date
#
#    # Create and activate LOC
#    When Client creates a new "receivable" line of credit with start date "01 January 2025" and max limit 100000
#    And Line of credit is approved on "01 January 2025" with expected available 100000
#    And Line of credit is activated on "01 January 2025" with expected available 100000
#
#    # Make drawdown
#    When Client makes a drawdown with the following details:
#      | amount            | 15000              |
#      | date              | 01 January 2025    |
#      | invoiceNo         | INV-DRAW007        |
#      | invoiceDate       | 01 January 2025    |
#      | invoiceDueDate    | 31 January 2025    |
#      | invoiceAmount     | 15000              |
#      | invoiceCurrency   | EUR                |
#      | disapprovedAmount | 0                  |
#      | advancePercentage | 100                |
#      | interestRate      | 12                 |
#      | tenor             | 30                 |
#    Then Line of credit available balance should be 85000
#
#    # Approve and disburse - interest: (15000 × 12 × 30) / 360 / 100 = 150 EUR
#    And Admin approves the loan on "01 January 2025" date and principal amount of 15000
#    And Admin disburses the loan on "01 January 2025" date and principal of 15000 to savings account
#    Then Line of credit available balance should be 85000
#    Then Net disbursed amount is 14850
#
#    # Make partial repayment 1 on day 10
#    When Admin sets the business date to "10 January 2025"
#    And Client makes a partial repayment of 5000 EUR on "10 January 2025" date
#    Then Loan principal outstanding is 10000
#    And Line of credit available balance should be 90000
#
#    # Make partial repayment 2 on day 20
#    When Admin sets the business date to "20 January 2025"
#    And Client makes a partial repayment of 6000 EUR on "20 January 2025" date
#    Then Loan principal outstanding is 4000
#    And Line of credit available balance should be 96000
#
#    # Make final repayment on due date
#    When Admin sets the business date to "31 January 2025"
#    And Client makes a final repayment of 4000 EUR on "31 January 2025" date
#    Then Loan status is "closed.obligations.met"
#    And Interest is calculated for exactly 30 days
#    And Loan outstanding balance is 0
#    And Overpaid amount is 0
#    And Repayment schedule status is "paid"
#    And Line of credit available balance should be 100000
#
