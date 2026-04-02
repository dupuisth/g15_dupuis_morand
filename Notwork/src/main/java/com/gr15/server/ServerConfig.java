package com.gr15.server;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import static com.gr15.common.Constants.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for the server
 */
public class ServerConfig {
    public static final String ARG_SERVER_ID_KEY = "serverId=";
    public static final String ARG_SOCKET_CLIENT_PORT_KEY = "clientport=";
    public static final String ARG_SOCKET_SERVER_PORT_KEY = "serverport=";
    public static final String ARG_SOCKET_ADMIN_PORT_KEY = "adminport=";
    public static final String ARG_NEIGHBOR_KEY = "neighbor=";

    /**
     * Form:
     * server=<serverId>:<clientPort>:<serverPort>:<adminPort>/<neighborId>:<host>:<port>/<neighborId>:<host>:<port>/...
     * Example:
     * server=15:2222:2223:2224/5:localhost:2202/7:192.168.1.10:2302
     */
    public static final String ARG_COMPACT_KEY = "server=";

    // Arbitrary values, should just not be using the well known ports
    public static final int PORT_MAX = 4300;
    public static final int PORT_MIN = 2000;

    private Integer serverId;
    private Integer clientSocketPort;
    private Integer serverSocketPort;
    private Integer adminSocketPort;
    private final ArrayList<NeighborServerInfo> neighbors;

    public ServerConfig() {
        this.serverId = null;
        this.clientSocketPort = null;
        this.serverSocketPort = null;
        this.adminSocketPort = null;
        this.neighbors = new ArrayList<>();
    }

    public ServerConfig(
            Integer serverId,
            Integer clientSocketPort,
            Integer serverSocketPort,
            Integer adminSocketPort
    ) {
        this.serverId = serverId;
        this.clientSocketPort = clientSocketPort;
        this.serverSocketPort = serverSocketPort;
        this.adminSocketPort = adminSocketPort;
        this.neighbors = new ArrayList<>();
    }

    public ServerConfig(
            Integer serverId,
            Integer clientSocketPort,
            Integer serverSocketPort,
            Integer adminSocketPort,
            List<NeighborServerInfo> neighbors
    ) {
        this.serverId = serverId;
        this.clientSocketPort = clientSocketPort;
        this.serverSocketPort = serverSocketPort;
        this.adminSocketPort = adminSocketPort;
        this.neighbors = new ArrayList<>();
        if (neighbors != null) {
            this.neighbors.addAll(neighbors);
        }
    }

    public boolean validateConfiguration() {
        if (serverId == null || serverId < 0 || serverId >= MAX_SERVERS) {
            return false;
        }

        if (clientSocketPort == null || clientSocketPort < PORT_MIN || clientSocketPort > PORT_MAX) {
            return false;
        }

        if (serverSocketPort == null || serverSocketPort < PORT_MIN || serverSocketPort > PORT_MAX) {
            return false;
        }

        if (adminSocketPort == null || adminSocketPort < PORT_MIN || adminSocketPort > PORT_MAX) {
            return false;
        }

        if (clientSocketPort.equals(serverSocketPort)
                || serverSocketPort.equals(adminSocketPort)
                || clientSocketPort.equals(adminSocketPort)) {
            return false;
        }

        Set<Integer> neighborIds = new HashSet<>();
        for (NeighborServerInfo neighbor : neighbors) {
            if (neighbor == null) {
                return false;
            }

            if (neighbor.getServerId() < 0 || neighbor.getServerId() >= MAX_SERVERS) {
                return false;
            }

            if (neighbor.getServerId() == serverId) {
                return false;
            }

            if (neighbor.getServerHostname() == null || neighbor.getServerHostname().isBlank()) {
                return false;
            }

            if (neighbor.getServerPort() < PORT_MIN || neighbor.getServerPort() > PORT_MAX) {
                return false;
            }

            if (!neighborIds.add(neighbor.getServerId())) {
                return false; // duplicate neighbor id
            }
        }

        return true;
    }

    public static ServerConfig FromCli() {
        int serverId = CliHelper.inputInt("Enter serverId", 0, MAX_SERVERS - 1);
        int clientSocketPort = CliHelper.inputInt("Enter client socket port", PORT_MIN, PORT_MAX);
        int serverSocketPort = CliHelper.inputInt("Enter server socket port", PORT_MIN, PORT_MAX);
        int adminSocketPort = CliHelper.inputInt("Enter admin socket port", PORT_MIN, PORT_MAX);

        ServerConfig config = new ServerConfig(serverId, clientSocketPort, serverSocketPort, adminSocketPort);

        // Optional neighbor input loop could be added here if you want
        return config;
    }

    public static ServerConfig FromArgs(String[] args) {
        ServerConfig config = new ServerConfig();

        for (String arg : args) {
            if (arg == null) {
                continue;
            }

            if (arg.startsWith(ARG_SERVER_ID_KEY)) {
                try {
                    config.setServerId(Integer.parseInt(arg.substring(ARG_SERVER_ID_KEY.length())));
                } catch (NumberFormatException ignored) {
                }

            } else if (arg.startsWith(ARG_SOCKET_CLIENT_PORT_KEY)) {
                try {
                    config.setClientSocketPort(Integer.parseInt(arg.substring(ARG_SOCKET_CLIENT_PORT_KEY.length())));
                } catch (NumberFormatException ignored) {
                }

            } else if (arg.startsWith(ARG_SOCKET_SERVER_PORT_KEY)) {
                try {
                    config.setServerSocketPort(Integer.parseInt(arg.substring(ARG_SOCKET_SERVER_PORT_KEY.length())));
                } catch (NumberFormatException ignored) {
                }

            } else if (arg.startsWith(ARG_SOCKET_ADMIN_PORT_KEY)) {
                try {
                    config.setAdminSocketPort(Integer.parseInt(arg.substring(ARG_SOCKET_ADMIN_PORT_KEY.length())));
                } catch (NumberFormatException ignored) {
                }

            } else if (arg.startsWith(ARG_NEIGHBOR_KEY)) {
                try {
                    String neighborRaw = arg.substring(ARG_NEIGHBOR_KEY.length());
                    config.addNeighbor(NeighborServerInfo.fromString(neighborRaw));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return config;
    }

    public static ServerConfig FromCompactArgs(String arg) {
        if (arg == null || !arg.startsWith(ARG_COMPACT_KEY)) {
            throw new IllegalArgumentException("Invalid compact server argument: " + arg);
        }

        String dataString = arg.substring(ARG_COMPACT_KEY.length());
        String[] sections = dataString.split("/");

        if (sections.length == 0 || sections[0].isBlank()) {
            throw new IllegalArgumentException("Missing server main configuration in arg=" + arg);
        }

        try {
            String[] mainParts = sections[0].split(":");
            if (mainParts.length != 4) {
                throw new IllegalArgumentException(
                        "Main server config must be <id>:<clientPort>:<serverPort>:<adminPort>, arg=" + arg
                );
            }

            ServerConfig config = new ServerConfig();
            config.setServerId(Integer.parseInt(mainParts[0]));
            config.setClientSocketPort(Integer.parseInt(mainParts[1]));
            config.setServerSocketPort(Integer.parseInt(mainParts[2]));
            config.setAdminSocketPort(Integer.parseInt(mainParts[3]));

            for (int i = 1; i < sections.length; i++) {
                String neighborSection = sections[i].trim();
                if (neighborSection.isEmpty()) {
                    continue;
                }
                config.addNeighbor(NeighborServerInfo.fromString(neighborSection));
            }

            return config;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in compact server argument arg=" + arg, e);
        }
    }

    public ArrayList<String> toArgs() {
        ArrayList<String> args = new ArrayList<>();
        args.add(ARG_SERVER_ID_KEY + serverId);
        args.add(ARG_SOCKET_CLIENT_PORT_KEY + clientSocketPort);
        args.add(ARG_SOCKET_SERVER_PORT_KEY + serverSocketPort);
        args.add(ARG_SOCKET_ADMIN_PORT_KEY + adminSocketPort);

        for (NeighborServerInfo neighbor : neighbors) {
            args.add(ARG_NEIGHBOR_KEY + neighbor.toArgumentValue());
        }

        return args;
    }

    public String toCompactArg() {
        StringBuilder builder = new StringBuilder();
        builder.append(ARG_COMPACT_KEY)
                .append(serverId).append(":")
                .append(clientSocketPort).append(":")
                .append(serverSocketPort).append(":")
                .append(adminSocketPort);

        for (NeighborServerInfo neighbor : neighbors) {
            builder.append("/").append(neighbor.toArgumentValue());
        }

        return builder.toString();
    }

    public Integer getServerId() {
        return serverId;
    }

    public void setServerId(Integer serverId) {
        this.serverId = serverId;
    }

    public Integer getClientSocketPort() {
        return clientSocketPort;
    }

    public void setClientSocketPort(Integer clientSocketPort) {
        this.clientSocketPort = clientSocketPort;
    }

    public Integer getServerSocketPort() {
        return serverSocketPort;
    }

    public void setServerSocketPort(Integer serverSocketPort) {
        this.serverSocketPort = serverSocketPort;
    }

    public Integer getAdminSocketPort() {
        return adminSocketPort;
    }

    public void setAdminSocketPort(Integer adminSocketPort) {
        this.adminSocketPort = adminSocketPort;
    }

    public ArrayList<NeighborServerInfo> getNeighbors() {
        return new ArrayList<>(neighbors);
    }

    public void addNeighbor(NeighborServerInfo neighbor) {
        if (neighbor != null) {
            neighbors.add(neighbor);
        }
    }

    public void clearNeighbors() {
        neighbors.clear();
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "serverId=" + serverId +
                ", clientSocketPort=" + clientSocketPort +
                ", serverSocketPort=" + serverSocketPort +
                ", adminSocketPort=" + adminSocketPort +
                ", neighbors=" + neighbors +
                '}';
    }

    public static class NeighborServerInfo {
        private final int serverId;
        private final String serverHostname;
        private final int serverPort;

        public NeighborServerInfo(int serverId, String serverHostname, int serverPort) {
            this.serverId = serverId;
            this.serverHostname = serverHostname;
            this.serverPort = serverPort;
        }

        /**
         * Format: <serverId>:<hostname>:<port>
         * Example: 4:localhost:2102
         */
        public static NeighborServerInfo fromString(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Neighbor string is empty");
            }

            String[] parts = value.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Invalid neighbor format. Expected <serverId>:<hostname>:<port>, got: " + value
                );
            }

            try {
                int serverId = Integer.parseInt(parts[0]);
                String hostname = parts[1];
                int serverPort = Integer.parseInt(parts[2]);

                return new NeighborServerInfo(serverId, hostname, serverPort);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid neighbor numeric values: " + value, e);
            }
        }

        public String toArgumentValue() {
            return serverId + ":" + serverHostname + ":" + serverPort;
        }

        public int getServerId() {
            return serverId;
        }

        public String getServerHostname() {
            return serverHostname;
        }

        public int getServerPort() {
            return serverPort;
        }

        @Override
        public String toString() {
            return "NeighborServerInfo{" +
                    "serverId=" + serverId +
                    ", serverHostname='" + serverHostname + '\'' +
                    ", serverPort=" + serverPort +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NeighborServerInfo that)) return false;
            return serverId == that.serverId
                    && serverPort == that.serverPort
                    && Objects.equals(serverHostname, that.serverHostname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serverId, serverHostname, serverPort);
        }
    }
}