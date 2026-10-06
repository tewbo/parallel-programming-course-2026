package org.example;

import java.util.Arrays;

public class MetricsCollectorImpl implements MetricsCollector {
    long[] buckets = new long[256];
    long count = 0;
    long sum = 0;
    long min = Long.MAX_VALUE;
    long max = 0;

    @Override
    public void record(long value) {
        buckets[(int) Math.min(255L, value / 4)]++;
        count++;
        sum += value;
        min = Math.min(value, min);
        max = Math.max(value, max);
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(Arrays.stream(buckets).toArray(),
                count,
                sum,
                min,
                max,
                Percentiles.compute(buckets, count, 0.5),
                Percentiles.compute(buckets, count, 0.99));
    }

}
