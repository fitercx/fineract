@LoanAccountCustom
Feature: LoanAccountCustom

  @TestRailId:CRED2
  Scenario: Create and disburse a loan to client's savings account
    Given A Custom EUR savings product exists
    Given A EUR loan product exists
    When Admin sets the business date to "1 January 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "1 January 2024" submitted on date
    And Approve EUR savings account on "1 January 2024" date
    And Activate EUR savings account on "1 January 2024" date
    When Client creates a new EUR loan with "1 January 2024" submitted on date and principal 10000 linked to savings account
    And Admin approves the loan on "1 January 2024" date and principal amount of 10000
    And Admin disburses the loan on "1 January 2024" date and principal of 10000 to savings account

#  @TestRailId:CRED3
#  Scenario: Create loan, disburse to savings and verify disbursement transaction
#    Given A Custom EUR savings product exists
#    Given A EUR loan product exists
#    When Admin sets the business date to "1 February 2024"
#    When Admin creates a client with random data
#    And Client creates a new EUR savings account with "1 February 2024" submitted on date
#    And Approve EUR savings account on "1 February 2024" date
#    And Activate EUR savings account on "1 February 2024" date
#    When Client creates a new EUR loan with "1 February 2024" submitted on date and principal 15000
#    And Admin approves the loan on "1 February 2024" date
#    And Admin disburses the loan on "1 February 2024" date to savings account
#    Then Loan has a "loanTransactionType.disbursement" transaction with amount 15000 on "1 February 2024"
#
#  @TestRailId:CRED4
#  Scenario: Create loan with disbursement charge including VAT
#    Given A Custom EUR savings product exists
#    Given A EUR loan product exists
#    Given A disbursement charge "Disbursement Fee" of type 1 with amount 100 and VAT 15 percent exists
#    When Admin sets the business date to "1 March 2024"
#    When Admin creates a client with random data
#    And Client creates a new EUR savings account with "1 March 2024" submitted on date
#    And Approve EUR savings account on "1 March 2024" date
#    And Activate EUR savings account on "1 March 2024" date
#    When Client creates a new EUR loan with "1 March 2024" submitted on date, principal 20000 and disbursement charge
#    And Admin approves the loan on "1 March 2024" date
#    And Admin disburses the loan on "1 March 2024" date to savings account
#    Then Loan has exactly 3 transactions on "1 March 2024"
#    And Loan has a "loanTransactionType.disbursement" transaction with amount 20000 on "1 March 2024"
#    And Loan has a "loanTransactionType.accrual" transaction with amount 100 on "1 March 2024"
#    And Loan has a "loanTransactionType.accrual" transaction with amount 15 on "1 March 2024"
