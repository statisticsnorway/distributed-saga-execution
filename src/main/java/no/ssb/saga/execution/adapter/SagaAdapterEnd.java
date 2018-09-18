package no.ssb.saga.execution.adapter;

import no.ssb.saga.api.Saga;

class SagaAdapterEnd extends Adapter<Object> {

    public SagaAdapterEnd() {
        super(Object.class, Saga.ADAPTER_END);
    }
}
