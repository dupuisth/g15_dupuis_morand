package com.gr15.common.message.universal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Byte-oriented LZ78 codec used by the universal DATA message payload.
 *
 * Tokens are encoded as a 16-bit dictionary prefix followed by one raw byte,
 * matching the common.model.LZ78 implementation used by peer projects.
 */
public final class LZ78Codec {
    private static final int TOKEN_BYTES = Short.BYTES + Byte.BYTES;
    private static final int MAX_DICTIONARY_INDEX = Short.MAX_VALUE - 1;

    private LZ78Codec() {
    }

    public static byte[] compress(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }

        Map<String, Short> dictionary = new HashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        short nextIndex = 1;
        StringBuilder currentSequence = new StringBuilder();

        for (byte currentByte : input) {
            currentSequence.append((char) (currentByte & 0xff));

            String sequence = currentSequence.toString();
            if (!dictionary.containsKey(sequence)) {
                String prefix = sequence.substring(0, sequence.length() - 1);
                short prefixIndex = prefix.isEmpty() ? 0 : dictionary.get(prefix);

                writeToken(output, prefixIndex, currentByte);

                if (nextIndex <= MAX_DICTIONARY_INDEX) {
                    dictionary.put(sequence, nextIndex);
                    nextIndex++;
                }
                currentSequence.setLength(0);
            }
        }

        if (!currentSequence.isEmpty()) {
            String sequence = currentSequence.toString();
            String prefix = sequence.substring(0, sequence.length() - 1);
            short prefixIndex = prefix.isEmpty() ? 0 : dictionary.get(prefix);
            byte finalByte = (byte) sequence.charAt(sequence.length() - 1);

            writeToken(output, prefixIndex, finalByte);
        }

        return output.toByteArray();
    }

    public static byte[] decompress(byte[] input) throws IOException {
        if (input == null || input.length == 0) {
            return new byte[0];
        }

        if (input.length % TOKEN_BYTES != 0) {
            throw new IOException("Invalid LZ78 payload length: " + input.length);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Map<Short, byte[]> dictionary = new HashMap<>();
        short nextIndex = 1;

        for (int offset = 0; offset < input.length; offset += TOKEN_BYTES) {
            short prefixIndex = readShort(input, offset);
            byte nextByte = input[offset + Short.BYTES];

            if (prefixIndex < 0 || (prefixIndex > 0 && !dictionary.containsKey(prefixIndex))) {
                throw new IOException("Invalid LZ78 prefix index: " + prefixIndex);
            }

            byte[] prefix = prefixIndex == 0 ? new byte[0] : dictionary.get(prefixIndex);
            byte[] entry = append(prefix, nextByte);
            output.writeBytes(entry);

            if (nextIndex <= MAX_DICTIONARY_INDEX) {
                dictionary.put(nextIndex, entry);
                nextIndex++;
            }
        }

        return output.toByteArray();
    }

    private static byte[] append(byte[] bytes, byte next) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = next;
        return result;
    }

    private static void writeToken(ByteArrayOutputStream output, short prefix, byte value) {
        output.write((prefix >>> 8) & 0xff);
        output.write(prefix & 0xff);
        output.write(value);
    }

    private static short readShort(byte[] bytes, int offset) {
        return (short) (((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff));
    }
}
