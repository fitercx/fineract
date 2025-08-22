@LoanRepaymentAffectedInstallments
Feature: Loan Repayment Affected Installments

  @TestRailId:CRED5
  Scenario: Verify partial repayment affects specific installments with webhook data
    Given A Custom EUR savings product exists
    Given A EUR loan product exists
    When Admin sets the business date to "01 March 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "01 March 2024" submitted on date
    And Approve EUR savings account on "01 March 2024" date
    And Activate EUR savings account on "01 March 2024" date
    When Client creates a new EUR loan with "01 March 2024" submitted on date and principal 10000 linked to savings account
    And Admin approves the loan on "01 March 2024" date and principal amount of 10000
    And Admin disburses the loan on "01 March 2024" date and principal of 10000 to savings account
    When Client makes a loan repayment of 1500 on "01 March 2024"
    Then Loan repayment transaction includes affected installments data
    And Affected installments contain installment status information
    And Affected installments contain transaction portions for this repayment

  @TestRailId:CRED6
  Scenario: Verify large repayment affects multiple installments
    Given A Custom EUR savings product exists
    Given A EUR loan product exists
    When Admin sets the business date to "01 April 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "01 April 2024" submitted on date
    And Approve EUR savings account on "01 April 2024" date
    And Activate EUR savings account on "01 April 2024" date
    When Client creates a new EUR loan with "01 April 2024" submitted on date and principal 12000 linked to savings account
    And Admin approves the loan on "01 April 2024" date and principal amount of 12000
    And Admin disburses the loan on "01 April 2024" date and principal of 12000 to savings account
    When Client makes a loan repayment of 4000 on "01 April 2024"
    Then Loan repayment transaction includes affected installments data
    And Multiple installments are affected by the repayment
    And Each affected installment has complete status and transaction data

  @TestRailId:CRED7
  Scenario: Verify installment status changes after partial and full payments
    Given A Custom EUR savings product exists
    Given A EUR loan product exists
    When Admin sets the business date to "01 May 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "01 May 2024" submitted on date
    And Approve EUR savings account on "01 May 2024" date
    And Activate EUR savings account on "01 May 2024" date
    When Client creates a new EUR loan with "01 May 2024" submitted on date and principal 8000 linked to savings account
    And Admin approves the loan on "01 May 2024" date and principal amount of 8000
    And Admin disburses the loan on "01 May 2024" date and principal of 8000 to savings account
    When Client makes a loan repayment of 500 on "01 May 2024"
    Then Affected installments show "PARTIAL_PAID" status for partially paid installments
    When Client makes a loan repayment of 1500 on "01 June 2024"
    Then Affected installments show "PAID" status for fully paid installments
    And Remaining installments show appropriate status based on due dates

  @TestRailId:CRED8
  Scenario: Verify standing instruction repayment includes affected installments data
    Given A Custom EUR savings product exists
    Given A EUR loan product exists
    When Admin sets the business date to "01 June 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "01 June 2024" submitted on date
    And Approve EUR savings account on "01 June 2024" date
    And Activate EUR savings account on "01 June 2024" date
    When Client creates a new EUR loan with "01 June 2024" submitted on date and principal 15000 linked to savings account
    And Admin approves the loan on "01 June 2024" date and principal amount of 15000
    And Admin disburses the loan on "01 June 2024" date and principal of 15000 to savings account
    When Client deposits 3000 into savings account on "01 June 2024"
    When Standing instruction processes automatic repayment of 2000 on "01 July 2024"
    Then Standing instruction repayment includes affected installments webhook data
    And Webhook payload structure is consistent between UI and standing instruction repayments
