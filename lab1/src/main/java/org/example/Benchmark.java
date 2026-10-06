package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Benchmark {
    public long run(MetricsCollector collector, long[] values, int threadCount, int seconds) throws InterruptedException {
        long t0;
        long t1;

        var latch = new CountDownLatch(1);
        var stop = new AtomicBoolean(false);
        var ops = new long[threadCount];
        System.out.println("Start");
        try (var executorService = Executors.newFixedThreadPool(threadCount)) {
            for (int k = 0; k < threadCount; k++) {
                int finalK = k;
                executorService.submit(() -> {
                    long localCount = 0;
                    int i = finalK * 1000;
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    while (!stop.get()) {
                        collector.record(values[i]);
                        localCount++;
                        i++;
                        if (i == values.length) {
                            i = 0;
                        }
                    }

                    ops[finalK] = localCount;
                });
            }
            t0 = System.nanoTime();
            latch.countDown();
            Thread.sleep(seconds * 1000L);
            stop.set(true);
            t1 = System.nanoTime();
        }

        var sumOps = Arrays.stream(ops).sum();
        return sumOps * 1_000_000_000 / (t1 - t0);
    }

    public long measurePoint(MetricsCollector collector, long[] values, int threadCount) throws InterruptedException {
        run(collector, values, threadCount, 5);
        var result = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            result.add(run(collector, values, threadCount, 5));
        }
        System.out.println(collector.snapshot().count());
        var sortedResult = result.stream().sorted().toList();
        return sortedResult.get(sortedResult.size() / 2);
    }
}
