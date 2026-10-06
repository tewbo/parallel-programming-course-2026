package org.example;

public interface MetricsCollector {
    void record(long value);
    Snapshot snapshot();
}