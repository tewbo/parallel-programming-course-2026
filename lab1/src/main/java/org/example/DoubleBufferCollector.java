package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferCollector implements MetricsCollector {
    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(this::registerBuffers);
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();
    private volatile int active;
    private final Object snapLock = new Object();

    private final long[] globalBuckets = new long[256];
    private long globalCount = 0;
    private long globalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = 0;

    @Override
    public void record(long value) {
        var my = myBuffers.get();

        int b;
        while (true) {
            b = active;
            my.inside.set(b);
            if (active == b || true) {
                break;
            }
            my.inside.setRelease(-1);
        }

        int bucket = (int) Math.min(value / 4, 255);
        my.buckets[b][bucket]++;
        my.count[b]++;
        my.sum[b] += value;
        if (value < my.min[b]) {
            my.min[b] = value;
        }
        if (value > my.max[b]) {
            my.max[b] = value;
        }

        my.inside.setRelease(-1);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;

            for (var buffers : allBuffers) {
                while (buffers.inside.get() == old) {
                    Thread.onSpinWait();
                }
                for (int i = 0; i < 256; i++) {
                    globalBuckets[i] += buffers.buckets[old][i];
                }
                globalCount += buffers.count[old];
                globalSum += buffers.sum[old];
                if (buffers.min[old] < globalMin) {
                    globalMin = buffers.min[old];
                }
                if (buffers.max[old] > globalMax) {
                    globalMax = buffers.max[old];
                }

                Arrays.fill(buffers.buckets[old], 0);
                buffers.count[old] = 0;
                buffers.sum[old] = 0;
                buffers.min[old] = Long.MAX_VALUE;
                buffers.max[old] = 0;
            }
        }

        return new Snapshot(Arrays.copyOf(globalBuckets, 256),
                globalCount,
                globalSum,
                globalMin,
                globalMax,
                Percentiles.compute(globalBuckets, globalCount, 0.5),
                Percentiles.compute(globalBuckets, globalCount, 0.99));
    }

    private DoubleBufferCollector.ThreadBuffers registerBuffers() {
        DoubleBufferCollector.ThreadBuffers buffers = new DoubleBufferCollector.ThreadBuffers();
        synchronized (snapLock) {
            allBuffers.add(buffers);
        }
        return buffers;
    }

    static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        final AtomicInteger inside = new AtomicInteger(-1);
    }
}
