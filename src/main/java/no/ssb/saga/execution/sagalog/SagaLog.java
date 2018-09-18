package no.ssb.saga.execution.sagalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface SagaLog {

    String write(SagaLogEntry entry);

    List<SagaLogEntry> readEntries(String executionId);

    default Map<String, List<SagaLogEntry>> getSnapshotOfSagaLogEntriesByNodeId(String executionId) {
        Map<String, List<SagaLogEntry>> recoverySagaLogEntriesBySagaNodeId = new LinkedHashMap<>();
        List<SagaLogEntry> entries = readEntries(executionId);
        for (SagaLogEntry entry : entries) {
            List<SagaLogEntry> nodeEntries = recoverySagaLogEntriesBySagaNodeId.get(entry.nodeId);
            if (nodeEntries == null) {
                nodeEntries = new ArrayList<>(4);
                recoverySagaLogEntriesBySagaNodeId.put(entry.nodeId, nodeEntries);
            }
            nodeEntries.add(entry);
        }
        return recoverySagaLogEntriesBySagaNodeId;
    }

}
