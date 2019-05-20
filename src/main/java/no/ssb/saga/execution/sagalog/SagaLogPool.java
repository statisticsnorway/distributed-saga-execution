package no.ssb.saga.execution.sagalog;

public interface SagaLogPool {

    SagaLog connect(String logId);

    void release(String logId);
}
