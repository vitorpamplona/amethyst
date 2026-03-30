# NIP-BE: Nostr BLE Communications Protocol

Kotlin Multiplatform implementation of [NIP-BE](https://github.com/nostr-protocol/nips/blob/master/BE.md)
for peer-to-peer Nostr event synchronization over Bluetooth Low Energy.

Compatible with [KoalaSat/samiz](https://github.com/KoalaSat/samiz).

## Quick Start (Android)

```kotlin
// 1. Create the mesh (auto-wires transport)
val mesh = BleNostrMesh(AndroidBleTransport(context))

// 2. Listen for events from nearby peers
mesh.onEvent { event, peer ->
    Log.d("BLE", "Received kind ${event.kind} from ${peer.deviceUuid}")
    myDatabase.save(event)
}

// 3. Optional: track peer connectivity
mesh.onPeerConnected { peer -> updatePeerCount(mesh.connectedPeers().size) }
mesh.onPeerDisconnected { peer -> updatePeerCount(mesh.connectedPeers().size) }
mesh.onError { error -> Log.e("BLE", error) }

// 4. Start (begins advertising + scanning)
mesh.start()

// 5. Broadcast events to all connected peers
mesh.broadcast(mySignedEvent)

// 6. Stop when done
mesh.stop()
```

### Required Android Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Request these at runtime before calling `mesh.start()`.

## How It Works

When two devices running NIP-BE discover each other:

1. **Discovery** - Both devices advertise and scan for the Nostr BLE service UUID
2. **Role assignment** - The device with the higher UUID becomes the GATT Server (relay),
   the other becomes the GATT Client
3. **Connection** - The client connects to the server's GATT service
4. **Messaging** - NIP-01 JSON messages are DEFLATE-compressed, split into chunks,
   and exchanged via BLE characteristics
5. **Event spreading** - New events are automatically forwarded to all connected peers

## Package Structure

```
nipBEBle/
  BleNostrMesh.kt           # Easy-to-use facade (start here)
  BleConfig.kt              # NIP-BE constants (UUIDs, sizes)
  BlePeer.kt                # Peer data model
  BleRole.kt                # Role assignment (SERVER/CLIENT)

  protocol/                  # Wire format
    BleMessageChunker.kt     # DEFLATE compression + chunk splitting/joining
    BleChunkAssembler.kt     # Reassembles incoming chunks into messages

  transport/                 # Platform BLE abstraction
    BleTransport.kt          # Interface for BLE operations
    AndroidBleTransport.kt   # Android implementation (androidMain)

  relay/                     # Nostr relay protocol over BLE
    BleNostrClient.kt        # Client side (implements IRelayClient)
    BleNostrServer.kt        # Server side (receives commands, sends messages)
    BleMeshManager.kt        # Orchestrates discovery, connections, broadcasting
```

## Advanced Usage

### Use a BLE peer as a standard relay client

`BleNostrClient` implements `IRelayClient`, so you can use BLE peers alongside
WebSocket relays in the existing subscription infrastructure:

```kotlin
val relayClient = mesh.getRelayClient(peer.deviceUuid)
relayClient?.sendIfConnected(ReqCmd("sub1", listOf(filter)))
```

### Access raw protocol messages

```kotlin
mesh.onMessage { relay, msg ->
    // Nostr messages received when acting as client (EVENT, EOSE, OK, etc.)
}

mesh.onCommand { server, cmd ->
    // Nostr commands received when acting as server (REQ, EVENT, CLOSE, etc.)
}
```

### Implement a custom BLE transport

For platforms without a built-in transport, implement `BleTransport`:

```kotlin
class MyPlatformBleTransport : BleTransport {
    override val deviceUuid = UUID.randomUUID().toString()
    override fun startAdvertising() { /* ... */ }
    override fun startScanning() { /* ... */ }
    // ... other methods
}
```

### Use BleMeshManager directly

For full control over the relay protocol layer:

```kotlin
val transport = AndroidBleTransport(context)
val manager = BleMeshManager(transport, object : BleMeshListener {
    override fun onEventReceived(event: Event, fromPeer: BlePeer) { /* ... */ }
})
transport.setListener(manager)
manager.start()
```

## Wire Protocol

Messages follow [NIP-01](https://github.com/nostr-protocol/nips/blob/master/01.md) JSON format,
compressed with DEFLATE and split into chunks:

```
[chunk index (1 byte)][payload (up to 500 bytes)][total chunks (1 byte)]
```

| GATT Characteristic | UUID | Direction |
|---------------------|------|-----------|
| Write | `87654321-0000-1000-8000-00805f9b34fb` | Client -> Server |
| Read (Notify) | `12345678-0000-1000-8000-00805f9b34fb` | Server -> Client |

Service UUID: `0000180f-0000-1000-8000-00805f9b34fb`
