package com.aegispay.orchestrator.saga;

public final class SagaStatus {
    public static final String RUNNING      = "RUNNING";
    public static final String COMPLETED    = "COMPLETED";
    public static final String FAILED       = "FAILED";
    public static final String COMPENSATING = "COMPENSATING";
    public static final String ROLLED_BACK  = "ROLLED_BACK";  // internal audit only
    private SagaStatus() {}
}
