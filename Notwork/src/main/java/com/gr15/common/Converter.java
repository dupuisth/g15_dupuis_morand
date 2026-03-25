package com.gr15.common;

public class Converter {

    /**
     * Size of a long in bytes
     */
    public static final int BYTES_PER_LONG = 8;

    /**
     * Size of a integer in bytes
     */
    public static final int BYTES_PER_INTEGER = 4;

    public static final int BITS_PER_BYTE = 8;
    public static final int BITS_PER_LONG = BITS_PER_BYTE * BYTES_PER_LONG;
    public static final int BITS_PER_INTEGER = BITS_PER_BYTE * BYTES_PER_INTEGER;

    public static void ByteToBits(byte value, byte[] array, int startBit) {
        int pos = startBit / BITS_PER_BYTE;
        int bit = startBit % BITS_PER_BYTE;

        if (bit == 0) {
            // We start at the given index, so just write it raw
            array[pos] = value;
        } else {
            // We start at the given index, at the bits : bit
            // And it overflows on the next byte
            array[pos] |= (byte) (value << bit);
            array[pos + 1] |= (byte) (value >> (8 - bit));
        }
    }

    public static void IntToBits(int value, byte[] array, int startBit) {
        for (int i = 0; i < BITS_PER_INTEGER; i++) {
            int pos = (startBit + i) / BITS_PER_BYTE;
            int bit = (startBit + i) % BITS_PER_BYTE;

            byte bitValue = (byte) (((value >> i) & 1) << bit);
            array[pos] |= bitValue;
        }
    }

    public static int BytesToInt(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < bytes.length; i++)
        {
            result |= bytes[i] << (i * BITS_PER_BYTE);
        }
        return result;
    }
}
