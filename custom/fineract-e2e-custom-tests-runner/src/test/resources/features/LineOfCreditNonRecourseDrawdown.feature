@LineOfCreditNonRecourseDrawdown
Feature: Line Of Credit Receivable Non-Recourse Drawdown Tests

  @LOC_NR_DRAW1 @TestRailId:LOCNRD1
  Scenario: Non-recourse drawdown is disbursed in full and repaid with interest at maturity
    Given A Custom EUR product with line of credit enabled exists
      | locType          | receivable  |
      | productName      | LOC Rec Pdt |
      | tenorDays        | 30          |
      | hasOverdueCharge | false       |
    And A Custom EUR savings product exists
    When Admin sets the business date to "01 January 2025"
    And Admin creates a client with random data

    # Setup savings account for LOC and disbursement
    And Client creates a new EUR savings account with "01 January 2025" submitted on date
    And Approve EUR savings account on "01 January 2025" date
    And Activate EUR savings account on "01 January 2025" date

    # Create and activate a non-recourse LOC (reuses the receivable loan product)
    When Client creates a new "non-recourse" line of credit with start date "01 January 2025" and max limit 100000
    And Line of credit is approved on "01 January 2025" with expected available 100000
    And Line of credit is activated on "01 January 2025" with expected available 100000

    # Make drawdown - interest is NOT deducted at disbursement
    When Client makes a drawdown with the following details:
      | amount            | 10000              |
      | date              | 01 January 2025    |
      | invoiceNo         | INV-NR-DRAW001     |
      | invoiceDate       | 01 January 2025    |
      | invoiceDueDate    | 31 January 2025    |
      | invoiceAmount     | 10000              |
      | invoiceCurrency   | EUR                |
      | disapprovedAmount | 0                  |
      | advancePercentage | 100                |
      | interestRate      | 1                  |
      | tenor             | 30                 |
    Then Line of credit available balance should be 90000

    # Approve and disburse the full funded amount (no upfront interest deduction)
    And Admin approves the loan on "01 January 2025" date and principal amount of 10000
    And Admin disburses the loan on "01 January 2025" date and principal of 10000 to savings account
    Then Line of credit available balance should be 90000
    Then Net disbursed amount is 10000

    # Move to due date
    When Admin sets the business date to "31 January 2025"

    # Repay principal + interest (10000 + 100) at maturity
    When Client successfully deposits 10100 EUR to the savings account on "31 January 2025" date
    When Client makes a repayment of 10100 EUR on "31 January 2025" date
    Then Loan status is "closed.obligations.met"
    And Loan outstanding balance is 0
    And Overpaid amount is 0
    And Repayment schedule status is "paid"
    # Only the principal was reserved, so the full limit is restored
    And Line of credit available balance should be 100000
