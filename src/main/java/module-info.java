module no.ssb.saga.execution {
    requires java.base;
    requires no.ssb.saga.api;
    requires no.ssb.concurrent.futureselector;
    exports no.ssb.saga.execution;
    exports no.ssb.saga.execution.adapter;
    exports no.ssb.saga.execution.sagalog;
}
