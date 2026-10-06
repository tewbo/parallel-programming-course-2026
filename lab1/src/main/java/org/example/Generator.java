package org.example;

import java.util.Arrays;
import java.util.SplittableRandom;

public final class Generator {
    public static final int SIZE = 1 << 20;
    public static final int MAX_VALUE = 1023;
    public static final double EXPONENT = 1.15;
    public static final long DEFAULT_SEED = 42;

    private Generator() {
    }

    public static long[] generateValues() {
        return generateValues(SIZE, DEFAULT_SEED);
    }

    public static long[] generateValues(int size, long seed) {
        double[] cumulativeWeights = buildCumulativeWeights();
        double totalWeight = cumulativeWeights[cumulativeWeights.length - 1];
        SplittableRandom random = new SplittableRandom(seed);

        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            values[i] = sample(cumulativeWeights, random.nextDouble() * totalWeight);
        }
        return values;
    }

    private static double[] buildCumulativeWeights() {
        double[] cumulativeWeights = new double[MAX_VALUE];
        double accumulator = 0;
        for (int k = 1; k <= MAX_VALUE; k++) {
            accumulator += 1.0 / Math.pow(k, EXPONENT);
            cumulativeWeights[k - 1] = accumulator;
        }
        return cumulativeWeights;
    }

    private static long sample(double[] cumulativeWeights, double point) {
        int index = Arrays.binarySearch(cumulativeWeights, point);
        int firstGreaterOrEqual = index >= 0 ? index : -index - 1;
        return Math.min(firstGreaterOrEqual, MAX_VALUE - 1) + 1;
    }
}
