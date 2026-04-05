This project is part of the IN363 Network course. Its goal is to simulate a network in order to understand key networking concepts such as : 

- Client / Server communication
- Routing between servers

The system is entirely implemented in Java and uses TCP/IP communication.

# Architecture

The project is split in three main applications : 

- Client
- Server
- Admin
## Client 

The client application allows users to send and receive data over the network. It connects to a server at start-up.

## Server

The server acts as a relay and routing node in the network.

## Admin

The admin is used to create new servers and clients. It can also create a console to manage a server (add / remove neighbors)

# Technical choices

## Servers and Clients Identification

In the network, both servers and clients are uniquely identified using integers.

### Server Identification

A server is identified by a `ServerId` which is a simple integer: 

```
0, 1, 2, ...
```

### Client Identification

A client is identified using a `ClientId`, which is composed of two parts:

- A `ServerId` : identifies the server hosting the client
- A `LocalId` : identifies the client locally within that server

![[id.excalidraw.svg]]

### Internal representation

The `ClientId` is encoded as a single integer by combining both parts:

- The **ServerId** occupies the **most significant bits (MSB)**
- The **LocalId** occupies the **least significant bits (LSB)**

> The numbers of bits allocated for each part is defined in `commons/Constants.java`.
> This determines the maximum number of servers and the maximum numbers of clients per server.

## Messaging System

> The messaging system is heavily inspired from [Riptide Networking](https://github.com/RiptideNetworking/Riptide) and focuses on bit-level control for efficient serialization

### Core concept

Instead of writing full bytes or objects, the system writes **exact amounts of bits**.

This allow:

- Reduced bandwidth usage
- Custom protocol design
- Precise control over frame structure

### Message categories

Messages are categorized depending on the sender and receiver :

- StC -> Server to Client
- StS -> Server to Server
- StA -> Server to Admin
- CtS -> Client to Server
- AtS -> Admin to Server

### Message structures

Each message is a **bit-packed buffer** stored in a byte array.

The structure is always the same: 

- **Message ID** (currently 4 bits), define the *type* of message (not categorie !)
- **Payload** (number of bits vary by message), encoded data specific to the message type

```
| MessageId (4 bits) | Payload (...) |
```

### Writing data

The `Message` class provides low-level methods to write data, each of those methods increment a cursor to keep track of the current bit to write to (`writeBit`).

**Add bits / integers**

```Java
addBits(byte value, int amount);
addInt(int value, int amount);
```

This writes only the specified number of bits and automatically packs data across by boundaries.

**Add raw data**

```Java
addBytes(byte[] bytes, boolean writeLength);
```

This optionally prefixes the length (32 bits) then writes all bytes sequentially.

> Note: 32 bits for the length is too much, this will surely change later.

**Add strings**

```Java
addString(String value);
```

The string are encoded using **UTF-8** (can be modified by changing a constant). It encode the string to bytes and call `addBytes`, so it stores the length of the string in the prefix.

### Reading data

Reading follows the same logic as writing, using a read cursor (`readBit`)

```Java
byte readByte(int amount);
int readInt(int amount);
byte[] readBytesWithLength();
String readString();
```

The data must be read **in the exact same order** as it was written. Otherwise, decoding will be incorrect.

### Bit-Level Packing

The system writes data **bit by bit**, not byte-aligned.

For example : 

- Write 3 bits -> Continues in same byte
- Write 10 bits -> Spans across multiple bytes

This is handled by:

- `Converter.ByteToBits(…)`
- `Converter.IntToBits(…)`

### Message Types

Each message category (Client to Server, Server to Server, etc.) contains multiple message types.

For example, the **Server to Client** category includes:

- `HELLO`: welcome message sent to the client, containing a welcome message and the client identifier
- `MESSAGE`: sent when a client receives a message, containing the sender identifier and the message content
- `NEW_CLIENT`: sent when a new client connects
- …

Each category defines its message types in a dedicated enumeration:

- `MessageSTC` for Server to Client
- `MessageCTS` for Client to Server
- …

> Using enumerations makes message identifiers more reliable. Each identifier is defined once and reused everywhere, which reduces mistakes and also makes it easy to retrieve a message type from its numeric ID.

For each message type, a dedicated class is used to encapsulate its data and its serialization logic.

#### Example of a message class

```java
public class STC_MessageHello {
    public static final int ID = MessageSTC.HELLO.getId();

    private final String welcomeMessage;
    private final int clientId;

    private STC_MessageHello(int clientId, String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
        this.clientId = clientId;
    }

    public static Message CreateMessage(int clientId, String welcomeMessage) {
        Message message = new Message(ID);
        message.addInt(clientId, TOTAL_CLIENT_ID_BITS);
        message.addString(welcomeMessage);
        return message;
    }

    public static STC_MessageHello ReadMessage(Message message) {
        int clientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String welcome = message.readString();
        return new STC_MessageHello(clientId, welcome);
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public int getClientId() {
        return clientId;
    }

    @Override
    public String toString() {
        return "STC_MessageHello{" +
                "welcomeMessage='" + welcomeMessage + '\'' +
                ", clientId=" + clientId +
                '}';
    }
}
```

In this design, the message data is explicitly defined in the class. Each message type provides:

- a method to create a serialized `Message` from structured data
- a method to read and decode a `Message` back into a typed object

This makes the messaging system easier to use and more reliable, since each message type is isolated and its write/read logic is grouped in a single place.

## Server Architecture

The server is organized around a central `ServerApp` class and three specialized managers:

- `ClientManager`
- `ServerManager`
- `AdminManager`

This design separates responsibilities clearly and makes the server easier to maintain and extend.

### Global Structure

The `ServerApp` is the entry point of the server application. It is responsible for:

- validating the initial configuration
- creating all managers
- starting and stopping them in the correct order
- running the main event loop

The server uses the following managers:

- `ClientManager` handles connections with clients
- `ServerManager` handles connections with neighbor servers
- `AdminManager` handles connections with the admin application

This means that each communication channel has its own logic, message types, and connection lifecycle.

### Main Application Loop

Once all managers are started, the server enters a main loop:

```java
while (!isStopping) {
    ThreadUtils.safeSleep(Constants.SERVER_POLL_DELAY_MS);

    clientManager.pollEvents();
    serverManager.pollEvents();
    adminManager.pollEvents();
}
```

This loop acts as the central coordination point of the server.

Instead of processing all network operations directly inside background threads, the server delegates most of the work to manager-specific event queues, which are then processed during each polling cycle.

This architecture provides two main benefits:

- it limits the amount of logic executed in low-level network threads
- it centralizes state updates in a more controlled way

### Manager Abstraction

All managers inherit from the abstract `Manager<T, K>` class.

This base class provides the common socket infrastructure:

- creation of the listening server socket
- creation of a `SocketAcceptingThread`
- default creation of `ListeningThread` instances
- generic helper methods for accessing active connections

Each concrete manager must implement:

- `pollEvents()`
- `handleNewSocket(…)`
- `stopConnection(…)`
- `onMessageRead(…)`
- `onListeningError(…)`
- `send(…)`
- `sendToAll(…)`

This abstraction avoids code duplication and ensures that all manager types follow the same lifecycle.

### Connection Handling Model

Each manager uses a similar model when a new socket is accepted:

1. create a typed connection object (`ClientConnection`, `ServerConnection`, or `AdminConnection`)
2. create a dedicated listening thread for incoming messages
3. create a dedicated handler thread for connection-specific logic
4. wrap everything into a wrapper object
5. store the wrapper in the manager's connection list

This means that a connection is not represented only by a socket, but by a full runtime structure including:

- the typed connection object
- the listening thread
- the handler thread

### Role of Listening Threads

Listening threads are responsible only for low-level network reading.

When a message is received, the thread does not process it immediately. Instead, it forwards it to the manager through `onMessageRead(…)`, which places it into a queue.

This decouples message reception from message processing.

If a listening error occurs, the connection is also queued for removal instead of being destroyed immediately from the listening thread.

### Role of Handler Threads

Each connection also has an associated handler thread.

A handler thread is responsible for connection-specific background behavior.

Examples:

- `ClientHandler` sends the initial welcome message to a newly connected client and notifies other clients that a new client has joined
- `ServerHandler` periodically sends identification messages until the remote server is fully identified
- `AdminHandler` is currently lightweight, but it provides a dedicated place for future admin-specific background logic

This makes the design more extensible, because all recurring or asynchronous behaviors related to a connection are isolated in their own handler.

### Event Queues

Managers do not process all operations immediately. Instead, they use queues to defer actions to the main polling phase.

Typical queues include:

- a queue of received messages
- a queue of outgoing messages
- a queue of connections to remove

For example, the `ServerManager` uses:

- `messageReceivedQueue`
- `messageToSendQueue`
- `connectionsToRemoveQueue`

During `pollEvents()`, the manager:

1. removes and cleans dead connections
2. processes received messages
3. sends queued outgoing messages

This queue-based architecture improves thread safety and keeps state changes centralized.

### ServerManager Specific Architecture

The `ServerManager` is the most complex manager because it handles inter-server communication and network propagation.

It manages two categories of connections:

- fully identified neighbor servers
- pending connections that are not authenticated yet

Pending connections are stored separately until an `IDENTIFY` message is received. Once the remote server identity is known, the connection is moved into the main server connection table.

This allows the server to accept incoming server connections before their identity is fully established.

### Neighbor Connection Thread

In addition to accepted incoming connections, the `ServerManager` also actively tries to connect to configured neighbors.

This is done by the `ServerConnectToNeighborThread`.

Its role is to:

- periodically inspect the configured neighbors
- detect which ones are not currently connected
- initiate missing connections
- avoid duplicate connections

To avoid symmetric duplicate connections, the implementation uses a simple rule: the connection is initiated only by one side, based on the server identifier ordering.

This background thread makes the network self-healing: if a neighbor becomes reachable again, the server can reconnect automatically.

### Message Processing in the ServerManager

The `ServerManager` processes messages in two stages:

1. read the raw `Message`
2. decode its message ID and dispatch it to the correct typed handler

For example:

- `IDENTIFY` registers the remote server identity
- `BROADCAST_CHAT` propagates chat messages through the server network

This separation keeps the message-processing logic clean and strongly typed.

### Broadcast Support

The server architecture includes a broadcast mechanism for propagating messages across the network.

To avoid infinite loops and duplicate processing, each broadcast message includes:

- a unique broadcast identifier
- a TTL (time to live)

The `ServerManager` keeps a map of recently seen broadcast IDs. If a message was already processed recently, it is ignored.

This ensures that broadcasts can travel across a graph of interconnected servers without being processed indefinitely.

### Shutdown and Cleanup

When the server stops, `ServerApp` stops all managers.

Each manager then:

- closes its server socket
- stops its accepting thread
- interrupts all connection-specific threads
- waits for them to terminate
- removes the connection from its internal structures

This explicit cleanup phase avoids leaving half-open connections or orphan threads behind.

### Summary

The server architecture is based on three principles:

- **separation of responsibilities** through specialized managers
- **decoupling of I/O and logic** through queues and polling
- **thread isolation** through dedicated listening and handler threads

This results in an architecture that is modular, scalable, and well adapted to a simulated distributed network.