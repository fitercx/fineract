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

  @TestRailId:CRED3
  Scenario: Create loan, disburse to savings and verify disbursement transaction
    Given A Custom EUR savings product exists
    Given A EUR loan product exists
    When Admin sets the business date to "01 February 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "01 February 2024" submitted on date
    And Approve EUR savings account on "01 February 2024" date
    And Activate EUR savings account on "01 February 2024" date
    When Client creates a new EUR loan with "01 February 2024" submitted on date and principal 15000 linked to savings account
    And Admin approves the loan on "01 February 2024" date and principal amount of 15000
    And Admin disburses the loan on "01 February 2024" date and principal of 15000 to savings account
    Then Loan has a "loanTransactionType.disbursement" transaction with amount 15000 on "01 February 2024"


  @TestRailId:CRED4
  Scenario: Create loan with disbursement charge including VAT
    Given A Custom EUR savings product exists
    Given A EUR loan product exists
    Given A disbursement charge "Disbursement Fee with VAT" of type 1 with amount 10 and VAT 15 percent exists
    When Admin sets the business date to "15 July 2025"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "1 March 2024" submitted on date
    And Approve EUR savings account on "15 July 2025" date
    And Activate EUR savings account on "15 July 2025" date
    When Client creates a new EUR loan with "15 July 2025" submitted on date, principal 20000 and disbursement charge
    And Admin approves the loan on "15 July 2025" date and principal amount of 20000
    And Admin disburses the loan on "15 July 2025" date and principal of 20000 to savings account
    Then Loan has exactly 4 transactions on "15 July 2025"
    And Loan has a "loanTransactionType.disbursement" transaction with amount 20000 on "15 July 2025"
    And Loan has a "loanTransactionType.repaymentAtDisbursement" transaction with amount 10 on "15 July 2025"
    And Loan has a "loanTransactionType.vatDeductionAtDisbursement" transaction with amount 1.5 on "15 July 2025"

  @TestRailId:CRED5
  Scenario: Create loan with factor rate fee calculation and verify fee distribution across installments
    Given A Custom EUR savings product exists
    Given A EUR loan product with factor rate 1.2 exists
    When Admin sets the business date to "01 March 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "01 March 2024" submitted on date
    And Approve EUR savings account on "01 March 2024" date
    And Activate EUR savings account on "01 March 2024" date
    When Client creates a new EUR loan with "01 March 2024" submitted on date, principal 12000 and factor rate 1.2
    And Admin approves the loan on "01 March 2024" date and principal amount of 12000
    And Admin disburses the loan on "01 March 2024" date and principal of 12000 to savings account
    Then Loan has factor rate fee distributed across all installments
    And Each installment has factor rate fee amount of 500
    And Total factor rate fee amount equals 6000

  @TestRailId:CRED6
  Scenario: Create loan with enhanced factor rate validation and edge case handling
    Given A Custom EUR savings product exists
    Given A EUR loan product with factor rate 1.5 exists
    When Admin sets the business date to "15 April 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "15 April 2024" submitted on date
    And Approve EUR savings account on "15 April 2024" date
    And Activate EUR savings account on "15 April 2024" date
    When Client creates a new EUR loan with "15 April 2024" submitted on date, principal 8000 and factor rate 1.5
    And Admin approves the loan on "15 April 2024" date and principal amount of 8000
    And Admin disburses the loan on "15 April 2024" date and principal of 8000 to savings account
    Then Loan schedule contains factor rate fees calculated correctly
    And Factor rate fee validation rules are enforced
    And Factor rate fee per installment equals 667
