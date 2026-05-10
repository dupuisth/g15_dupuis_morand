# TASKS

Backlog derived from `docs/Sujet_Projet_IN363_V4.pdf` and a current code inspection on 2026-05-10.

## Protocol and Frame Format

- [ ] Specify the complete frame format in the repository documentation: header fields, payload fields, length/framing strategy, optional end-of-frame marker, bit widths, encoding, and error behavior.
- [x] Add a real destination client identifier to client data messages. `CTS_Message` now carries the destination client id.
- [x] Define and implement server/client error frames for unknown or unreachable destinations.
- [ ] Decide which frames must be forwarded unchanged and enforce that rule. The specification says a server should not modify a frame when relaying it, but the current implementation converts client messages into server/client broadcast messages.
- [ ] Add focused tests for bit-level read/write symmetry, non-byte-aligned values, maximum values, string encoding, and invalid frame lengths.
- [ ] Reduce or justify the current 32-bit string/byte length prefix in `Message.addBytes`; the README already notes that this is likely too large.

## Client Features

- [x] Let the user choose a destination client from known clients before sending user data.
- [x] Maintain and display the known client list from `NEW_CLIENT` and `REMOVE_CLIENT` messages.
- [ ] Implement the client liveness response required by the specification, for example a ping/pong or heartbeat response to the server.
- [ ] Display corruption warnings to the user when parity validation fails.
- [ ] Make the client input loop respond cleanly to disconnection while waiting for console input. This is currently marked as a TODO in `ClientApp`.

## Server Routing and Delivery

- [x] Replace network-wide chat broadcast with addressed delivery. A message for a local client should be sent only to that client.
- [x] Build and maintain a routing table for remote servers and clients.
- [x] Implement routing-table exchange between servers during network construction.
- [x] Route user data to the shortest path. If several shortest paths are equal, choose the path through the lowest server id as required by the specification.
- [x] Notify the sending client when the destination is unknown or unreachable.
- [x] Propagate new-client and removed-client information across the whole server network, not only to clients connected to the same server.
- [ ] Implement server liveness checks for both clients and neighbor servers. Current cleanup mostly depends on socket read errors.
- [ ] Stop routing frames toward clients or servers that fail liveness checks, and notify the rest of the network.
- [ ] Handle live link changes between servers by updating routing state and propagating the topology change.
- [x] Add duplicate-connection handling for identified server connections so a new connection cannot silently overwrite an existing `connectionsToServer[serverId]`.
- [ ] Review broadcast TTL handling before reusing it for routing. Current broadcast support prevents loops for chat, but it does not satisfy shortest-path routing.

## Admin Features

- [ ] Add an admin action to stop selected client processes during runtime. The admin can currently launch clients but does not track or stop them.
- [ ] Add an admin workflow for collective startup/configuration: server names/IPs, clients, neighbor links, and the "tout est ok" network-construction trigger described in the spec.
- [ ] Allow admin-defined client capacity or initial fixed clients per server if the final project model keeps the spec's "n clients" / `C1`, `C2`, `C3` requirement.
- [ ] Make admin link modifications identify neighbors unambiguously. `REMOVE_NEIGHBOR` currently removes by hostname only and ignores port/server id.
- [ ] Validate admin-added neighbors before mutating server config, including port range, duplicates, and self-links.
- [ ] Ensure admin `ADD_NEIGHBOR` can work when the neighbor id is initially unknown, then updates the stored neighbor entry after server identification.
- [ ] Add admin responses/acknowledgements for successful and failed actions, not only `LIST_NEIGHBOR`.
- [ ] Store launched server/client/admin processes if the admin program is expected to manage their lifecycle after creation.
- [ ] Provide a minimal-config launch path, preferably from a single topology/config file or compact argument set documented for the teacher/demo scenario.

## Data Encoding and Integrity

- [ ] Implement LZ78 encoding for user data, or document and implement the exact alternative if the class agreement allows one.
- [ ] Add a global parity bit to user data frames.
- [ ] Validate the parity bit on receive and surface corruption risk to the user.
- [ ] Add tests for LZ78 round trips, empty input, repeated patterns, alphanumeric input, and parity failure detection.

## Interoperability Requirements

- [x] Update server id capacity to match the spec's `S01` to `S32` range. `SERVER_ID_BITS = 5` supports 32 server ids.
- [ ] Define how project-local frames map to the future common half-class frame specification.
- [ ] Add the required "translation" program/layer once the common class specification is known.
- [ ] Keep compatibility tests or fixtures for local protocol frames and translated common frames.

## Documentation and Design Deliverables

- [ ] Add or export the message exchange diagram in a reviewable format. `docs/design.vpp` exists, but the repository should include an easily readable diagram or image/PDF export.
- [ ] List all possible messages exhaustively between Client, Server, Admin, and user interactions.
- [ ] Document pseudocode for frame exchange, client creation, client shutdown, and routing-table modification.
- [ ] Extend `README.md` with the final protocol, routing algorithm, admin workflows, liveness behavior, and demo launch commands.
- [ ] Document the current limitations and any intentional deviations from the original PDF specification.

## Verification

- [ ] Add unit tests for protocol serialization and routing-table logic once those modules are implemented.
- [ ] Add integration/demo scripts for a multi-server topology with at least one remote client-to-client message.
- [ ] Verify the required compile command from `Notwork/`: `mvn compile`.
- [ ] Add a manual demo checklist covering local delivery, remote delivery, unreachable destination, client removal, server link change, liveness timeout, and admin stop/reset.
