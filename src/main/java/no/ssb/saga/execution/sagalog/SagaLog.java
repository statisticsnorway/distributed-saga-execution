package no.ssb.saga.execution.sagalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface SagaLog<ID> {

    default SagaLogEntryBuilder<ID> builder() {
        return new SagaLogEntryBuilder<>();
    }

    CompletableFuture<SagaLogEntry<ID>> write(SagaLogEntryBuilder<ID> builder);

    CompletableFuture<Void> truncate(ID id);

    Stream<SagaLogEntry<ID>> readIncompleteSagas();

    Stream<SagaLogEntry<ID>> readEntries(String executionId);

    default Map<String, List<SagaLogEntry<ID>>> getSnapshotOfSagaLogEntriesByNodeId(String executionId) {
        Map<String, List<SagaLogEntry<ID>>> recoverySagaLogEntriesBySagaNodeId = new LinkedHashMap<>();
        List<SagaLogEntry<ID>> entries = readEntries(executionId).collect(Collectors.toList());
        for (SagaLogEntry<ID> entry : entries) {
            List<SagaLogEntry<ID>> nodeEntries = recoverySagaLogEntriesBySagaNodeId.get(entry.getNodeId());
            if (nodeEntries == null) {
                nodeEntries = new ArrayList<>(4);
                recoverySagaLogEntriesBySagaNodeId.put(entry.getNodeId(), nodeEntries);
            }
            nodeEntries.add(entry);
        }
        return recoverySagaLogEntriesBySagaNodeId;
    }
}
