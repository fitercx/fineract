package com.crediblex.fineract.test.service;

import org.apache.fineract.test.data.job.JobResolver;
import org.apache.fineract.test.service.JobService;
import org.springframework.stereotype.Component;

@Component
public class CustomJobService extends JobService {

    public CustomJobService(JobResolver jobResolver) {
        super(jobResolver);
    }
}
