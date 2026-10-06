package org.example;

final class Percentiles {
    private static final int BUCKET_WIDTH = 4;

    private Percentiles() {
    }

    static long compute(long[] buckets, long count, double quantile) {
        long threshold = (long) (count * quantile);
        long accumulator = 0;
        for (int bucket = 0; bucket < buckets.length; bucket++) {
            accumulator += buckets[bucket];
            if (accumulator > threshold) {
                return (long) bucket * BUCKET_WIDTH;
            }
        }
        return (long) (buckets.length - 1) * BUCKET_WIDTH;
    }
}
