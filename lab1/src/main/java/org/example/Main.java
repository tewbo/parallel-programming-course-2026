package org.example;

import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Main {
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8, 16};

    private static void zeroStage() throws InterruptedException {
        long[] values = Generator.generateValues();
        MetricsCollector collector = new MetricsCollectorImpl();

        long opsPerSecond = new Benchmark().measurePoint(collector, values, 1);

        System.out.printf("T=1: %d ops/sec%n", opsPerSecond);
        // 188_643_484 ops/sec
    }

    private static void stage(String name, Supplier<MetricsCollector> collectorFactory) throws InterruptedException {
        long[] values = Generator.generateValues();
        int processorCount = Runtime.getRuntime().availableProcessors();
        int[] threadCounts = IntStream.of(THREAD_COUNTS).filter(t -> t <= processorCount).toArray();

        var benchmark = new Benchmark();
        var results = new long[threadCounts.length];
        for (int i = 0; i < threadCounts.length; i++) {
            results[i] = benchmark.measurePoint(collectorFactory.get(), values, threadCounts[i]);
        }

        System.out.println("collector,threads,opsPerSecond");
        for (int i = 0; i < threadCounts.length; i++) {
            System.out.printf("%s,%d,%d%n", name, threadCounts[i], results[i]);
        }
        /*
        synchronized, 1,19752351
        synchronized, 2, 4581207
        synchronized, 4, 3630648
        synchronized, 8, 4401533
        synchronized,16, 4027097

        empty-lock, 1,20001338
        empty-lock, 2, 3341455
        empty-lock, 4, 4559965
        empty-lock, 8, 2806600
        empty-lock,16, 2799787

        striped,1,14238321
        striped,2, 4512600
        striped,4, 3857194
        striped,8, 4176421
        striped,16,4266279

        (striped) Consistency stress test:
        Sum less than count: 0.9564
        Sum greater than count: 0.0013
        Total: 0.9577
        Sum after all: 288179
        Count after all (real): 288179

        thread-local,1,  83580681
        thread-local,2, 169067172
        thread-local,4, 337205565
        thread-local,8, 485617864
        thread-local,16,649620475

        (thread-local) Consistency stress test:
        Sum less than count: 0.9846
        Sum greater than count: 1.0E-4
        Total: 0.9847
        Sum after all: 8798004
        Count after all (real): 8798004

        double-buffer,1,  44516218
        double-buffer,2,  89313802
        double-buffer,4, 179312796
        double-buffer,8, 273565788
        double-buffer,16,391543914

        (double-buffer) Consistency stress test:
        Sum less than count: 0.0
        Sum greater than count: 0.0
        Total: 0.0
        Sum after all: 10575464
        Count after all (real): 10575464

        (double-buffer without check) Consistency stress test:
        Sum less than count: 0.9949
        Sum greater than count: 0.0
        Total: 0.9949
        Sum after all: 9707711
        Count after all (real): 9707705
         */
    }

    private static Supplier<MetricsCollector> collectorFactory(String name) {
        return switch (name) {
            case "synchronized" -> () -> new SynchronizedMetricsCollector(new MetricsCollectorImpl());
            case "empty-lock" -> EmptyLockMetricsCollector::new;
            case "striped" -> StripedMetricsCollector::new;
            case "thread-local" -> ThreadLocalMetricsCollector::new;
            case "double-buffer" -> DoubleBufferCollector::new;
            default -> throw new IllegalArgumentException("Unknown collector: " + name);
        };
    }

    public static void main(String[] args) throws InterruptedException {
        String variant = args.length > 0 ? args[0] : "zero";
        switch (variant) {
            case "zero" -> zeroStage();
            case "striped-consistency" -> ConsistencyStressTest.run(collectorFactory(args.length > 1 ? args[1] : "striped"), 4);
            case "thread-local-consistency" -> ConsistencyStressTest.run(collectorFactory(args.length > 1 ? args[1] : "thread-local"), 4);
            case "double-buffer-consistency" -> ConsistencyStressTest.run(collectorFactory(args.length > 1 ? args[1] : "double-buffer"), 4);
            default -> stage(variant, collectorFactory(variant));
        }
    }
}
