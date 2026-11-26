package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.PartyPlayer
import com.example.data.PlayerConnState
import com.example.data.RoomConnectionHealth
import com.example.data.SyncPacket
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Real zero-data proximity multiplayer engine powered by Google Nearby Connections API.
 * Uses Strategy.P2P_STAR (BLE discovery + automatic high-bandwidth Wi-Fi Direct/Aware upgrade),
 * deterministic Host Election to prevent dual-host deadlocks, heartbeat RTT tracking,
 * and automatic exponential-backoff reconnect for dropped endpoints.
 */
class NearbyConnectionsTransport(
    context: Context
) : PartyTransport {

    companion object {
        private const val TAG = "BumpNearbyTransport"
        private const val SERVICE_ID = "com.aistudio.bumpparty.xkqtrm.nearby"
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val packetAdapter = moshi.adapter(SyncPacket::class.java)
    private val playerAdapter = moshi.adapter(PartyPlayer::class.java)

    private val _state = MutableStateFlow(TransportSnapshot())
    override val state: StateFlow<TransportSnapshot> = _state.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<SyncPacket>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingPackets: SharedFlow<SyncPacket> = _incomingPackets.asSharedFlow()

    private val connectedEndpoints = ConcurrentHashMap<String, PartyPlayer>()
    private val pendingReconnectEndpoints = ConcurrentHashMap<String, String>()
    private val seqCounter = AtomicLong(1L)
    private var localPlayerCache: PartyPlayer? = null
    private var heartbeatJob: Job? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val json = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return
            val packet = runCatching { packetAdapter.fromJson(json) }.getOrNull() ?: return

            when (packet.type) {
                "PING" -> {
                    sendToEndpoint(
                        endpointId,
                        SyncPacket(
                            type = "PONG",
                            senderId = localPlayerCache?.id ?: "local",
                            seq = seqCounter.incrementAndGet(),
                            timestampMs = packet.timestampMs
                        )
                    )
                }

                "PONG" -> {
                    val rtt = (System.currentTimeMillis() - packet.timestampMs).coerceIn(4L, 250L).toInt()
                    connectedEndpoints[endpointId]?.let { existing ->
                        connectedEndpoints[endpointId] = existing.copy(
                            rttMs = rtt,
                            status = PlayerConnState.ONLINE.name
                        )
                        syncPlayersToState()
                    }
                }

                "JOIN" -> {
                    val remotePlayer = runCatching { playerAdapter.fromJson(packet.payload) }.getOrNull()
                    if (remotePlayer != null) {
                        connectedEndpoints[endpointId] = remotePlayer.copy(
                            isLocal = false,
                            isBot = false,
                            status = PlayerConnState.ONLINE.name
                        )
                        syncPlayersToState()
                    }
                    _incomingPackets.tryEmit(packet)
                }

                else -> {
                    _incomingPackets.tryEmit(packet)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Instant proximity auto-accept so nearby friends form a room without manual PIN dialogs
            client.acceptConnection(endpointId, payloadCallback)
            _state.update {
                it.copy(statusLog = "靠近配对成功：正在握手 ${info.endpointName}...")
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    pendingReconnectEndpoints.remove(endpointId)
                    val displayName = _state.value.discoveredDevices
                        .firstOrNull { it.endpointId == endpointId }?.displayName ?: "附近玩家"
                    val placeholder = connectedEndpoints[endpointId] ?: PartyPlayer(
                        id = endpointId,
                        name = displayName.substringBefore("|"),
                        avatarEmoji = displayName.substringAfter("|", "🎉"),
                        colorIndex = (connectedEndpoints.size + 1) % 8,
                        isLocal = false,
                        isHost = false,
                        isBot = false,
                        isReady = true,
                        rttMs = 18
                    )
                    connectedEndpoints[endpointId] = placeholder.copy(status = PlayerConnState.ONLINE.name)

                    // Send our player metadata immediately
                    localPlayerCache?.let { me ->
                        sendToEndpoint(
                            endpointId,
                            SyncPacket(
                                type = "JOIN",
                                senderId = me.id,
                                seq = seqCounter.incrementAndGet(),
                                payload = playerAdapter.toJson(me)
                            )
                        )
                    }

                    syncPlayersToState()
                    _state.update {
                        it.copy(
                            health = RoomConnectionHealth.CONNECTED,
                            statusLog = "已直连 ${connectedEndpoints.size} 台附近设备（零流量模式）"
                        )
                    }
                    startHeartbeatLoop()
                }

                else -> {
                    _state.update {
                        it.copy(statusLog = "配对连接未完成，继续雷达扫描中...")
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val existing = connectedEndpoints[endpointId]
            if (existing != null) {
                connectedEndpoints[endpointId] = existing.copy(status = PlayerConnState.RECONNECTING.name)
                pendingReconnectEndpoints[endpointId] = existing.name
                syncPlayersToState()
                _state.update {
                    it.copy(
                        health = RoomConnectionHealth.RECONNECTING,
                        statusLog = "${existing.name} 信号波动，触发指数退避自动重连..."
                    )
                }
                scheduleAutoReconnect(endpointId)
            }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val cleanName = info.endpointName.substringBefore("|")
            val remoteDeviceId = info.endpointName.substringAfterLast("|", endpointId)
            _state.update { snap ->
                val list = snap.discoveredDevices.filterNot { it.endpointId == endpointId } +
                    DiscoveredNearbyDevice(
                        endpointId = endpointId,
                        displayName = cleanName,
                        isConnected = connectedEndpoints.containsKey(endpointId)
                    )
                snap.copy(
                    discoveredDevices = list,
                    statusLog = "发现附近玩家「$cleanName」，正在自动拉入房间..."
                )
            }

            // Deterministic Host Election tie-breaker: smaller ID initiates connection to avoid race deadlock
            val localId = localPlayerCache?.id ?: ""
            if (!connectedEndpoints.containsKey(endpointId) && localId <= remoteDeviceId) {
                val localTag = "${localPlayerCache?.name ?: "玩家"}|${localPlayerCache?.avatarEmoji ?: "🦊"}|$localId"
                client.requestConnection(localTag, endpointId, lifecycleCallback)
                    .addOnFailureListener { err ->
                        Log.w(TAG, "requestConnection deferred: ${err.message}")
                    }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { snap ->
                snap.copy(
                    discoveredDevices = snap.discoveredDevices.filterNot { it.endpointId == endpointId }
                )
            }
        }
    }

    override fun startProximityDiscovery(localPlayer: PartyPlayer) {
        localPlayerCache = localPlayer
        val advertiseName = "${localPlayer.name}|${localPlayer.avatarEmoji}|${localPlayer.id}"
        val roomCode = "BP-${(1000..9999).random()}"

        _state.update {
            it.copy(
                health = RoomConnectionHealth.SCANNING,
                roomCode = it.roomCode.ifEmpty { roomCode },
                statusLog = "Nearby 蓝牙/Wi-Fi 靠近雷达已启动，请将手机贴近好友..."
            )
        }

        val advOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()
        client.startAdvertising(advertiseName, SERVICE_ID, lifecycleCallback, advOptions)
            .addOnFailureListener { err ->
                Log.w(TAG, "Advertising failed: ${err.message}")
                _state.update {
                    it.copy(statusLog = "Nearby 广播受限（${err.localizedMessage ?: "需真机蓝牙/Wi-Fi"}），可随时切换单机AI模拟体验")
                }
            }

        val discOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, discOptions)
            .addOnFailureListener { err ->
                Log.w(TAG, "Discovery failed: ${err.message}")
            }
    }

    override fun stopProximityDiscovery() {
        runCatching {
            client.stopAdvertising()
            client.stopDiscovery()
        }
        _state.update { snap ->
            snap.copy(
                health = if (connectedEndpoints.isEmpty()) RoomConnectionHealth.IDLE else RoomConnectionHealth.CONNECTED,
                statusLog = if (connectedEndpoints.isEmpty()) "雷达已暂停" else "房间保持低延迟直连中"
            )
        }
    }

    override fun broadcastPacket(packet: SyncPacket) {
        val bytes = packetAdapter.toJson(packet).toByteArray(Charsets.UTF_8)
        val payload = Payload.fromBytes(bytes)
        val targets = connectedEndpoints.keys.toList()
        if (targets.isNotEmpty()) {
            client.sendPayload(targets, payload)
        }
    }

    override fun disconnectAll() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pendingReconnectEndpoints.clear()
        connectedEndpoints.clear()
        runCatching {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints()
        }
        _state.value = TransportSnapshot()
    }

    private fun sendToEndpoint(endpointId: String, packet: SyncPacket) {
        val bytes = packetAdapter.toJson(packet).toByteArray(Charsets.UTF_8)
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    private fun startHeartbeatLoop() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive && connectedEndpoints.isNotEmpty()) {
                val ping = SyncPacket(
                    type = "PING",
                    senderId = localPlayerCache?.id ?: "local",
                    seq = seqCounter.incrementAndGet(),
                    timestampMs = System.currentTimeMillis()
                )
                broadcastPacket(ping)
                delay(1500L)
            }
        }
    }

    private fun scheduleAutoReconnect(endpointId: String) {
        scope.launch {
            val backoffs = listOf(800L, 1600L, 3000L)
            for (waitMs in backoffs) {
                if (!pendingReconnectEndpoints.containsKey(endpointId)) return@launch
                delay(waitMs)
                val me = localPlayerCache ?: return@launch
                val tag = "${me.name}|${me.avatarEmoji}|${me.id}"
                runCatching {
                    client.requestConnection(tag, endpointId, lifecycleCallback)
                }
            }
            if (pendingReconnectEndpoints.remove(endpointId) != null) {
                connectedEndpoints.remove(endpointId)
                syncPlayersToState()
                _state.update {
                    it.copy(
                        health = if (connectedEndpoints.isEmpty()) RoomConnectionHealth.SCANNING else RoomConnectionHealth.CONNECTED,
                        statusLog = "部分设备离线，其余房间状态已由本地快照保护"
                    )
                }
            }
        }
    }

    private fun syncPlayersToState() {
        val remotes = connectedEndpoints.values.toList()
        val avgRtt = if (remotes.isEmpty()) 0 else remotes.map { it.rttMs }.average().toInt()
        _state.update {
            it.copy(
                remotePlayers = remotes,
                averageRttMs = avgRtt
            )
        }
    }
}
