package com.aegispay.orchestrator.saga;

public final class SagaSteps {
    public static final String RESERVE_BALANCE  = "RESERVE_BALANCE";
    public static final String ASSESS_RISK      = "ASSESS_RISK";
    public static final String PROCESS_PAYMENT  = "PROCESS_PAYMENT";
    public static final String COMMIT_BALANCE   = "COMMIT_BALANCE";
    private SagaSteps() {}
}
