Feature: Client Account Details API
  As a user
  I want to retrieve client account details
  So that I can view all accounts associated with a client

  Background:
    Given the system is set up with test data
    And I am logged in as a user with appropriate permissions

  Scenario: Retrieve account details for a client with active loans
    Given a client exists with ID "1"
    And the client has active loans
    When I request the client's account details
    Then the response should contain loan account information
    And the loan accounts should include installment amounts
    And the loan accounts should include late fees

  Scenario: Retrieve account details for a client with no active loans
    Given a client exists with ID "2"
    And the client has no active loans
    When I request the client's account details
    Then the response should be successful
    And the response should contain an empty list of loan accounts

  Scenario: Retrieve account details for a non-existent client
    Given a client with ID "999" does not exist
    When I request the client's account details
    Then the response should return a not found error

  Scenario: Retrieve account details for a client with late fees
    Given a client exists with ID "3"
    And the client has loans with late fees
    When I request the client's account details
    Then the response should contain loan account information
    And the loan accounts should include late fees
    And the late fees should be calculated correctly 