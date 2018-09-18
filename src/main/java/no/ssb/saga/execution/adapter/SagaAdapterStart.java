package no.ssb.saga.execution.adapter;

import no.ssb.saga.api.Saga;

class SagaAdapterStart extends Adapter<Object> {

    public SagaAdapterStart() {
        super(Object.class, Saga.ADAPTER_START);
    }
}
