package com.oracle.jobrunr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class MyJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyJob.class);


    private static final AtomicInteger lastLoggedPercent = new AtomicInteger(0);

    public void doWork(int totalAmountOfJobs, int index) {
        int percentComplete = (int) (((double) (index + 1) / totalAmountOfJobs) * 100);

        // Log a message when a new percentage is reached.
        if(lastLoggedPercent.compareAndSet(percentComplete - 1, percentComplete)) {
            LOGGER.info("Progress: {}% completed ({} of {} jobs).", percentComplete, index, totalAmountOfJobs);
        }
    }
}
