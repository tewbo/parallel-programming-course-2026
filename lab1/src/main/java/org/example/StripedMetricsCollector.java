package org.example;

import java.util.concurrent.atomic.AtomicLong;

public class StripedMetricsCollector implements MetricsCollector {
    private static final int BUCKET_COUNT = 256;
    private static final int STRIPE_COUNT = 16;
    private static final int BUCKETS_PER_STRIPE = BUCKET_COUNT / STRIPE_COUNT;

    private final Stripe[] stripes = new Stripe[STRIPE_COUNT];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(0);

    public StripedMetricsCollector() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new Stripe();
        }
    }

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, BUCKET_COUNT - 1);
        Stripe stripe = stripes[bucket % STRIPE_COUNT];
        synchronized (stripe) {
            stripe.buckets[bucket / STRIPE_COUNT]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);
        updateMin(value);
        updateMax(value);
    }

    @Override
    public Snapshot snapshot() {
        long[] buckets = new long[BUCKET_COUNT];
        for (int stripeIndex = 0; stripeIndex < STRIPE_COUNT; stripeIndex++) {
            Stripe stripe = stripes[stripeIndex];
            synchronized (stripe) {
                for (int i = 0; i < BUCKETS_PER_STRIPE; i++) {
                    buckets[i * STRIPE_COUNT + stripeIndex] = stripe.buckets[i];
                }
            }
        }

        long countValue = count.get();
        return new Snapshot(buckets,
                countValue,
                sum.get(),
                min.get(),
                max.get(),
                Percentiles.compute(buckets, countValue, 0.5),
                Percentiles.compute(buckets, countValue, 0.99));
    }

    private void updateMin(long value) {
        long current = min.get();
        while (value < current && !min.compareAndSet(current, value)) {
            current = min.get();
        }
    }

    private void updateMax(long value) {
        long current = max.get();
        while (value > current && !max.compareAndSet(current, value)) {
            current = max.get();
        }
    }

    private static final class Stripe {
        private final long[] buckets = new long[BUCKETS_PER_STRIPE];
    }
}
