package no.ssb.saga.execution.sagalog.memory;

import no.ssb.saga.execution.sagalog.SagaLog;
import no.ssb.saga.execution.sagalog.SagaLogEntry;
import no.ssb.saga.execution.sagalog.SagaLogEntryBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MemorySagaLog implements SagaLog<Long> {

    private final AtomicLong nextId = new AtomicLong(0);
    private final List<SagaLogEntry<Long>> incompleteEntries = new CopyOnWriteArrayList();

    @Override
    public Stream<SagaLogEntry<Long>> readEntries(String executionId) {
        return incompleteEntries.stream().filter(e -> executionId.equals(e.getExecutionId()));
    }

    @Override
    public CompletableFuture<SagaLogEntry<Long>> write(SagaLogEntryBuilder<Long> builder) {
        synchronized (this) {
            if (builder.id() == null) {
                builder.id(nextId.getAndIncrement());
            }
            SagaLogEntry entry = builder.build();
            incompleteEntries.add(entry);
            return CompletableFuture.completedFuture(entry);
        }
    }

    @Override
    public CompletableFuture<Void> truncate(Long entryId) {
        Set<SagaLogEntry<Long>> toBeRemoved = new LinkedHashSet<>();
        Set<Long> toBeRemovedIds = new LinkedHashSet<>();
        for (SagaLogEntry<Long> sle : incompleteEntries) {
            toBeRemoved.add(sle);
            toBeRemovedIds.add(sle.getId());
            if (entryId.equals(sle.getId())) {
                break;
            }
        }
        if (toBeRemovedIds.contains(entryId)) {
            incompleteEntries.removeAll(toBeRemoved);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Stream<SagaLogEntry<Long>> readIncompleteSagas() {
        return incompleteEntries.stream();
    }
}
