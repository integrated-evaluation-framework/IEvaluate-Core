package edu.mayo.dhs.ievaluate.core.jobs;

import edu.mayo.dhs.ievaluate.api.models.jobs.ApplicationJob;

/**
 * A default implementation of an application job that utilizes RESTful endpoints for execution and state updates.
 *
 * All REST endpoints must contain the macro %JOB_UID% which will be substituted with the appropriate {@link #jobUID}
 */
public abstract class RESTfulApplicationJob extends ApplicationJob {

    private String jobStatusRestEndpoint;

    public RESTfulApplicationJob(String jobStatusRestEndpoint) {
        this.jobStatusRestEndpoint = jobStatusRestEndpoint;
    }
}
