package org.example;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ConsistencyStressTest {
    public static void run(Supplier<MetricsCollector> collectorSupplier, int threadCount) {
        var collector = collectorSupplier.get();
        long[] values = Generator.generateValues();
        var stop = new AtomicBoolean(false);
        var result = new long[threadCount];

        int SNAPSHOT_COUNT = 10_000;
        int sumGreaterThanCount = 0;
        int sumLessThanCount = 0;

        try (var executorService = Executors.newFixedThreadPool(threadCount)) {
            for (int k = 0; k < threadCount; k++) {
                int finalK = k;
                executorService.submit(() -> {
                    long records = 0;
                    int i = finalK * 1000;
                    while (!stop.get()) {
                        collector.record(values[i++]);
                        records++;
                        if (i == values.length) {
                            i = 0;
                        }
                    }
                    result[finalK] = records;
                });
            }

            for (int i = 0; i < 10_000; i++) {
                var snapshot = collector.snapshot();
                var bucketsSum = Arrays.stream(snapshot.buckets()).sum();
                if (bucketsSum > snapshot.count()) {
                    sumGreaterThanCount++;
                } else if (bucketsSum < snapshot.count()) {
                    sumLessThanCount++;
                }
            }
            stop.set(true);

            int wrongTotal = sumGreaterThanCount + sumLessThanCount;
            System.out.println("Sum less than count: " + (double) sumLessThanCount / SNAPSHOT_COUNT);
            System.out.println("Sum greater than count: " + (double) sumGreaterThanCount / SNAPSHOT_COUNT);
            System.out.println("Total: " + (double) wrongTotal / SNAPSHOT_COUNT);
        }

        var sumAfterAll = Arrays.stream(result).sum();
        System.out.println("Sum after all: " + sumAfterAll);
        var countAfterAll = collector.snapshot().count();
        System.out.println("Count after all (real): " + countAfterAll);
    }
}
