package no.ssb.saga.execution.sagalog;

import no.ssb.saga.api.Saga;

public class SagaLogEntry {

    public static SagaLogEntry startSaga(String executionId, String sagaName, String sagaInputJson) {
        return new SagaLogEntry(executionId, SagaLogEntryType.Start, Saga.ID_START, sagaName, sagaInputJson);
    }

    public static SagaLogEntry startAction(String executionId, String nodeId) {
        return new SagaLogEntry(executionId, SagaLogEntryType.Start, nodeId, null, null);
    }

    public static SagaLogEntry endAction(String executionId, String nodeId, String actionOutputJson) {
        return new SagaLogEntry(executionId, SagaLogEntryType.End, nodeId, null, actionOutputJson);
    }

    public static SagaLogEntry endSaga(String executionId) {
        return new SagaLogEntry(executionId, SagaLogEntryType.End, Saga.ID_END, null, null);
    }

    public static SagaLogEntry abort(String executionId, String nodeId) {
        return new SagaLogEntry(executionId, SagaLogEntryType.Abort, nodeId, null, null);
    }

    public static SagaLogEntry compDone(String executionId, String nodeId) {
        return new SagaLogEntry(executionId, SagaLogEntryType.Comp, nodeId, null, null);
    }

    public static SagaLogEntry from(String line) {

        // mandatory log-fields

        int executionIdEndIndex = line.indexOf(' ');
        String executionId = line.substring(0, executionIdEndIndex);
        line = line.substring(executionIdEndIndex + 1);

        int entryTypeEndIndex = line.indexOf(' ');
        SagaLogEntryType entryType = SagaLogEntryType.valueOf(line.substring(0, entryTypeEndIndex));
        line = line.substring(entryTypeEndIndex + 1);

        int nodeIdEndIdex = line.indexOf(' ');
        if (nodeIdEndIdex == -1) {
            String nodeId = line;
            return new SagaLogEntry(executionId, entryType, nodeId, null, null);
        }

        String nodeId = line.substring(0, nodeIdEndIdex);
        line = line.substring(nodeIdEndIdex + 1);

        // optional log-fields
        if (Saga.ID_START.equals(nodeId)) {
            int jsonDataBeginIndex = line.indexOf('{');
            if (jsonDataBeginIndex == -1) {
                String sagaName = line.substring(0, line.length() - 1);
                return new SagaLogEntry(executionId, entryType, nodeId, sagaName, null);
            }
            String sagaName = line.substring(0, jsonDataBeginIndex - 1);
            String jsonData = line.substring(jsonDataBeginIndex);
            return new SagaLogEntry(executionId, entryType, nodeId, sagaName, jsonData);
        }

        int jsonDataBeginIndex = line.indexOf('{');
        if (jsonDataBeginIndex == -1) {
            return new SagaLogEntry(executionId, entryType, nodeId, null, null);
        }
        String jsonData = line.substring(jsonDataBeginIndex);
        return new SagaLogEntry(executionId, entryType, nodeId, null, jsonData);
    }

    public final String executionId;
    public final SagaLogEntryType entryType;
    public final String nodeId;
    public final String sagaName;
    public final String jsonData;

    public SagaLogEntry(String executionId, SagaLogEntryType entryType, String nodeId, String sagaName, String jsonData) {
        this.executionId = executionId;
        this.entryType = entryType;
        this.nodeId = nodeId;
        this.sagaName = sagaName;
        this.jsonData = jsonData;
    }

    @Override
    public String toString() {
        return executionId + ' ' + entryType + ' ' + nodeId + (sagaName == null ? "" : ' ' + sagaName) + (jsonData == null ? "" : ' ' + jsonData);
    }
}
