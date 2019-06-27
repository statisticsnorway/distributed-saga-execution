module no.ssb.saga.execution {

    requires java.base;
    requires no.ssb.saga.api;
    requires no.ssb.concurrent.futureselector;
    requires no.ssb.sagalog;

    exports no.ssb.saga.execution;
    exports no.ssb.saga.execution.adapter;
}
