package no.ssb.saga.execution;

import no.ssb.concurrent.futureselector.SelectableThreadPoolExectutor;
import no.ssb.saga.api.Saga;
import no.ssb.saga.execution.adapter.AbortSagaException;
import no.ssb.saga.execution.adapter.Adapter;
import no.ssb.saga.execution.adapter.AdapterLoader;
import no.ssb.saga.execution.sagalog.SagaLogEntry;
import no.ssb.saga.execution.sagalog.SagaLogEntryType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public class SagaExecutionTest {

    private static final AtomicLong nextWorkerId = new AtomicLong(1);

    private static final SelectableThreadPoolExectutor executorService = new SelectableThreadPoolExectutor(
            5, 20,
            60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("execution-test-worker-" + nextWorkerId.getAndIncrement());
                thread.setUncaughtExceptionHandler((t, e) -> {
                    System.err.println("Uncaught exception in thread " + thread.getName());
                    e.printStackTrace();
                });
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private static final AdapterLoader adapterLoader = new AdapterLoader()
            .register(new SagaAdapterGeneric())
            .register(new Adapter(Object.class, "rollback-trigger-action", (sagaInput, dependeesOutput) -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                throw new AbortSagaException();
            }));

    private void executeAndVerifyThatActionsWereExecuted(String requestData, Saga saga) {
        AtomicBoolean rollbackRecoveryRun = new AtomicBoolean(false);
        InMemorySagaLog sagaLog = new InMemorySagaLog() {
            @Override
            public String write(SagaLogEntry entry) {
                if (SagaLogEntryType.Abort == entry.entryType) {
                    rollbackRecoveryRun.set(true);
                }
                System.out.println(entry);
                return super.write(entry);
            }
        };
        SagaExecution sagaExecution = new SagaExecution(sagaLog, executorService, saga, adapterLoader);
        String executionId = UUID.randomUUID().toString();
        SagaHandoffControl handoffControl = sagaExecution.executeSaga(executionId, requestData, false, r -> {
        });
        try {
            handoffControl.getCompletionFuture().get(10, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        Map<String, List<SagaLogEntry>> sagaLogEntriesByNodeId = sagaLog.getSnapshotOfSagaLogEntriesByNodeId(executionId);

        if (rollbackRecoveryRun.get()) {

            /*
             * Check Distributed-Saga-Guarantee: A subset of requests and the corresponding
             * compensating requests were executed.
             */

            // Check that start and end saga was logged
            List<SagaLogEntry> startEntries = sagaLogEntriesByNodeId.get(Saga.ID_START);
            assertEquals(startEntries.size(), 1);
            assertEquals(startEntries.get(0).entryType, SagaLogEntryType.Start);
            List<SagaLogEntry> endEntries = sagaLogEntriesByNodeId.get(Saga.ID_END);
            assertEquals(endEntries.size(), 1);
            assertEquals(endEntries.get(0).entryType, SagaLogEntryType.End);

            // Check that those nodes that were logged with at least {Start} were also logged with either {Abort} or both {End,Comp}
            for (Map.Entry<String, List<SagaLogEntry>> entry : sagaLogEntriesByNodeId.entrySet()) {
                if (Saga.ID_START.equals(entry.getKey())) {
                    continue;
                }
                if (Saga.ID_END.equals(entry.getKey())) {
                    continue;
                }
                List<SagaLogEntry> nodeEntries = entry.getValue();
                if (nodeEntries.isEmpty()) {
                    continue;
                }
                if (nodeEntries.stream().anyMatch(sle -> SagaLogEntryType.Start == sle.entryType)) {
                    if (nodeEntries.stream().anyMatch(sle -> SagaLogEntryType.Abort == sle.entryType)) {
                        assertEquals(nodeEntries.size(), 2); // {Start, Abort}
                    } else {
                        if (nodeEntries.stream().anyMatch(sle -> SagaLogEntryType.End == sle.entryType)
                                && nodeEntries.stream().anyMatch(sle -> SagaLogEntryType.Comp == sle.entryType)) {
                            assertEquals(nodeEntries.size(), 3); // {Start, End, Comp}
                        } else {
                            Assert.fail(String.format("Node %s has start entry, it does not have abort, but does not have both End and Comp entry", entry.getKey()));
                        }
                    }
                } else {
                    Assert.fail(String.format("Node %s has entries, but no start entry", entry.getKey()));
                }
            }

            System.out.println("Rollback-recovery saga-execution was legal");

        } else {

            /*
             * Check Distributed-Saga-Guarantee: All requests were completed successfully.
             */

            // Check that start and end saga was logged
            List<SagaLogEntry> startEntries = sagaLogEntriesByNodeId.get(Saga.ID_START);
            assertEquals(startEntries.size(), 1);
            assertEquals(startEntries.get(0).entryType, SagaLogEntryType.Start);
            List<SagaLogEntry> endEntries = sagaLogEntriesByNodeId.get(Saga.ID_END);
            assertEquals(endEntries.size(), 1);
            assertEquals(endEntries.get(0).entryType, SagaLogEntryType.End);

            // Check that all nodes have at least one saga-log entry
            assertEquals(sagaLogEntriesByNodeId.keySet(), saga.nodes().stream().map(n -> n.id).collect(Collectors.toSet()));

            // Check that all nodes (except first and last) in saga were logged with {Start, End}
            for (Map.Entry<String, List<SagaLogEntry>> entry : sagaLogEntriesByNodeId.entrySet()) {
                if (Saga.ID_START.equals(entry.getKey())) {
                    continue;
                }
                if (Saga.ID_END.equals(entry.getKey())) {
                    continue;
                }
                List<SagaLogEntry> nodeEntries = entry.getValue();
                if (nodeEntries.stream().anyMatch(sle -> SagaLogEntryType.Start == sle.entryType)) {
                    if (nodeEntries.stream().anyMatch(sle -> SagaLogEntryType.End == sle.entryType)) {
                        assertEquals(nodeEntries.size(), 2); // {Start, End}
                    } else {
                        Assert.fail(String.format("Node %s has start entry but does not have End entry", entry.getKey()));
                    }
                } else {
                    Assert.fail(String.format("Node %s has entries, but no start entry", entry.getKey()));
                }
            }

            System.out.println("Normal saga-execution was legal");
        }
    }

    @Test
    public void thatEmptySagaExecutes() {
        executeAndVerifyThatActionsWereExecuted("{\"request-data\":\"empty-saga-data\"}", Saga
                .start("The Empty Saga").linkToEnd()
                .end()
        );
    }

    @Test
    public void thatAllActionsFromComplexSagaIsExecuted() {
        executeAndVerifyThatActionsWereExecuted("{\"request-data\":\"complex-saga-data\"}", Saga
                .start("The complex saga").linkTo("A1", "A2")
                .id("A1").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("A2").adapter(SagaAdapterGeneric.NAME).linkTo("B1", "B2")
                .id("B1").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("B2").adapter(SagaAdapterGeneric.NAME).linkTo("C1", "C2", "C3")
                .id("C1").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("C2").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("C3").adapter(SagaAdapterGeneric.NAME).linkTo("B1")
                .end()
        );
    }

    @Test
    public void thatAllCompensatingActionsFromComplexSagaAreExecutedOnRollback() {
        executeAndVerifyThatActionsWereExecuted("{\"request-data\":\"complex-saga-data\"}", Saga
                .start("The complex saga").linkTo("A1", "A2")
                .id("A1").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("A2").adapter("rollback-trigger-action").linkTo("B1", "B2")
                .id("B1").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("B2").adapter(SagaAdapterGeneric.NAME).linkTo("C1", "C2", "C3")
                .id("C1").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("C2").adapter(SagaAdapterGeneric.NAME).linkToEnd()
                .id("C3").adapter(SagaAdapterGeneric.NAME).linkTo("B1")
                .end()
        );
    }
}
