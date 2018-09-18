package no.ssb.saga.execution;

import no.ssb.saga.execution.sagalog.SagaLog;
import no.ssb.saga.execution.sagalog.SagaLogEntry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemorySagaLog implements SagaLog {

    private final List<SagaLogEntry> entries = new CopyOnWriteArrayList();

    @Override
    public List<SagaLogEntry> readEntries(String executionId) {
        return entries;
    }

    @Override
    public String write(SagaLogEntry entry) {
        entries.add(entry);
        return String.valueOf(System.currentTimeMillis());
    }
}
