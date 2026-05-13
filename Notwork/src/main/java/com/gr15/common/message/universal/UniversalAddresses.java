package com.gr15.common.message.universal;

import com.gr15.common.ClientId;

import java.nio.charset.StandardCharsets;

/**
 * Converts between the internal numeric ids and the fixed-width addresses used
 * by the universal protocol.
 */
public final class UniversalAddresses {
    public static final int SERVER_ADDRESS_BYTES = 3;
    public static final int CLIENT_ADDRESS_BYTES = 6;

    private UniversalAddresses() {
    }

    public static String serverAddress(int serverId) {
        if (serverId < 0 || serverId > 99) {
            throw new IllegalArgumentException("Universal server id must be between 0 and 99: " + serverId);
        }
        return String.format("S%02d", serverId);
    }

    public static String clientAddress(int clientId) {
        int serverId = ClientId.GetServerId(clientId);
        int localId = ClientId.GetLocalId(clientId);
        if (localId < 0 || localId > 9) {
            // The universal address format has a single decimal digit for the
            // local client id: Sxx_Cx.
            throw new IllegalArgumentException("Universal client local id must be between 0 and 9: " + localId);
        }
        return serverAddress(serverId) + "_C" + localId;
    }

    public static int parseServerAddress(String address) {
        if (address == null || !address.matches("S\\d{2}")) {
            throw new IllegalArgumentException("Invalid universal server address: " + address);
        }
        return Integer.parseInt(address.substring(1));
    }

    public static int parseClientAddress(String address) {
        if (address == null || !address.matches("S\\d{2}_C\\d")) {
            throw new IllegalArgumentException("Invalid universal client address: " + address);
        }
        return ClientId.Create(parseServerAddress(address.substring(0, SERVER_ADDRESS_BYTES)), address.charAt(5) - '0');
    }

    public static byte[] fixedAscii(String value, int expectedLength) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException("Expected " + expectedLength + " ASCII bytes, got " + bytes.length + ": " + value);
        }
        return bytes;
    }

    public static String ascii(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
