package no.ssb.saga.execution.sagalog;

import no.ssb.saga.api.Saga;

public class SagaLogEntryBuilder<ID> {

    ID id;
    String executionId;
    SagaLogEntryType entryType;
    String nodeId;
    String sagaName;
    String jsonData;

    public SagaLogEntryBuilder() {
    }

    public SagaLogEntry<ID> build() {
        return new SagaLogEntry(id, executionId, entryType, nodeId, sagaName, jsonData);
    }

    public SagaLogEntryBuilder<ID> startSaga(String executionId, String sagaName, String sagaInputJson) {
        return executionId(executionId).entryType(SagaLogEntryType.Start).nodeId(Saga.ID_START).sagaName(sagaName).jsonData(sagaInputJson);
    }

    public SagaLogEntryBuilder<ID> startAction(String executionId, String nodeId) {
        return executionId(executionId).entryType(SagaLogEntryType.Start).nodeId(nodeId);
    }

    public SagaLogEntryBuilder<ID> endAction(String executionId, String nodeId, String actionOutputJson) {
        return executionId(executionId).entryType(SagaLogEntryType.End).nodeId(nodeId).jsonData(actionOutputJson);
    }

    public SagaLogEntryBuilder<ID> endSaga(String executionId) {
        return executionId(executionId).entryType(SagaLogEntryType.End).nodeId(Saga.ID_END);
    }

    public SagaLogEntryBuilder<ID> abort(String executionId, String nodeId) {
        return executionId(executionId).entryType(SagaLogEntryType.Abort).nodeId(nodeId);
    }

    public SagaLogEntryBuilder<ID> compDone(String executionId, String nodeId) {
        return executionId(executionId).entryType(SagaLogEntryType.Comp).nodeId(nodeId);
    }

    public SagaLogEntryBuilder<ID> id(ID id) {
        this.id = id;
        return this;
    }

    public SagaLogEntryBuilder<ID> executionId(String executionId) {
        this.executionId = executionId;
        return this;
    }

    public SagaLogEntryBuilder<ID> entryType(SagaLogEntryType entryType) {
        this.entryType = entryType;
        return this;
    }

    public SagaLogEntryBuilder<ID> nodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public SagaLogEntryBuilder<ID> sagaName(String sagaName) {
        this.sagaName = sagaName;
        return this;
    }

    public SagaLogEntryBuilder<ID> jsonData(String jsonData) {
        this.jsonData = jsonData;
        return this;
    }

    public ID id() {
        return id;
    }

    public String executionId() {
        return executionId;
    }

    public SagaLogEntryType entryType() {
        return entryType;
    }

    public String nodeId() {
        return nodeId;
    }

    public String sagaName() {
        return sagaName;
    }

    public String jsonData() {
        return jsonData;
    }

    @Override
    public String toString() {
        return "SagaLogEntryBuilder{" +
                "id=" + id +
                ", executionId='" + executionId + '\'' +
                ", entryType=" + entryType +
                ", nodeId='" + nodeId + '\'' +
                ", sagaName='" + sagaName + '\'' +
                ", jsonData='" + jsonData + '\'' +
                '}';
    }
}
