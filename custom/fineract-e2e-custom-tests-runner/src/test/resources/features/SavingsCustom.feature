@SavingsAccountCustom
Feature: SavingsAccountCustom

  @TestRailId:CRED1
  Scenario: As a user I would like to Deposit to my savings account
    Given A Custom EUR savings product exists
    When Admin sets the business date to "1 June 2022"
    When Admin creates a client with random data
    And Client creates a new EUR savings account with "1 June 2022" submitted on date
    And Approve EUR savings account on "1 June 2022" date
    And Activate EUR savings account on "1 June 2022" date
    And Client successfully deposits 1000 EUR to the savings account on "1 June 2022" date
