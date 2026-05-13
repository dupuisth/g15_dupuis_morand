# Notwork

Notwork is a Java 21 TCP messaging project with three executable roles:

- `server`: accepts clients, admin consoles, and neighboring servers.
- `client`: connects to one server and exchanges messages with known clients.
- `admin`: starts/manages local servers and clients, or connects as an admin console.

The server-to-server layer speaks the universal network protocol described in
`../docs/specification_reseau_universel.pdf`. Internally, the application still
uses compact `STS_*`, `CTS_*`, `STC_*`, `ATS_*`, and `STA_*` messages.

## Requirements

- Java 21
- Maven, if you want to use the `pom.xml` build

The project has no external runtime dependency.

## Build

With Maven:

```bash
mvn compile
```

Without Maven:

```bash
javac --release 21 -d /tmp/notwork-classes $(find src/main/java -name '*.java')
```

## Run

Main class:

```bash
com.gr15.Application
```

When using the direct `javac` command above:

```bash
java -cp /tmp/notwork-classes com.gr15.Application <role> <args...>
```

## Server Arguments

Server arguments:

- `server`
- `serverId=<id>`
- `clientport=<port>`
- `serverport=<port>`
- `adminport=<port>`
- `neighbor=<serverId>:<hostname>:<serverPort>`

Example with two neighboring servers:

```bash
java -cp /tmp/notwork-classes com.gr15.Application server serverId=1 clientport=2101 serverport=2102 adminport=2103 neighbor=2:localhost:2112
java -cp /tmp/notwork-classes com.gr15.Application server serverId=2 clientport=2111 serverport=2112 adminport=2113 neighbor=1:localhost:2102
```

Only one side opens the TCP socket between two configured neighbors. The peer id
is confirmed by the universal `CONNECT` packet before the connection is fully
registered.

Compact server argument:

```text
server=<id>:<clientPort>:<serverPort>:<adminPort>/<neighborId>:<host>:<port>/...
```

Example:

```bash
java -cp /tmp/notwork-classes com.gr15.Application admin server=1:2101:2102:2103/2:localhost:2112 server=2:2111:2112:2113/1:localhost:2102
```

## Client Arguments

Client arguments:

- `client`
- `hostname=<serverHost>`
- `port=<serverClientPort>`

Example:

```bash
java -cp /tmp/notwork-classes com.gr15.Application client hostname=localhost port=2101
```

Compact client argument:

```text
client=<hostname>:<serverClientPort>
```

## Admin Console Arguments

Admin console compact argument:

```text
admin console=<hostname>:<serverAdminPort>
```

Example:

```bash
java -cp /tmp/notwork-classes com.gr15.Application admin console=localhost:2103
```

## Architecture

Important packages:

- `com.gr15.common`: shared bit-level message format and ids.
- `com.gr15.common.message.universal`: universal server-to-server wire protocol.
- `com.gr15.server.connections`: socket connection wrappers.
- `com.gr15.server.managers`: lifecycle, routing, and message dispatch.
- `com.gr15.server.routing`: network graph and next-hop computation.
- `com.gr15.client`: client application and client-side connection.
- `com.gr15.admin`: process manager and admin console.

The server-to-server path is intentionally split:

1. `ServerConnection` owns the socket-facing server connection.
2. `UniversalPacketIO` reads/writes the universal binary packet format.
3. `UniversalMessageAdapter` translates between universal packets and internal
   `STS_*` messages.
4. `ServerManager` and `ServerRoutingCoordinator` route internal messages.

This keeps the universal protocol isolated from the routing logic.

## Universal Protocol Support

Implemented universal message types:

- `CONNECT`
- `PING`
- `PONG`
- `DATA`
- `ERROR`
- `TOPOLOGY`
- `LISTE_CLIENT`

Notes:

- All universal integer fields are encoded as 32-bit big-endian values.
- Universal server addresses use `Sxx`.
- Universal client addresses use `Sxx_Cx`.
- Internal routing uses full bitmask snapshots; the universal adapter converts
  them to additive/subtractive topology and client-list updates per connection.
- `DATA` currently sends the existing UTF-8 payload and appends the required
  parity byte. The specification mentions LZ78, but does not define the exact
  binary encoding used for the compressed payload.

## Known Limits

- Universal client addresses only support one local client digit (`Sxx_Cx`), so
  only local ids `0..9` can be represented on the universal wire format.
- The internal server id range is controlled by `Constants.SERVER_ID_BITS`.
- `mvn test` requires Maven to be installed; the project currently has no test
  source tree.
