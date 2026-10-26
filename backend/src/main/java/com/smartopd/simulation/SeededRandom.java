package com.smartopd.simulation;

/**
 * SeededRandom - Pure Java implementation of the Mulberry32 pseudo-random number generator (PRNG).
 * 
 * ============================================================================
 * Algorithmic Port & Viva Explanation:
 * Mulberry32 is a 32-bit stateful, high-quality pseudo-random generator that produces
 * uniformly distributed floating-point numbers in [0.0, 1.0).
 * 
 * Mapping from JavaScript to Java:
 * 1. 32-bit State Wrap:
 *    In JavaScript: `s = (s + 0x6D2B79F5) | 0;` (bitwise OR with 0 forces 32-bit signed int).
 *    In Java: Standard `int` addition `s += 0x6D2B79F5` naturally wraps with two's complement
 *    overflow, behaving bit-identically.
 * 
 * 2. 32-bit Integer Multiplication (`Math.imul`):
 *    In JavaScript: `Math.imul(a, b)` performs C-like 32-bit integer multiplication.
 *    In Java: The standard `*` operator on 32-bit `int` values computes the exact same
 *    lower 32-bits with identical truncation.
 * 
 * 3. Unsigned Logical Right Shifts:
 *    Both JavaScript and Java provide the unsigned logical right-shift operator (`>>>`).
 *    In JavaScript: `(t >>> 0)` converts a signed 32-bit int into an unsigned double.
 *    In Java: `(t & 0xFFFFFFFFL)` masks the lower 32 bits into a positive 64-bit `long`
 *    in [0, 4294967295], which is then divided by 4294967296.0 (2^32).
 * 
 * Guarantees:
 * Given the exact same seed integer, SeededRandom produces bit-identical sequences
 * to frontend/js/simulator.js createPRNG().
 * ============================================================================
 */
public class SeededRandom {

    private int state;

    /**
     * Initializes the PRNG with a seed.
     * Replicates JS `let s = Math.floor(Math.abs(seed)) || 42;`
     * 
     * @param seed integer seed value
     */
    public SeededRandom(long seed) {
        long absSeed = Math.abs(seed);
        this.state = (absSeed == 0) ? 42 : (int) absSeed;
    }

    /**
     * Generates the next pseudorandom double in [0.0, 1.0).
     * 
     * @return pseudorandom value
     */
    public double next() {
        // Step 1: Advance state by constant Weyl-sequence delta
        state += 0x6D2B79F5;

        // Step 2: First non-linear mixing stage (equivalent to Math.imul(s ^ (s >>> 15), 1 | s))
        int t = (state ^ (state >>> 15)) * (1 | state);

        // Step 3: Second non-linear avalanche stage (equivalent to (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t)
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;

        // Step 4: Final bit rotation and division by 2^32
        long unsignedT = (t ^ (t >>> 14)) & 0xFFFFFFFFL;
        return (double) unsignedT / 4294967296.0;
    }
}
