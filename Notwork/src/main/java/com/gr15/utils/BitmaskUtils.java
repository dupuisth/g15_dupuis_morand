package com.gr15.utils;

import com.gr15.common.Converter;

// Functions are from : https://www.educative.io/answers/how-to-set-all-bits-in-the-given-range-of-a-number
public class BitmaskUtils {
    public static int GetBitmask(int l, int r) {
        return (((1 << (l - 1)) - 1) ^ ((1 << r) - 1));
    }

    public static int GetBitmask(int numBits) {
        return GetBitmask(numBits, Converter.BITS_PER_INTEGER);
    }
}
