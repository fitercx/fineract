package com.crediblex.fineract.test.data.job;

import org.apache.fineract.test.data.job.Job;

public enum CustomDefaultJob implements Job {


    APPLY_OVERDUE_PENALTY_JOB("Apply penalty to overdue loans","LA_OPEN"),
    UPDATE_LOAN_ARREARS_AGEING_JOB("Update Loan Arrears Ageing","LA_ARAG");

    private final String shortName;
    private final String customName;
    CustomDefaultJob(String customName, String shortName) {
        this.customName =customName;
        this.shortName = shortName;
    }
    @Override
    public String getName() {
        return this.customName;
    }

    @Override
    public String getShortName() {
        return shortName;
    }
}
