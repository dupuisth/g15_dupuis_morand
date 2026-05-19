package com.gr15.common.message.universal;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.message.sts.MessageSTS;
import com.gr15.common.message.sts.STS_Identify;
import com.gr15.common.message.sts.STS_Ping;
import com.gr15.common.message.sts.STS_Pong;
import com.gr15.common.message.sts.STS_RoutedError;
import com.gr15.common.message.sts.STS_RoutedMessage;
import com.gr15.common.message.sts.STS_RoutingUpdate;
import com.gr15.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.gr15.common.Constants.MAX_CLIENTS;
import static com.gr15.common.Constants.MAX_SERVERS;

/**
 * Bridges the public universal server-to-server protocol and the internal STS
 * messages used by the rest of the server implementation.
 *
 * The server managers only know how to route STS messages. Keeping the
 * translation here lets the socket connection speak the universal wire format
 * without spreading protocol-specific parsing through the routing layer.
 */
public class UniversalMessageAdapter {
    /**
     * A single universal packet can imply several internal routing updates.
     * Extra updates are queued here and returned by following read() calls.
     */
    private final ArrayDeque<Message> pendingMessages = new ArrayDeque<>();

    /**
     * The universal protocol carries additive/subtractive topology and client
     * lists. Internally we route with full bitmasks, so this adapter keeps the
     * reconstructed remote state per server.
     */
    private final Map<Integer, Integer> receivedClientMasks = new HashMap<>();
    private final Map<Integer, Integer> receivedNeighborMasks = new HashMap<>();
    private final Map<Integer, Integer> receivedSequences = new HashMap<>();

    /**
     * Last state sent to this peer. It is used to convert full internal routing
     * snapshots into universal additive/subtractive updates.
     */
    private final Map<Integer, Integer> sentClientMasks = new HashMap<>();
    private final Map<Integer, Integer> sentNeighborMasks = new HashMap<>();
    private int nextOutgoingPacketId = 0;

    /**
     * Reads one universal packet from the stream and returns the next internal
     * message that the existing server manager can handle.
     */
    public Message read(DataInputStream in) throws IOException {
        synchronized (pendingMessages) {
            Message pending = pendingMessages.poll();
            if (pending != null) {
                Logger.universal("Delivering queued internal message type=" + messageTypeName(pending));
                return pending;
            }
        }

        UniversalPacket packet = UniversalPacketIO.read(in);
        Logger.universal("Received packet " + packetSummary(packet));
        Message converted = toLocalMessage(packet);
        synchronized (pendingMessages) {
            Message pending = pendingMessages.poll();
            if (converted == null && pending != null) {
                Logger.universal("Converted packet type=" + packet.getType()
                        + " to queued internal message type=" + messageTypeName(pending));
                return pending;
            }
        }
        if (converted == null) {
            Logger.universal("Packet type=" + packet.getType()
                    + " produced no direct internal message, emitting internal PING placeholder");
            return STS_Ping.CreateMessage();
        }
        Logger.universal("Converted packet type=" + packet.getType()
                + " to internal message type=" + messageTypeName(converted));
        return converted;
    }

    /**
     * Converts one internal STS message into zero, one, or several universal
     * packets and writes them to the stream.
     */
    public synchronized void write(DataOutputStream out, Message message) throws IOException {
        String sourceType = messageTypeName(message);
        UniversalPacket[] packets = toUniversalPackets(message);
        if (packets.length == 0) {
            Logger.universal("Internal message type=" + sourceType
                    + " has no universal equivalent, nothing sent");
            return;
        }

        Logger.universal("Sending internal message type=" + sourceType
                + " as " + packets.length + " universal packet(s)");
        for (UniversalPacket packet : packets) {
            Logger.universal("Sending packet " + packetSummary(packet));
            UniversalPacketIO.write(out, packet);
        }
    }

    private Message toLocalMessage(UniversalPacket packet) throws IOException {
        byte[] payload = packet.getPayload();
        try {
            return switch (packet.getType()) {
                case CONNECT -> {
                    int remoteServerId = UniversalAddresses.parseServerAddress(
                            UniversalAddresses.ascii(payload)
                    );
                    Logger.universal("CONNECT identifies remote server=" + remoteServerId);
                    yield STS_Identify.CreateMessage(remoteServerId, 1);
                }
                case PING -> STS_Ping.CreateMessage();
                case PONG -> STS_Pong.CreateMessage();
                case DATA -> readUniversalData(payload);
                case ERROR -> readUniversalError(payload);
                case TOPOLOGY -> readUniversalTopology(payload);
                case LIST_CLIENT -> readUniversalClientList(payload);
            };
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid universal packet payload type=" + packet.getType(), e);
        }
    }

    private Message readUniversalData(byte[] payload) throws IOException {
        String source = UniversalAddresses.ascii(slice(payload, 0, UniversalAddresses.CLIENT_ADDRESS_BYTES));
        String destination = UniversalAddresses.ascii(slice(payload, UniversalAddresses.CLIENT_ADDRESS_BYTES, UniversalAddresses.CLIENT_ADDRESS_BYTES));
        int size = UniversalPacketIO.readInt(payload, UniversalAddresses.CLIENT_ADDRESS_BYTES * 2);
        int contentOffset = UniversalAddresses.CLIENT_ADDRESS_BYTES * 2 + Integer.BYTES;
        if (size < 0 || contentOffset + size >= payload.length) {
            throw new IOException("Invalid universal DATA payload size: " + size);
        }
        byte[] compressedContentBytes = slice(payload, contentOffset, size);
        int parity = payload[contentOffset + size] & 0xff;
        if (parity != UniversalPacketIO.parity(compressedContentBytes)) {
            Logger.universal("DATA parity mismatch source=" + source
                    + " destination=" + destination + " size=" + size);
            // Parity errors become routed errors so the failure follows the
            // same delivery path as other server-to-server message errors.
            return STS_RoutedError.CreateMessage(
                    UniversalAddresses.parseClientAddress(source),
                    UniversalAddresses.parseClientAddress(destination),
                    "Universal DATA parity mismatch"
            );
        }
        byte[] contentBytes = LZ78Codec.decompress(compressedContentBytes);
        Logger.universal("DATA source=" + source
                + " destination=" + destination
                + " compressedBytes=" + size
                + " contentBytes=" + contentBytes.length);
        return STS_RoutedMessage.CreateMessage(
                UniversalAddresses.parseClientAddress(source),
                UniversalAddresses.parseClientAddress(destination),
                new String(contentBytes, StandardCharsets.UTF_8)
        );
    }

    private Message readUniversalError(byte[] payload) {
        String source = UniversalAddresses.ascii(slice(payload, 0, UniversalAddresses.CLIENT_ADDRESS_BYTES));
        String destination = UniversalAddresses.ascii(slice(payload, UniversalAddresses.CLIENT_ADDRESS_BYTES, UniversalAddresses.CLIENT_ADDRESS_BYTES));
        int code = UniversalPacketIO.readInt(payload, UniversalAddresses.CLIENT_ADDRESS_BYTES * 2);
        Logger.universal("ERROR source=" + source
                + " destination=" + destination
                + " code=" + code);
        return STS_RoutedError.CreateMessage(
                UniversalAddresses.parseClientAddress(source),
                UniversalAddresses.parseClientAddress(destination),
                "Universal error code " + code
        );
    }

    private Message readUniversalTopology(byte[] payload) {
        StringLists lists = readStringLists(payload);
        Logger.universal("TOPOLOGY add='" + lists.additive()
                + "' remove='" + lists.subtractive() + "'");
        Set<Integer> changedServers = new HashSet<>();
        applyTopologyList(lists.additive(), true, changedServers);
        applyTopologyList(lists.subtractive(), false, changedServers);
        enqueueRoutingUpdates(changedServers);
        return pollPendingMessage();
    }

    private Message readUniversalClientList(byte[] payload) {
        StringLists lists = readStringLists(payload);
        Logger.universal("LIST_CLIENT add='" + lists.additive()
                + "' remove='" + lists.subtractive() + "'");
        Set<Integer> changedServers = new HashSet<>();
        applyClientList(lists.additive(), true, changedServers);
        applyClientList(lists.subtractive(), false, changedServers);
        enqueueRoutingUpdates(changedServers);
        return pollPendingMessage();
    }

    private UniversalPacket[] toUniversalPackets(Message source) throws IOException {
        Message message = copyForReading(source);
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageSTS type = MessageSTS.fromId(messageId);
        if (type == null) {
            throw new IOException("Cannot convert unknown STS message id to universal packet: " + messageId);
        }

        return switch (type) {
            case IDENTIFY -> {
                STS_Identify identify = STS_Identify.ReadMessage(message);
                yield new UniversalPacket[] {
                        createOutgoingPacket(UniversalMessageType.CONNECT, UniversalPacketIO.connectPayload(identify.getFromServerId()))
                };
            }
            case PING -> new UniversalPacket[] {
                    createOutgoingPacket(UniversalMessageType.PING, UniversalPacketIO.routedClientsPayload(0, 0))
            };
            case PONG -> new UniversalPacket[] {
                    createOutgoingPacket(UniversalMessageType.PONG, UniversalPacketIO.routedClientsPayload(0, 0))
            };
            case ROUTED_MESSAGE -> {
                STS_RoutedMessage routed = STS_RoutedMessage.ReadMessage(message);
                yield new UniversalPacket[] {
                        createOutgoingPacket(UniversalMessageType.DATA, UniversalPacketIO.dataPayload(
                                routed.getFromClientId(),
                                routed.getDestinationClientId(),
                                routed.getContent()
                        ))
                };
            }
            case ROUTED_ERROR -> {
                STS_RoutedError error = STS_RoutedError.ReadMessage(message);
                yield new UniversalPacket[] {
                        createOutgoingPacket(UniversalMessageType.ERROR, UniversalPacketIO.errorPayload(
                                error.getRecipientClientId(),
                                error.getDestinationClientId(),
                                500
                        ))
                };
            }
            case ROUTING_UPDATE -> {
                STS_RoutingUpdate update = STS_RoutingUpdate.ReadMessage(message);
                yield routingUpdatePackets(update);
            }
            case HELLO, BROADCAST_CHAT -> new UniversalPacket[0];
        };
    }

    private UniversalPacket[] routingUpdatePackets(STS_RoutingUpdate update) {
        int originServerId = update.getOriginServerId();
        int previousNeighborMask = sentNeighborMasks.getOrDefault(originServerId, 0);
        int previousClientMask = sentClientMasks.getOrDefault(originServerId, 0);
        sentNeighborMasks.put(originServerId, update.getNeighborMask());
        sentClientMasks.put(originServerId, update.getClientMask());

        // The local routing layer publishes complete snapshots. The universal
        // protocol expects deltas, so compute them against the last snapshot
        // that was emitted on this connection.
        String topologyAdd = topologyList(originServerId, update.getNeighborMask() & ~previousNeighborMask);
        String topologyRemove = topologyList(originServerId, previousNeighborMask & ~update.getNeighborMask());
        String clientsAdd = clientList(originServerId, update.getClientMask() & ~previousClientMask);
        String clientsRemove = clientList(originServerId, previousClientMask & ~update.getClientMask());

        return new UniversalPacket[] {
                createOutgoingPacket(UniversalMessageType.TOPOLOGY, UniversalPacketIO.twoStringsPayload(topologyAdd, topologyRemove)),
                createOutgoingPacket(UniversalMessageType.LIST_CLIENT, UniversalPacketIO.twoStringsPayload(clientsAdd, clientsRemove))
        };
    }

    private UniversalPacket createOutgoingPacket(UniversalMessageType type, byte[] payload) {
        return new UniversalPacket(nextOutgoingPacketId++, UniversalPacket.DEFAULT_TTL, UniversalPacket.DEFAULT_OPTION, type, payload);
    }

    private void applyTopologyList(String list, boolean additive, Set<Integer> changedServers) {
        for (String entry : list.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\|", 2);
            int originServerId = UniversalAddresses.parseServerAddress(parts[0].trim());
            int mask = receivedNeighborMasks.getOrDefault(originServerId, 0);
            if (parts.length > 1 && !parts[1].isBlank()) {
                for (String neighborRaw : parts[1].split(",")) {
                    String neighbor = neighborRaw.trim();
                    if (neighbor.isEmpty()) {
                        continue;
                    }
                    int neighborId = UniversalAddresses.parseServerAddress(neighbor);
                    if (additive) {
                        mask |= 1 << neighborId;
                    } else {
                        mask &= ~(1 << neighborId);
                    }
                }
            }
            receivedNeighborMasks.put(originServerId, mask);
            changedServers.add(originServerId);
        }
    }

    private void applyClientList(String list, boolean additive, Set<Integer> changedServers) {
        for (String entry : list.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int clientId = UniversalAddresses.parseClientAddress(trimmed);
            int server = ClientId.GetServerId(clientId);
            int local = ClientId.GetLocalId(clientId);
            int mask = receivedClientMasks.getOrDefault(server, 0);
            if (additive) {
                mask |= 1 << local;
            } else {
                mask &= ~(1 << local);
            }
            receivedClientMasks.put(server, mask);
            changedServers.add(server);
        }
    }

    private void enqueueRoutingUpdates(Set<Integer> changedServers) {
        synchronized (pendingMessages) {
            for (Integer changedServer : changedServers) {
                // Universal updates do not carry an internal sequence number.
                // Generate a monotonically increasing local sequence so the
                // RoutingTable can still reject stale repeats.
                int sequence = receivedSequences.getOrDefault(changedServer, -1) + 1;
                receivedSequences.put(changedServer, sequence);
                Logger.universal("Queued routing update from universal state originServer="
                        + changedServer
                        + " sequence=" + sequence
                        + " clientMask=" + receivedClientMasks.getOrDefault(changedServer, 0)
                        + " neighborMask=" + receivedNeighborMasks.getOrDefault(changedServer, 0));
                pendingMessages.add(STS_RoutingUpdate.CreateMessage(
                        changedServer,
                        sequence,
                        receivedClientMasks.getOrDefault(changedServer, 0),
                        receivedNeighborMasks.getOrDefault(changedServer, 0)
                ));
            }
        }
    }

    private Message pollPendingMessage() {
        synchronized (pendingMessages) {
            return pendingMessages.poll();
        }
    }

    private String topologyList(int originServerId, int neighborMask) {
        if (neighborMask == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(UniversalAddresses.serverAddress(originServerId)).append("|");
        for (int server = 0; server < MAX_SERVERS; server++) {
            if (((neighborMask >> server) & 1) == 1) {
                builder.append(UniversalAddresses.serverAddress(server)).append(",");
            }
        }
        builder.append(";");
        return builder.toString();
    }

    private String clientList(int originServerId, int clientMask) {
        if (clientMask == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int localId = 0; localId < MAX_CLIENTS && localId <= 9; localId++) {
            if (((clientMask >> localId) & 1) == 1) {
                builder.append(UniversalAddresses.clientAddress(ClientId.Create(originServerId, localId))).append(";");
            }
        }
        return builder.toString();
    }

    private StringLists readStringLists(byte[] payload) {
        int addLength = UniversalPacketIO.readInt(payload, 0);
        int addOffset = Integer.BYTES;
        String additive = new String(slice(payload, addOffset, addLength), StandardCharsets.UTF_8);
        int removeOffset = addOffset + addLength;
        int removeLength = UniversalPacketIO.readInt(payload, removeOffset);
        String subtractive = new String(slice(payload, removeOffset + Integer.BYTES, removeLength), StandardCharsets.UTF_8);
        return new StringLists(additive, subtractive);
    }

    private byte[] slice(byte[] bytes, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(bytes, offset, result, 0, length);
        return result;
    }

    private Message copyForReading(Message source) {
        byte[] bytes = new byte[source.getWrittenByte()];
        System.arraycopy(source.getData(), 0, bytes, 0, bytes.length);
        return new Message(bytes);
    }

    private String messageTypeName(Message source) {
        Message copy = copyForReading(source);
        int messageId = copy.readInt(Message.MESSAGE_ID_BITS);
        MessageSTS type = MessageSTS.fromId(messageId);
        if (type == null) {
            return "UNKNOWN(" + messageId + ")";
        }
        return type.name();
    }

    private String packetSummary(UniversalPacket packet) {
        return "type=" + packet.getType()
                + " id=" + packet.getId()
                + " ttl=" + packet.getTtl()
                + " option=" + packet.getOption()
                + " payloadBytes=" + packet.getPayload().length
                + " " + packetPayloadSummary(packet);
    }

    private String packetPayloadSummary(UniversalPacket packet) {
        byte[] payload = packet.getPayload();
        return switch (packet.getType()) {
            case CONNECT -> "server=" + UniversalAddresses.ascii(payload);
            case PING, PONG -> routedClientsSummary(payload);
            case DATA -> dataPayloadSummary(payload);
            case ERROR -> errorPayloadSummary(payload);
            case TOPOLOGY, LIST_CLIENT -> stringListsSummary(payload);
        };
    }

    private String routedClientsSummary(byte[] payload) {
        return "source=" + UniversalAddresses.ascii(slice(payload, 0, UniversalAddresses.CLIENT_ADDRESS_BYTES))
                + " destination=" + UniversalAddresses.ascii(slice(payload, UniversalAddresses.CLIENT_ADDRESS_BYTES, UniversalAddresses.CLIENT_ADDRESS_BYTES));
    }

    private String dataPayloadSummary(byte[] payload) {
        int size = UniversalPacketIO.readInt(payload, UniversalAddresses.CLIENT_ADDRESS_BYTES * 2);
        return routedClientsSummary(payload) + " compressedBytes=" + size;
    }

    private String errorPayloadSummary(byte[] payload) {
        int code = UniversalPacketIO.readInt(payload, UniversalAddresses.CLIENT_ADDRESS_BYTES * 2);
        return routedClientsSummary(payload) + " code=" + code;
    }

    private String stringListsSummary(byte[] payload) {
        StringLists lists = readStringLists(payload);
        return "add='" + lists.additive() + "' remove='" + lists.subtractive() + "'";
    }

    private record StringLists(String additive, String subtractive) {
    }
}
