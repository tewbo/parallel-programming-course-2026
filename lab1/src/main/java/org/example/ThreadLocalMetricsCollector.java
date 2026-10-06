package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalMetricsCollector implements MetricsCollector {
    private static final int BUCKET_COUNT = 256;

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();
    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(this::registerState);

    @Override
    public void record(long value) {
        ThreadState state = myState.get();
        int bucket = (int) Math.min(value / 4, BUCKET_COUNT - 1);

        state.buckets.setRelease(bucket, state.buckets.getPlain(bucket) + 1);
        state.count.setRelease(state.count.getPlain() + 1);
        state.sum.setRelease(state.sum.getPlain() + value);

        if (value < state.min.getPlain()) {
            state.min.setRelease(value);
        }
        if (value > state.max.getPlain()) {
            state.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> states;
        synchronized (listLock) {
            states = List.copyOf(allStates);
        }

        long[] buckets = new long[BUCKET_COUNT];
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        for (ThreadState state : states) {
            for (int i = 0; i < BUCKET_COUNT; i++) {
                buckets[i] += state.buckets.get(i);
            }
            count += state.count.get();
            sum += state.sum.get();
            min = Math.min(min, state.min.get());
            max = Math.max(max, state.max.get());
        }

        return new Snapshot(buckets,
                count,
                sum,
                min,
                max,
                Percentiles.compute(buckets, count, 0.5),
                Percentiles.compute(buckets, count, 0.99));
    }

    private ThreadState registerState() {
        ThreadState state = new ThreadState();
        synchronized (listLock) {
            allStates.add(state);
        }
        return state;
    }

    private static final class ThreadState {
        private final AtomicLongArray buckets = new AtomicLongArray(BUCKET_COUNT);
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong sum = new AtomicLong();
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(0);
    }
}
