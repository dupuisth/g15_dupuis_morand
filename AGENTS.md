# Codex Instructions

## Project Goal

This repository is for an IN363 networking course project. The application simulates a TCP/IP network in Java to explore:

- client/server communication
- routing between servers
- admin-driven network management
- compact bit-level message serialization

Keep changes aligned with that educational networking simulation goal. Prefer clear, explicit code over clever abstractions that hide protocol or routing behavior.

## Repository Layout

- `README.md` documents the architecture and protocol design. Treat it as the main source of project intent.
- `docs/` contains design artifacts, currently a Visual Paradigm file.
- `Notwork/` is the Java 21 Maven project.
- Main Java package: `com.gr15`.
- Application entry point: `Notwork/src/main/java/com/gr15/Application.java`.

## Build and Verification

Run Maven commands from `Notwork/`.

```powershell
cd Notwork
mvn compile
```

There are currently no dedicated tests in the repository. For behavioral changes, at minimum run `mvn compile`. If adding testable protocol or utility logic, prefer adding focused unit tests under `Notwork/src/test/java`.

## Runtime Shape

The application can start as:

- `admin`
- `server`
- `client`

The main entry point selects the app from args or falls back to an interactive CLI.

Useful argument forms:

- Server: `server serverId=<id> clientport=<port> serverport=<port> adminport=<port> neighbor=<neighborId>:<host>:<port>`
- Client: `client hostname=<host> port=<port>`
- Admin compact server config: `server=<id>:<clientPort>:<serverPort>:<adminPort>/<neighborId>:<host>:<port>`
- Admin compact client config: `client=<host>:<port>`

Valid network ports are defined in `ServerConfig.PORT_MIN` and `ServerConfig.PORT_MAX`.

## Core Architecture

The server is centered on `ServerApp` and specialized managers:

- `ClientManager` handles client connections.
- `ServerManager` handles neighbor server connections and broadcast/routing behavior.
- `AdminManager` handles admin console connections.

Managers use queues to decouple socket/listening threads from state mutation. Preserve this model: low-level listening threads should enqueue work, and manager `pollEvents()` methods should process state changes.

Connection lifecycle usually involves:

1. typed connection object
2. listening thread
3. handler thread
4. wrapper object
5. manager-owned collection

When changing connection cleanup, make sure sockets and threads are stopped deliberately.

## Messaging and Protocol Rules

`Message` is a bit-packed byte buffer. Message classes should keep read/write order exactly symmetric.

Important invariants:

- Message IDs are currently `Message.MESSAGE_ID_BITS` bits.
- Client IDs are split into server/local parts based on `Constants.SERVER_ID_BITS` and `Constants.LOCAL_ID_BITS`.
- Broadcast IDs and TTL values use the bit widths in `Constants`.
- Strings are encoded through `Message.ENCODING_CHARSET`.
- Avoid assuming byte alignment; writes and reads can cross byte boundaries.

When adding a message type:

1. add it to the correct enum category (`MessageSTC`, `MessageCTS`, `MessageSTS`, `MessageSTA`, or `MessageATS`)
2. create or update the typed message class
3. keep `CreateMessage(...)` and `ReadMessage(...)` in the same field order
4. update the relevant handler/manager dispatch

## Style Preferences

- Follow the existing Java style: explicit classes, simple control flow, and package-local organization by role.
- Keep protocol constants centralized in `Constants` unless a value is truly local.
- Prefer descriptive logs through `Logger` over ad hoc `System.out` in non-CLI infrastructure.
- Avoid broad refactors while fixing protocol, routing, or threading issues.
- Do not commit generated Maven output from `Notwork/target`.

## Things To Be Careful With

- This is a multithreaded socket application. Be cautious with shared manager collections and event queues.
- Server-to-server connections include pending and identified states; do not collapse them without checking duplicate-connection and identification behavior.
- Broadcast messages use IDs and TTL to prevent loops. Any routing change should preserve duplicate suppression and TTL handling.
- The compact CLI/admin args are useful for launching multiple nodes; avoid breaking them when changing config parsing.
