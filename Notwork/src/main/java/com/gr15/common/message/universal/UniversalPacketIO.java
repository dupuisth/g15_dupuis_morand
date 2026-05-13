package com.gr15.common.message.universal;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Low-level serializer for the universal protocol.
 *
 * All integer fields are written through DataInputStream/DataOutputStream,
 * which matches the required 32-bit big-endian representation.
 */
public final class UniversalPacketIO {
    private UniversalPacketIO() {
    }

    public static UniversalPacket read(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != UniversalPacket.MAGIC) {
            throw new IOException("Invalid universal protocol magic: " + magic);
        }

        int id = in.readInt();
        int ttl = in.readInt();
        int option = in.readInt();
        int typeId = in.readUnsignedByte();
        UniversalMessageType type = UniversalMessageType.fromId(typeId);
        if (type == null) {
            throw new IOException("Unknown universal message type: " + typeId);
        }

        return new UniversalPacket(id, ttl, option, type, readPayload(in, type));
    }

    public static void write(DataOutputStream out, UniversalPacket packet) throws IOException {
        out.writeInt(UniversalPacket.MAGIC);
        out.writeInt(packet.getId());
        out.writeInt(packet.getTtl());
        out.writeInt(packet.getOption());
        out.writeByte(packet.getType().getId());
        out.write(packet.getPayload());
        out.flush();
    }

    private static byte[] readPayload(DataInputStream in, UniversalMessageType type) throws IOException {
        // TCP is a byte stream, and the universal header has no payload length.
        // The message type is therefore used to know exactly how many bytes must
        // be consumed for the payload.
        return switch (type) {
            case CONNECT -> readFixed(in, UniversalAddresses.SERVER_ADDRESS_BYTES);
            case PING, PONG -> readFixed(in, UniversalAddresses.CLIENT_ADDRESS_BYTES * 2);
            case ERROR -> readFixed(in, UniversalAddresses.CLIENT_ADDRESS_BYTES * 2 + Integer.BYTES);
            case DATA -> readDataPayload(in);
            case TOPOLOGY, LIST_CLIENT -> readTwoStringsPayload(in);
        };
    }

    private static byte[] readDataPayload(DataInputStream in) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        byte[] addresses = readFixed(in, UniversalAddresses.CLIENT_ADDRESS_BYTES * 2);
        payload.writeBytes(addresses);
        int size = in.readInt();
        writeInt(payload, size);
        if (size < 0) {
            throw new IOException("Invalid universal DATA size: " + size);
        }
        payload.writeBytes(readFixed(in, size));
        payload.write(in.readUnsignedByte());
        return payload.toByteArray();
    }

    private static byte[] readTwoStringsPayload(DataInputStream in) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        int addLength = in.readInt();
        writeInt(payload, addLength);
        if (addLength < 0) {
            throw new IOException("Invalid additive list size: " + addLength);
        }
        payload.writeBytes(readFixed(in, addLength));

        int removeLength = in.readInt();
        writeInt(payload, removeLength);
        if (removeLength < 0) {
            throw new IOException("Invalid subtractive list size: " + removeLength);
        }
        payload.writeBytes(readFixed(in, removeLength));
        return payload.toByteArray();
    }

    private static byte[] readFixed(DataInputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    public static byte[] connectPayload(int serverId) {
        return UniversalAddresses.fixedAscii(
                UniversalAddresses.serverAddress(serverId),
                UniversalAddresses.SERVER_ADDRESS_BYTES
        );
    }

    public static byte[] routedClientsPayload(int sourceClientId, int destinationClientId) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.writeBytes(UniversalAddresses.fixedAscii(
                UniversalAddresses.clientAddress(sourceClientId),
                UniversalAddresses.CLIENT_ADDRESS_BYTES
        ));
        payload.writeBytes(UniversalAddresses.fixedAscii(
                UniversalAddresses.clientAddress(destinationClientId),
                UniversalAddresses.CLIENT_ADDRESS_BYTES
        ));
        return payload.toByteArray();
    }

    public static byte[] dataPayload(int sourceClientId, int destinationClientId, String content) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.writeBytes(routedClientsPayload(sourceClientId, destinationClientId));
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        writeInt(payload, contentBytes.length);
        payload.writeBytes(contentBytes);
        // The specification requires one parity byte after DATA content.
        payload.write(parity(contentBytes));
        return payload.toByteArray();
    }

    public static byte[] errorPayload(int sourceClientId, int destinationClientId, int errorCode) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.writeBytes(routedClientsPayload(sourceClientId, destinationClientId));
        writeInt(payload, errorCode);
        return payload.toByteArray();
    }

    public static byte[] twoStringsPayload(String additive, String subtractive) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        byte[] additiveBytes = additive.getBytes(StandardCharsets.UTF_8);
        byte[] subtractiveBytes = subtractive.getBytes(StandardCharsets.UTF_8);
        writeInt(payload, additiveBytes.length);
        payload.writeBytes(additiveBytes);
        writeInt(payload, subtractiveBytes.length);
        payload.writeBytes(subtractiveBytes);
        return payload.toByteArray();
    }

    public static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    public static int parity(byte[] bytes) {
        int parity = 0;
        for (byte value : bytes) {
            parity ^= Integer.bitCount(value & 0xff) & 1;
        }
        return parity;
    }
}
