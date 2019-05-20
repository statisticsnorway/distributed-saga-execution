package no.ssb.saga.execution.sagalog;

public enum SagaLogEntryType {
    Start,
    End,
    Abort,
    Comp,
    Ignore // Entries of this type should always be ignored, may be used internally by the saga-log.
}
