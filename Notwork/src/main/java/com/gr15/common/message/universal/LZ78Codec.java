package com.gr15.common.message.universal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Byte-oriented LZ78 codec used by the universal DATA message payload.
 *
 * The universal specification requires LZ78 without defining a binary token
 * format. Tokens are encoded here as a 32-bit dictionary prefix followed by a
 * 16-bit symbol marker. Marker 0 means "no following byte"; markers 1..256
 * carry byte values 0..255.
 */
public final class LZ78Codec {
    private static final int TOKEN_BYTES = Integer.BYTES + Short.BYTES;

    private LZ78Codec() {
    }

    public static byte[] compress(byte[] input) {
        Map<ByteSequence, Integer> dictionary = new HashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int nextIndex = 1;
        int position = 0;

        while (position < input.length) {
            int prefix = 0;
            byte[] phrase = new byte[0];
            int cursor = position;

            while (cursor < input.length) {
                byte[] candidate = append(phrase, input[cursor]);
                Integer candidateIndex = dictionary.get(new ByteSequence(candidate));
                if (candidateIndex == null) {
                    break;
                }
                prefix = candidateIndex;
                phrase = candidate;
                cursor++;
            }

            if (cursor < input.length) {
                byte[] newPhrase = append(phrase, input[cursor]);
                writeToken(output, prefix, (input[cursor] & 0xff) + 1);
                dictionary.put(new ByteSequence(newPhrase), nextIndex);
                nextIndex++;
                position = cursor + 1;
            } else {
                writeToken(output, prefix, 0);
                position = cursor;
            }
        }

        return output.toByteArray();
    }

    public static byte[] decompress(byte[] input) throws IOException {
        if (input.length % TOKEN_BYTES != 0) {
            throw new IOException("Invalid LZ78 payload length: " + input.length);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Dictionary dictionary = new Dictionary();

        for (int offset = 0; offset < input.length; offset += TOKEN_BYTES) {
            int prefix = readInt(input, offset);
            int marker = readUnsignedShort(input, offset + Integer.BYTES);
            byte[] phrase = dictionary.get(prefix);

            if (marker == 0) {
                output.writeBytes(phrase);
                continue;
            }

            if (marker > 256) {
                throw new IOException("Invalid LZ78 symbol marker: " + marker);
            }

            byte[] newPhrase = append(phrase, (byte) (marker - 1));
            output.writeBytes(newPhrase);
            dictionary.add(newPhrase);
        }

        return output.toByteArray();
    }

    private static byte[] append(byte[] bytes, byte next) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = next;
        return result;
    }

    private static void writeToken(ByteArrayOutputStream output, int prefix, int marker) {
        output.write((prefix >>> 24) & 0xff);
        output.write((prefix >>> 16) & 0xff);
        output.write((prefix >>> 8) & 0xff);
        output.write(prefix & 0xff);
        output.write((marker >>> 8) & 0xff);
        output.write(marker & 0xff);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private record ByteSequence(byte[] bytes) {
        private ByteSequence {
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ByteSequence sequence && Arrays.equals(bytes, sequence.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    private static final class Dictionary {
        private byte[][] values = new byte[16][];
        private int size = 1;

        private Dictionary() {
            values[0] = new byte[0];
        }

        private byte[] get(int index) throws IOException {
            if (index < 0 || index >= size) {
                throw new IOException("Invalid LZ78 prefix index: " + index);
            }
            return values[index];
        }

        private void add(byte[] value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size] = value;
            size++;
        }
    }
}
