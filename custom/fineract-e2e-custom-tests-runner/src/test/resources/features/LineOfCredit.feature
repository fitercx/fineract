@LineOfCredit
Feature: Line Of Credit Operations

  @LOC1
  Scenario: Create client with savings account and line of credit, then approve and activate
    Given A Custom EUR savings product exists
    When Admin sets the business date to "01 January 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "01 January 2024" submitted on date
    And Approve EUR savings account on "01 January 2024" date
    And Activate EUR savings account on "01 January 2024" date
    When Client creates a new line of credit with start date "01 January 2024", max limit 50000 and expected available 50000
    And Line of credit is approved on "01 January 2024" with expected available 50000
    And Line of credit is activated on "01 January 2024" with expected available 50000
    Then Line of credit status is "ACTIVE" and maximum amount is 50000

  @LOC2
  Scenario: Create client with savings account and line of credit, verify pending status
    Given A Custom EUR savings product exists
    When Admin sets the business date to "05 February 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "05 February 2024" submitted on date
    And Approve EUR savings account on "05 February 2024" date
    And Activate EUR savings account on "05 February 2024" date
    When Client creates a new line of credit with start date "05 February 2024", max limit 30000 and expected available 30000
    Then Line of credit status is "SUBMITTED" and maximum amount is 30000

  @LOC4
  Scenario: Create receivable line of credit with 30-day tenor and make pending disbursal
    Given A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
    When Admin sets the business date to "15 April 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "15 April 2024" submitted on date
    And Approve EUR savings account on "15 April 2024" date
    And Activate EUR savings account on "15 April 2024" date
    When Client creates a new "receivable" line of credit with start date "15 April 2024" and max limit 500000
    And Line of credit is approved on "15 April 2024" with expected available 500000
    And Line of credit is activated on "15 April 2024" with expected available 500000
    Then Line of credit status is "ACTIVE" and maximum amount is 500000
    Then Admin sets the business date to "19 September 2025"
    When Client makes a drawdown with the following details:
      | amount            | 100000             |
      | date              | 19 September 2025  |
      | reference         | DD001              |
      | invoiceNo         | INV-2025-19        |
      | invoiceDate       | 20 April 2025      |
      | invoiceDueDate    | 20 September 2025  |
      | invoiceAmount     | 100000.00          |
      | invoiceCurrency   | EUR                |
      | disapprovedAmount | 5000               |
      | advancePercentage | 90                 |
      | interestRate      | 1                 |
      | tenor             | 30                 |
    Then Line of credit available balance should be 414500
    And Drawdown transaction should be recorded with amount 85500

  @LOC5
  Scenario: Create receivable line of credit with 30-day tenor and complete disbursal
    Given A Custom EUR product with line of credit "receivable" loan product name "LOC Rec Pdt" with 30 days tenor exists
    When Admin sets the business date to "15 April 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "15 April 2024" submitted on date
    And Approve EUR savings account on "15 April 2024" date
    And Activate EUR savings account on "15 April 2024" date
    When Client creates a new "receivable" line of credit with start date "15 April 2024" and max limit 500000
    And Line of credit is approved on "15 April 2024" with expected available 500000
    And Line of credit is activated on "15 April 2024" with expected available 500000
    Then Line of credit status is "ACTIVE" and maximum amount is 500000
    Then Admin sets the business date to "19 September 2025"
    When Client makes a drawdown with the following details:
      | amount            | 100000             |
      | date              | 19 September 2025  |
      | reference         | DD001              |
      | invoiceNo         | INV-2025-19        |
      | invoiceDate       | 20 April 2025      |
      | invoiceDueDate    | 20 September 2025  |
      | invoiceAmount     | 100000.00          |
      | invoiceCurrency   | EUR                |
      | disapprovedAmount | 5000               |
      | advancePercentage | 90                 |
      | interestRate      | 1                 |
      | tenor             | 30                 |
    Then Line of credit available balance should be 414500
    And Drawdown transaction should be recorded with amount 85500
    And Admin approves the loan on "19 September 2025" date and principal amount of 85500
    And Admin disburses the loan on "19 September 2025" date and principal of 85500 to savings account
    Then Loan has exactly one transactions on "19 September 2025" of amount 84645

  @LOC6
  Scenario: Create payable LOC with foreign currency invoice in pending state
    Given A Custom EUR product with line of credit "payable" loan product name "LOC Payable Pdt" with 30 days tenor exists
    When Admin sets the business date to "30 April 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "30 April 2024" submitted on date
    And Approve EUR savings account on "30 April 2024" date
    And Activate EUR savings account on "30 April 2024" date
    When Client creates a new "payable" line of credit with start date "30 April 2024" and max limit 120000
    And Line of credit is approved on "30 April 2024" with expected available 120000
    And Line of credit is activated on "30 April 2024" with expected available 120000
    When Client makes a drawdown with the following details:
      | amount                      | 105000             |
      | date                        | 30 April 2024      |
      | reference                   | PAY-DRAW-001       |
      | tenor                       | 30                 |
      | invoiceNo                   | SUPP-INV-2024-050  |
      | invoiceDate                 | 28 April 2024      |
      | invoiceDueDate              | 08 May 2024        |
      | invoiceAmount               | 100000             |
      | invoiceCurrency             | EUR                |
      | disapprovedAmount           | 0                  |
      | exchangeRate                | 1.00               |
      | markup                      | 0.00               |
      | interestRate                | 1                  |
    Then Line of credit available balance should be 20000
    And Drawdown transaction should be recorded with amount 100000

  @LOC7
  Scenario: Create payable LOC with foreign currency invoice and complete disbursal
    Given A Custom EUR product with line of credit "payable" loan product name "LOC Payable Pdt" with 30 days tenor exists
    When Admin sets the business date to "30 April 2024"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "30 April 2024" submitted on date
    And Approve EUR savings account on "30 April 2024" date
    And Activate EUR savings account on "30 April 2024" date
    When Client creates a new "payable" line of credit with start date "30 April 2024" and max limit 120000
    And Line of credit is approved on "30 April 2024" with expected available 120000
    And Line of credit is activated on "30 April 2024" with expected available 120000
    When Client makes a drawdown with the following details:
      | amount                      | 105000             |
      | date                        | 30 April 2024      |
      | reference                   | PAY-DRAW-001       |
      | tenor                       | 30                 |
      | invoiceNo                   | SUPP-INV-2024-050  |
      | invoiceDate                 | 28 April 2024      |
      | invoiceDueDate              | 08 May 2024        |
      | invoiceAmount               | 100000             |
      | invoiceCurrency             | EUR                |
      | disapprovedAmount           | 0                  |
      | exchangeRate                | 1.00               |
      | markup                      | 0.00               |
      | interestRate                | 1                  |
    Then Line of credit available balance should be 20000
    And Drawdown transaction should be recorded with amount 100000
    And Admin approves the loan on "30 April 2024" date and principal amount of 100000
    And Admin disburses the loan on "30 April 2024" date and principal of 100000 to savings account
    Then Loan has exactly one transactions on "30 April 2024" of amount 100000
