package com.crediblex.fineract.test.stepdef;

import com.crediblex.fineract.test.data.job.CustomDefaultJob;
import com.crediblex.fineract.test.service.CustomJobService;
import io.cucumber.java.en.And;
import org.springframework.beans.factory.annotation.Autowired;

public class CustomSchedulerStepDef {

    @Autowired
    private CustomJobService jobService;

    @And("Admin runs Apply penalty to overdue loans")
    public void runPeriodicAccrualTransaction() {
        jobService.executeAndWait(CustomDefaultJob.APPLY_OVERDUE_PENALTY_JOB);
    }

    @And("Admin runs Update Loan Arrears Ageing")
    public void runUpdateLoanArrearsAgeing() {
        jobService.executeAndWait(CustomDefaultJob.UPDATE_LOAN_ARREARS_AGEING_JOB);
    }
}
