package no.ssb.saga.execution;

import no.ssb.saga.api.SagaNode;

import java.util.Deque;
import java.util.Map;

public class SagaTraversalElement {
    public final Map<SagaNode, Object> outputByNode;
    public final Deque<SagaNode> ancestors;
    public final SagaNode node;

    public SagaTraversalElement(Map<SagaNode, Object> outputByNode, Deque<SagaNode> ancestors, SagaNode node) {
        this.outputByNode = outputByNode;
        this.ancestors = ancestors;
        this.node = node;
    }
}
