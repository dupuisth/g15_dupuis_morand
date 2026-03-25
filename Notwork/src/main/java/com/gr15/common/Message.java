package com.gr15.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class Message {
    private static final Logger LOGGER = Logger.getLogger(Message.class.getName());

    // Server --> Client
    public static final byte STC_HELLO = 0;
    public static final byte STC_BROADCAST = 2;
    public static final byte STC_PING = 3;

    // Client --> Server
    public static final byte CTS_PONG = 0;
    public static final byte CTS_MESSAGE = 1;

    // Length
    public static final int MESSAGE_ID_BITS = 4;
    public static final int MESSAGE_HEADER_BITS = 16;

    // Config
    public static final Charset ENCODING_CHARSET = StandardCharsets.UTF_8;

    public static final int MAX_SIZE_BYTES = 64;
    private byte[] data = new byte[MAX_SIZE_BYTES];
    /**
     * How many bits have been written
     */
    private int writtenBit;

    /**
     * How many bits have been read
     */
    private int readBit;

    public Message(byte messageId) {
        writtenBit = 0;
        readBit = 0;

        AddBits(messageId, MESSAGE_ID_BITS);
    }

    public Message(byte[] aBytes) {
        this.data = aBytes;
        this.writtenBit = data.length * Converter.BITS_PER_BYTE; // Approximate
        this.readBit = 0;
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Add up to 8 bits to the message
     *
     * @param bitfield
     * @param amount
     */
    public void AddBits(byte bitfield, int amount) {
        if (amount > Converter.BITS_PER_BYTE || amount <= 0) {
            throw new IllegalArgumentException("Cannot add amount=" + amount + " bits from a byte type");
        }

        if (amount > getUnwrittenBits()) {
            throw new RuntimeException("Message will be too big !");
        }

        // Discard any bits that are beyond the given amount
        byte mask = (byte) (0xFF >> (Converter.BITS_PER_BYTE - amount));
        bitfield &= mask;
        Converter.ByteToBits(bitfield, data, writtenBit);
        writtenBit += amount;
    }


    /**
     * Add up to 32 bits to the message
     */
    public void AddInt(int bitfield, int amount) {
        if (amount > Converter.BITS_PER_INTEGER || amount <= 0) {
            throw new IllegalArgumentException("Cannot add amount=" + amount + " bit from an integer type");
        }

        if (amount > getUnwrittenBits()) {
            throw new RuntimeException("Message will be too big !");
        }

        int mask = (0xFFFFFFFF >> (Converter.BITS_PER_INTEGER - amount));
        bitfield &= mask;
        Converter.IntToBits(bitfield, data, writtenBit);
        writtenBit += amount;
    }

    public void AddBytes(byte[] bytes, boolean writeLength) {
        if (bytes.length * Converter.BITS_PER_BYTE > getUnwrittenBits()) {
            throw new RuntimeException("Message will be too big !");
        }

        if (writeLength) {
            AddInt(bytes.length, Converter.BITS_PER_INTEGER);
        }

        for (byte aByte : bytes) {
            AddBits(aByte, Converter.BITS_PER_BYTE);
        }
    }

    public void AddString(String string) {
        AddBytes(string.getBytes(ENCODING_CHARSET), true);
    }

    public byte ReadByte(int amount) {
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

    public static Message readMessageFromSocket(DataInputStream in) throws IOException {
        // Read the length
        int length = in.readInt();

        // Read the message
        byte[] messageBytes = new byte[length];
        in.readFully(messageBytes);

        // Build the message from the bytes
        return new Message(messageBytes);
    }

    public static void sendMessageToSocket(DataOutputStream out, Message message) throws IOException {
        // Get the total bytes written
        int length = message.getWrittenByte();

        // Send it
        out.writeInt(length);

        // Then send all the data
        out.write(message.getData(), 0, length);

        // And assure the message is fully sent
        out.flush();
    }
}
