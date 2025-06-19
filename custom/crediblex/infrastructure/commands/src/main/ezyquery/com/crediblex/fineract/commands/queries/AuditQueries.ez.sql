-- ## dynamic:LoanChargeWaiveDetails
SELECT 
    c.display_name as clientName_string,
    l.id as loanId_long,
    lc.amount as waiveOffAmount_decimal,
    lc.id as loanChargeId_long
FROM 
    m_loan_charge lc
JOIN 
    m_loan l ON lc.loan_id = l.id
JOIN 
    m_client c ON l.client_id = c.id
