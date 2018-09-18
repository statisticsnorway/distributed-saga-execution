package no.ssb.saga.execution;

import no.ssb.concurrent.futureselector.SelectableFuture;
import no.ssb.saga.api.Saga;
import no.ssb.saga.execution.adapter.AdapterLoader;
import no.ssb.saga.execution.sagalog.SagaLog;

public class SagaHandoffControl {
    private final Object sagaInput;
    private final String executionId;
    private final Saga saga;
    private final SagaLog sagaLog;
    private final AdapterLoader adapterLoader;
    private final SagaTraversalResult traversalResult;
    private final SelectableFuture<SagaHandoffResult> handoffFuture;
    private final SelectableFuture<SagaHandoffResult> completionFuture;

    SagaHandoffControl(Object sagaInput, String executionId, Saga saga, SagaLog sagaLog, AdapterLoader adapterLoader, SagaTraversalResult traversalResult, SelectableFuture<SagaHandoffResult> handoffFuture, SelectableFuture<SagaHandoffResult> completionFuture) {
        this.sagaInput = sagaInput;
        this.executionId = executionId;
        this.saga = saga;
        this.sagaLog = sagaLog;
        this.adapterLoader = adapterLoader;
        this.traversalResult = traversalResult;
        this.handoffFuture = handoffFuture;
        this.completionFuture = completionFuture;
    }

    public Object getSagaInput() {
        return sagaInput;
    }

    public String getExecutionId() {
        return executionId;
    }

    public Saga getSaga() {
        return saga;
    }

    public SagaLog getSagaLog() {
        return sagaLog;
    }

    public AdapterLoader getAdapterLoader() {
        return adapterLoader;
    }

    public SagaTraversalResult getTraversalResult() {
        return traversalResult;
    }

    public SelectableFuture<SagaHandoffResult> getHandoffFuture() {
        return handoffFuture;
    }

    public SelectableFuture<SagaHandoffResult> getCompletionFuture() {
        return completionFuture;
    }
}
