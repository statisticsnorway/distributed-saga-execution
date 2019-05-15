package no.ssb.saga.execution;

import no.ssb.concurrent.futureselector.SelectableFuture;
import no.ssb.concurrent.futureselector.SelectableThreadPoolExectutor;
import no.ssb.saga.api.Saga;
import no.ssb.saga.api.SagaNode;
import no.ssb.saga.execution.adapter.AbortSagaException;
import no.ssb.saga.execution.adapter.AdapterLoader;
import no.ssb.saga.execution.adapter.SagaAdapter;
import no.ssb.saga.execution.sagalog.SagaLog;
import no.ssb.saga.execution.sagalog.SagaLogEntry;
import no.ssb.saga.execution.sagalog.SagaLogEntryType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

public class SagaExecution<SAGA_LOG_ID> {

    private final SagaLog<SAGA_LOG_ID> sagaLog;
    private final SelectableThreadPoolExectutor executorService;
    private final Saga saga;
    private final AdapterLoader adapterLoader;

    public SagaExecution(SagaLog<SAGA_LOG_ID> sagaLog, SelectableThreadPoolExectutor executorService, Saga saga, AdapterLoader adapterLoader) {
        this.sagaLog = sagaLog;
        this.executorService = executorService;
        this.saga = saga;
        this.adapterLoader = adapterLoader;
    }

    public SagaLog<SAGA_LOG_ID> getSagaLog() {
        return sagaLog;
    }

    public SelectableThreadPoolExectutor getExecutorService() {
        return executorService;
    }

    public Saga getSaga() {
        return saga;
    }

    public AdapterLoader getAdapterLoader() {
        return adapterLoader;
    }

    /**
     * @param sagaInput the data to pass as input to start-node.
     * @param recovery
     * @return
     */
    public SagaHandoffControl executeSaga(String executionId, Object sagaInput, boolean recovery, Consumer<SagaHandoffResult> onComplete) {
        SelectableFuture<SagaHandoffResult> handoffFuture = new SelectableFuture<>(null);
        SelectableFuture<SagaHandoffResult> completionFuture = new SelectableFuture<>(null);
        SagaTraversal sagaTraversal = new SagaTraversal(executorService, saga);
        CompletableFuture<SagaTraversalResult> futureTraversalResult = new CompletableFuture<>();
        Map<String, List<SagaLogEntry<SAGA_LOG_ID>>> recoverySagaLogEntriesBySagaNodeId;
        if (recovery) {
            recoverySagaLogEntriesBySagaNodeId = sagaLog.getSnapshotOfSagaLogEntriesByNodeId(executionId);
        } else {
            recoverySagaLogEntriesBySagaNodeId = Collections.emptyMap();
        }
        SagaTraversalResult traversalResult = sagaTraversal.forward(handoffFuture, completionFuture, ste -> {
            SagaAdapter adapter = adapterLoader.load(ste.node);
            if (Saga.ID_START.equals(ste.node.id)) {
                if (!recovery) {
                    String serializedSagaInput = ofNullable(sagaInput)
                            .map(i -> adapter.serializer().serialize(i)) // safe unchecked call
                            .orElse(null);
                    sagaLog.write(sagaLog.builder().startSaga(executionId, saga.name, serializedSagaInput));
                }
                handoffFuture.complete(new SagaHandoffResult(executionId));
                return null;
            }
            if (Saga.ID_END.equals(ste.node.id)) {
                sagaLog.write(sagaLog.builder().endSaga(executionId));
                SagaHandoffResult result = new SagaHandoffResult(executionId);
                completionFuture.complete(result);
                onComplete.accept(result);
                return null;
            }
            List<SagaLogEntry<SAGA_LOG_ID>> sagaLogEntries = recoverySagaLogEntriesBySagaNodeId.get(ste.node.id);
            if (sagaLogEntries == null || sagaLogEntries.isEmpty()) {
                sagaLog.write(sagaLog.builder().startAction(executionId, ste.node.id));
            }
            if (sagaLogEntries != null && sagaLogEntries.stream().anyMatch(e -> SagaLogEntryType.End == e.getEntryType())) {
                // action was already executed, return output from saga-log
                return sagaLogEntries.stream()
                        .filter(e -> SagaLogEntryType.End == e.getEntryType())
                        .findFirst()
                        .map(sle -> sle.getJsonData())
                        .map(jsonData -> adapter.serializer().deserialize(jsonData))
                        .orElse(null);
            }
            Object actionOutput;
            try {
                actionOutput = adapter.executeAction(sagaInput, ste.outputByNode);

            } catch (AbortSagaException e) {
                boolean firstToAbort = sagaTraversal.stopTraversal();
                sagaLog.write(sagaLog.builder().abort(executionId, ste.node.id));
                if (!firstToAbort) {
                    return null; // More than one abort, let only first abort trigger rollback-recovery
                }
                SagaTraversalResult sagaTraversalResult;
                try {
                    sagaTraversalResult = futureTraversalResult.get();
                } catch (InterruptedException | ExecutionException e1) {
                    throw new RuntimeException(e1);
                }

                executorService.submit(() -> {
                    // ensure we catch all saga-log entries of forward traversal before running recovery
                    sagaTraversalResult.waitForThreadWalksToComplete();

                    rollbackRecovery(e, executionId, sagaInput, completionFuture, sagaTraversalResult.pendingWalks, sagaTraversalResult.futureThreadWalk, new ConcurrentHashMap<>(), onComplete);
                });

                return null;
            }

            String serializedActionOutput = ofNullable(actionOutput)
                    .map(o -> adapter.serializer().serialize(o)) // safe unchecked call
                    .orElse(null);
            sagaLog.write(sagaLog.builder().endAction(executionId, ste.node.id, serializedActionOutput));

            return actionOutput;
        });
        futureTraversalResult.complete(traversalResult);
        return new SagaHandoffControl(sagaInput, executionId, saga, sagaLog, adapterLoader, traversalResult, handoffFuture, completionFuture);
    }

    private void rollbackRecovery(AbortSagaException exception,
                                  String executionId,
                                  Object sagaInput,
                                  SelectableFuture<SagaHandoffResult> completionFuture,
                                  AtomicInteger pendingWalks,
                                  BlockingQueue<SelectableFuture<List<String>>> futureThreadWalk,
                                  ConcurrentHashMap<String, SelectableFuture<SelectableFuture<Object>>> futureById,
                                  Consumer<SagaHandoffResult> onComplete) {
        Map<String, List<SagaLogEntry<SAGA_LOG_ID>>> sagaLogEntriesBySagaNodeId = sagaLog.getSnapshotOfSagaLogEntriesByNodeId(executionId);
        SagaTraversal sagaTraversal = new SagaTraversal(executorService, saga);
        sagaTraversal.backward(null, completionFuture, pendingWalks, futureThreadWalk, futureById, ste -> {
            if (Saga.ID_END.equals(ste.node.id)) {
                return null;
            }
            if (Saga.ID_START.equals(ste.node.id)) {
                sagaLog.write(sagaLog.builder().endSaga(executionId));
                SagaHandoffResult result = new SagaHandoffResult(executionId);
                completionFuture.completeExceptionally(exception);
                // completionFuture.complete(result);
                onComplete.accept(result);
                return null;
            }
            SagaAdapter adapter = adapterLoader.load(ste.node);
            List<SagaLogEntry<SAGA_LOG_ID>> sagaLogEntries = sagaLogEntriesBySagaNodeId.get(ste.node.id);
            if (sagaLogEntries == null || sagaLogEntries.isEmpty()) {
                return null;
            }
            if (sagaLogEntries != null && sagaLogEntries.stream().anyMatch(e -> SagaLogEntryType.Abort == e.getEntryType())) {
                return null; // abort action
            }
            Object actionOutput;
            if (sagaLogEntries != null && sagaLogEntries.stream().anyMatch(e -> SagaLogEntryType.End == e.getEntryType())) {
                // action was already executed, use its output to cancel
                actionOutput = sagaLogEntries.stream()
                        .filter(e -> SagaLogEntryType.End == e.getEntryType())
                        .findFirst()
                        .map(sle -> sle.getJsonData())
                        .map(jsonData -> adapter.serializer().deserialize(jsonData))
                        .orElse(null);
            } else {
                // Unknown whether action was executed or not, execute it (possibly again)
                // to ensure consistency and to update saga-log with end-action and output.
                Map<SagaNode, Object> dependeesOutput = getDependeesOutputByNode(sagaLogEntriesBySagaNodeId, ste.node, adapter);
                actionOutput = adapter.executeAction(sagaInput, dependeesOutput);
                String serializedActionOutput = adapter.serializer().serialize(actionOutput); // safe unchecked call
                sagaLog.write(sagaLog.builder().endAction(executionId, ste.node.id, serializedActionOutput));
            }
            adapter.executeCompensatingAction(sagaInput, actionOutput); // safe unchecked call
            sagaLog.write(sagaLog.builder().compDone(executionId, ste.node.id));
            return null; // no output from running compensating action.
        });
    }

    private Map<SagaNode, Object> getDependeesOutputByNode
            (Map<String, List<SagaLogEntry<SAGA_LOG_ID>>> sagaLogEntriesBySagaNodeId, SagaNode node, SagaAdapter adapter) {
        Map<SagaNode, Object> dependeesOutputByNode = null;
        for (SagaNode dependeeNode : node.incoming) {
            List<SagaLogEntry<SAGA_LOG_ID>> dependeeEntries = sagaLogEntriesBySagaNodeId.get(dependeeNode.id);
            Object dependeeOutput = dependeeEntries.stream()
                    .filter(e -> SagaLogEntryType.End == e.getEntryType())
                    .findFirst()
                    .map(sle -> sle.getJsonData())
                    .map(jsonData -> adapter.serializer().deserialize(jsonData))
                    .orElse(null);
            dependeesOutputByNode.put(dependeeNode, dependeeOutput);
        }
        return dependeesOutputByNode;
    }
}
