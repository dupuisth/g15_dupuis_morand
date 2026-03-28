package com.gr15.common;

import com.gr15.utils.BitmaskUtils;
import com.gr15.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Inspiration from https://github.com/RiptideNetworking/Riptide/tree/main

public class Message {

    // Length
    public static final int MESSAGE_ID_BITS = 4;

    // Config
    public static final Charset ENCODING_CHARSET = StandardCharsets.UTF_8;

    // Arbitrary value, seems ok after some testing (currently saw a limit at ~ 4100, to investigate)
    public static final int MAX_SIZE_BYTES = 3000;
    private byte[] data = new byte[MAX_SIZE_BYTES];

    /**
     * How many bits have been written
     */
    private int writtenBit;

    /**
     * How many bits have been read
     */
    private int readBit;

    public Message(int messageId) {
        writtenBit = 0;
        readBit = 0;

        addInt(messageId, MESSAGE_ID_BITS);
    }

    public Message(byte[] aBytes) {
        this.data = aBytes;
        this.writtenBit = aBytes.length * Converter.BITS_PER_BYTE; // Approximate
        this.readBit = 0;
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Add up to 8 bits to the message
     */
    public void addBits(byte bitfield, int amount) {
        if (amount > Converter.BITS_PER_BYTE || amount <= 0) {
            throw new IllegalArgumentException("Cannot add amount=" + amount + " bits from a byte type");
        }

        if (amount > getUnwrittenBits()) {
            throw new RuntimeException("Message will be too big !");
        }

        // Discard any bits that are beyond the given amount
        byte mask = (byte)BitmaskUtils.GetBitmask(amount);
        bitfield &= mask;
        Converter.ByteToBits(bitfield, data, writtenBit);
        writtenBit += amount;
    }


    /**
     * Add up to 32 bits to the message
     */
    public void addInt(int bitfield, int amount) {
        if (amount > Converter.BITS_PER_INTEGER || amount <= 0) {
            throw new IllegalArgumentException("Cannot add amount=" + amount + " bit from an integer type");
        }

        if (amount > getUnwrittenBits()) {
            throw new RuntimeException("Message will be too big !");
        }

        int mask = BitmaskUtils.GetBitmask(amount);
        bitfield &= mask;
        Converter.IntToBits(bitfield, data, writtenBit);
        writtenBit += amount;
    }

    /**
     * Add a byte array to the message
     * @param writeLength if the length of the byte array should be written before
     */
    public void addBytes(byte[] bytes, boolean writeLength) {
        if (bytes.length * Converter.BITS_PER_BYTE > getUnwrittenBits()) {
            throw new RuntimeException("Message will be too big !");
        }

        if (writeLength) {
            addInt(bytes.length, Converter.BITS_PER_INTEGER);
        }

        for (byte aByte : bytes) {
            addBits(aByte, Converter.BITS_PER_BYTE);
        }
    }

    public void addString(String string) {
        addBytes(string.getBytes(ENCODING_CHARSET), true);
    }

    /**
     * Read up to a byte from the message
     * @param amount the amount of bits to read
     */
    public byte readByte(int amount) {
        if (amount > Converter.BITS_PER_BYTE || amount <= 0) {
            throw new IllegalArgumentException("Wrong value for amount");
        }

        if (readBit + amount > writtenBit) {
            throw new RuntimeException("Will overflow message length");
        }

        byte result = 0;

        for (int i = 0; i < amount; i++) {
            int absoluteBit = readBit + i;

            int pos = absoluteBit / Converter.BITS_PER_BYTE;
            int bit = absoluteBit % Converter.BITS_PER_BYTE;

            int bitValue = (data[pos] >> bit) & 1;
            result |= (byte) (bitValue << i);
        }

        readBit += amount;
        return result;
    }

    /**
     * Read up to an int from the message
     * @param amount the amount of bits to read
     */
    public int readInt(int amount) {
        if (amount > Converter.BITS_PER_INTEGER || amount <= 0) {
            throw new IllegalArgumentException("Wrong value for amount");
        }

        if (readBit + amount > writtenBit) {
            throw new RuntimeException("Will overflow message length");
        }

        int result = 0;
        for (int i = 0; i < amount; i++) {
            int absoluteBit = readBit + i;

            int pos = absoluteBit / Converter.BITS_PER_BYTE;
            int bit = absoluteBit % Converter.BITS_PER_BYTE;

            int bitValue = (data[pos] >> bit) & 1;
            result |= bitValue << i;
        }
        readBit += amount;

        return result;
    }

    /**
     * Read the length of the byte array, then the byte array from the message
     */
    public byte[] readBytesWithLength() {
        int length = readInt(Converter.BITS_PER_INTEGER);

        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = readByte(Converter.BITS_PER_BYTE);
        }
        return bytes;
    }

    /**
     * Read a string from the message
     */
    public String readString() {
        byte[] stringBytes = readBytesWithLength();
        String decoded = new String(stringBytes, ENCODING_CHARSET);
        return decoded;
    }

    /**
     * Surely really slow, but just for testing
     */
    public String getDataAsBitsInString() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < writtenBit; i++) {
            int index = i / Converter.BITS_PER_BYTE;
            int bit = i % Converter.BITS_PER_BYTE;
            char character = ((data[index] >> bit) & 1) == 1 ? '1' : '0';
            str.append(character);
        }
        return str.toString();
    }

    public int getUnwrittenBits() {
        return MAX_SIZE_BYTES * Converter.BITS_PER_BYTE - writtenBit;
    }

    public int getUnreadenBits() {
        return writtenBit - readBit;
    }

    public int getWrittenBit() {
        return writtenBit;
    }

    public int getReadBit() {
        return readBit;
    }

    public int getWrittenByte() {
        return Math.ceilDiv(writtenBit, Converter.BITS_PER_BYTE);
    }
}
