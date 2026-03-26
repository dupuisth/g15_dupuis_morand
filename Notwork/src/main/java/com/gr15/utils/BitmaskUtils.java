package com.gr15.utils;

public class BitmaskUtils {
    public static int GetBitmask(int numBits) {
        // Use long because if we use numBits = 32 with an int it will overflow
        return (int)((long)1 << numBits) - 1;
    }
}
