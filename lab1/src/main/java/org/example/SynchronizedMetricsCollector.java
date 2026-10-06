package org.example;

public class SynchronizedMetricsCollector implements MetricsCollector {
    private final MetricsCollector delegate;

    public SynchronizedMetricsCollector(MetricsCollector delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized void record(long value) {
        delegate.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return delegate.snapshot();
    }
}
