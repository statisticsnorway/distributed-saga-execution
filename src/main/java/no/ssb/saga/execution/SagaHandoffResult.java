package no.ssb.saga.execution;

public class SagaHandoffResult {
    public final String executionId;

    public SagaHandoffResult(String executionId) {
        this.executionId = executionId;
    }
}
