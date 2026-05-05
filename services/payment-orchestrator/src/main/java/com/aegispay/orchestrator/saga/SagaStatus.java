package com.aegispay.orchestrator.saga;

public final class SagaStatus {
    public static final String RUNNING      = "RUNNING";
    public static final String COMPLETED    = "COMPLETED";
    public static final String FAILED       = "FAILED";
    public static final String COMPENSATING = "COMPENSATING";
    private SagaStatus() {}
}
