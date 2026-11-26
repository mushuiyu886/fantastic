package com.example.network

import com.example.data.PartyPlayer
import com.example.data.RoomConnectionHealth
import com.example.data.SyncPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class DiscoveredNearbyDevice(
    val endpointId: String,
    val displayName: String,
    val isConnected: Boolean = false,
    val signalQuality: String = "极近 · <1m"
)

data class TransportSnapshot(
    val health: RoomConnectionHealth = RoomConnectionHealth.IDLE,
    val roomCode: String = "",
    val isHost: Boolean = true,
    val averageRttMs: Int = 0,
    val discoveredDevices: List<DiscoveredNearbyDevice> = emptyList(),
    val remotePlayers: List<PartyPlayer> = emptyList(),
    val statusLog: String = "等待开启靠近雷达"
)

/**
 * Unified P2P transport abstraction supporting:
 * 1) Real Google Nearby Connections (Bluetooth + Wi-Fi Direct, zero internet)
 * 2) Local low-latency AI Bot Simulator for instant single-device testing (2P–8P)
 */
interface PartyTransport {
    val state: StateFlow<TransportSnapshot>
    val incomingPackets: SharedFlow<SyncPacket>

    fun startProximityDiscovery(localPlayer: PartyPlayer)
    fun stopProximityDiscovery()
    fun broadcastPacket(packet: SyncPacket)
    fun disconnectAll()
}
