/*
 * Copyright 2017, Heinz Kabutz - All Rights Reserved
 */
package eu.javaspecialists.performance.managedblocker;

import eu.javaspecialists.performance.math.*;

import java.util.*;
import java.util.concurrent.*;

public class Fibonacci {
    private final BigInteger RESERVED = BigInteger.valueOf(-1000);

    /*
        demo1: test100_000_000() time = 45935
        demo2: test100_000_000() time = 24572
        demo3: test100_000_000() time = 19569
        demo4: test100_000_000() time = 13323
        demo5: test100_000_000() time = 9707

         */
    public BigInteger f(int n) {
        Map<Integer, BigInteger> cache = new ConcurrentHashMap<>();
        cache.put(0, BigInteger.ZERO);
        cache.put(1, BigInteger.ONE);
        return f(n, cache);
    }

    private BigInteger f(int n, Map<Integer, BigInteger> cache) {
        BigInteger result = cache.putIfAbsent(n, RESERVED);
        if (result == null) { // we won the race
            int half = (n + 1) / 2;

            ForkJoinTask<BigInteger> f0_task = new RecursiveTask<BigInteger>() {
                protected BigInteger compute() {
                    return f(half - 1, cache);
                }
            };
            f0_task.fork();
            BigInteger f1 = f(half, cache);
            BigInteger f0 = f0_task.join();

            long time = n > 1000 ? System.currentTimeMillis() : 0;
            try {
                if (n % 2 == 1) {
                    result = f0.multiply(f0).add(f1.multiply(f1));
                } else {
                    result = f0.shiftLeft(1).add(f1).multiply(f1);
                }
            } finally {
                time = n > 1000 ? System.currentTimeMillis() - time : 0;
                if (time > 50) {
                    System.out.println("fib(" + n + ") took " + time);
                }
            }
            synchronized (RESERVED) {
                cache.put(n, result);
                RESERVED.notifyAll();
            }
        } else if (result == RESERVED) { // we must wait
            try {
                synchronized (RESERVED) {
                    while((result = cache.get(n)) == RESERVED) {
                        RESERVED.wait();
                    }
                }
            } catch (InterruptedException e) {
                throw new CancellationException("interrupted");
            }
        }
        return result;
    }
}
