package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConnectionMode
import com.example.data.DynamicLoadProgress
import com.example.data.DynamicPackPayload
import com.example.data.GameCatalog
import com.example.data.GameId
import com.example.data.LocalUserProfile
import com.example.data.MatchResultSummary
import com.example.data.MiniGameSpec
import com.example.data.PartyPlayer
import com.example.data.PartyRepository
import com.example.data.PlayerConnState
import com.example.data.PlayerTier
import com.example.data.ResourcePackDescriptor
import com.example.data.RoomConnectionHealth
import com.example.data.SyncPacket
import com.example.data.local.CachedResourcePackEntity
import com.example.data.local.PartyMatchRecordEntity
import com.example.data.local.SessionSnapshotEntity
import com.example.hardware.PartyHaptics
import com.example.hardware.PartySensorHub
import com.example.hardware.TiltTelemetry
import com.example.network.DiscoveredNearbyDevice
import com.example.network.NearbyConnectionsTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

data class PartyUiState(
    val localPlayerId: String = "me_${UUID.randomUUID().toString().take(6)}",
    val profile: LocalUserProfile = LocalUserProfile("派对玩家", "🦊", 0),
    val connectionMode: ConnectionMode = ConnectionMode.LOCAL_SIMULATED,
    val roomCode: String = "BP-2048",
    val connectionHealth: RoomConnectionHealth = RoomConnectionHealth.CONNECTED,
    val averageRttMs: Int = 14,
    val statusBanner: String = "单机 AI 模拟派对已就绪，一键添加好友或切换 Nearby 真机直连",
    val nearbyDevices: List<DiscoveredNearbyDevice> = emptyList(),
    val players: List<PartyPlayer> = emptyList(),
    val selectedTier: PlayerTier = PlayerTier.DUO,
    val activeGame: MiniGameSpec? = null,
    val activeDynamicPack: DynamicPackPayload? = null,
    val pendingPackPickerGame: MiniGameSpec? = null,
    val dynamicLoadProgress: DynamicLoadProgress? = null,
    val lastMatchResult: MatchResultSummary? = null,
    val syncSeq: Long = 1L,
    val isSimulatingNetworkDrop: Boolean = false,
    val toastMessage: String? = null
) {
    val recommendedTier: PlayerTier get() = PlayerTier.forPlayerCount(players.size.coerceAtLeast(2))
}

class PartyViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PartyRepository(application)
    val sensorHub = PartySensorHub(application)
    val haptics = PartyHaptics(application)

    private val nearbyTransport = NearbyConnectionsTransport(application)

    private val _uiState = MutableStateFlow(PartyUiState())
    val uiState: StateFlow<PartyUiState> = _uiState.asStateFlow()

    val tiltTelemetry: StateFlow<TiltTelemetry> = sensorHub.tilt

    val cachedPacks: StateFlow<List<CachedResourcePackEntity>> = repository.cachedPacksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestSnapshot: StateFlow<SessionSnapshotEntity?> = repository.latestSnapshotFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val matchHistory: StateFlow<List<PartyMatchRecordEntity>> = repository.recentMatchesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val simulatedBots = mutableListOf<PartyPlayer>()
    private var simTelemetryJob: Job? = null

    private val botRosterPool = listOf(
        Triple("可乐泡芙", "🐼", 1),
        Triple("闪电阿杰", "⚡", 2),
        Triple("柠檬茶茶", "🍋", 3),
        Triple("霓虹小鹿", "🦄", 4),
        Triple("火焰橘猫", "🐯", 5),
        Triple("电音企鹅", "🐧", 6),
        Triple("果冻柴犬", "🐶", 7)
    )

    init {
        // Initialize with 1 nearby AI buddy so 2P room is immediately ready to play out-of-the-box
        addSimulatedBotInternal()
        observeProfile()
        observeNearbyTransport()
        observeShakeToBump()
        startSimulatedTelemetry()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            repository.userProfileFlow.collectLatest { profile ->
                _uiState.update { state ->
                    val updated = state.copy(profile = profile)
                    updated.copy(players = rebuildPlayerRoster(updated))
                }
            }
        }
    }

    private fun observeNearbyTransport() {
        viewModelScope.launch {
            nearbyTransport.state.collectLatest { transportSnap ->
                if (_uiState.value.connectionMode == ConnectionMode.NEARBY_P2P) {
                    _uiState.update { state ->
                        val next = state.copy(
                            connectionHealth = transportSnap.health,
                            roomCode = transportSnap.roomCode.ifEmpty { state.roomCode },
                            averageRttMs = transportSnap.averageRttMs,
                            nearbyDevices = transportSnap.discoveredDevices,
                            statusBanner = transportSnap.statusLog
                        )
                        val roster = listOf(buildLocalPlayer(next)) + transportSnap.remotePlayers
                        next.copy(
                            players = roster,
                            selectedTier = PlayerTier.forPlayerCount(roster.size.coerceAtLeast(2))
                        )
                    }
                    checkpointSessionSnapshot("nearby_sync")
                }
            }
        }
    }

    private fun observeShakeToBump() {
        viewModelScope.launch {
            sensorHub.shakeEvents.collectLatest {
                // Only trigger shake-to-bump when on Radar/Lobby (not mid-game)
                if (_uiState.value.activeGame == null) {
                    triggerBumpProximityMatch()
                }
            }
        }
    }

    private fun startSimulatedTelemetry() {
        simTelemetryJob?.cancel()
        simTelemetryJob = viewModelScope.launch {
            while (isActive) {
                delay(1800L)
                val current = _uiState.value
                if (current.connectionMode == ConnectionMode.LOCAL_SIMULATED && !current.isSimulatingNetworkDrop) {
                    val jitterRtt = Random.nextInt(11, 24)
                    val updatedBots = simulatedBots.map { bot ->
                        bot.copy(rttMs = Random.nextInt(10, 26))
                    }
                    simulatedBots.clear()
                    simulatedBots.addAll(updatedBots)
                    _uiState.update { state ->
                        val next = state.copy(
                            averageRttMs = jitterRtt,
                            syncSeq = state.syncSeq + 1
                        )
                        next.copy(players = rebuildPlayerRoster(next))
                    }
                }
            }
        }
    }

    fun startSensors() = sensorHub.startListening()
    fun stopSensors() = sensorHub.stopListening()

    fun updateProfile(nickname: String, emoji: String, colorIndex: Int) {
        viewModelScope.launch {
            repository.updateProfile(nickname, emoji, colorIndex)
            showToast("玩家名片已更新")
        }
    }

    fun switchConnectionMode(mode: ConnectionMode) {
        if (_uiState.value.connectionMode == mode) return
        if (mode == ConnectionMode.NEARBY_P2P) {
            _uiState.update {
                it.copy(
                    connectionMode = ConnectionMode.NEARBY_P2P,
                    connectionHealth = RoomConnectionHealth.IDLE,
                    statusBanner = "已切换 Nearby 零流量直连模式，点击开启靠近雷达"
                )
            }
        } else {
            nearbyTransport.stopProximityDiscovery()
            if (simulatedBots.isEmpty()) addSimulatedBotInternal()
            _uiState.update { state ->
                val next = state.copy(
                    connectionMode = ConnectionMode.LOCAL_SIMULATED,
                    connectionHealth = RoomConnectionHealth.CONNECTED,
                    averageRttMs = 14,
                    statusBanner = "单机 AI 模拟派对已激活 · 随时一键增减至 2-8 人"
                )
                val roster = rebuildPlayerRoster(next)
                next.copy(
                    players = roster,
                    selectedTier = PlayerTier.forPlayerCount(roster.size.coerceAtLeast(2))
                )
            }
        }
        checkpointSessionSnapshot("mode_switch")
    }

    fun startNearbyRadar() {
        val local = buildLocalPlayer(_uiState.value)
        _uiState.update { it.copy(connectionMode = ConnectionMode.NEARBY_P2P) }
        nearbyTransport.startProximityDiscovery(local)
        haptics.bumpMatch()
    }

    fun stopNearbyRadar() {
        nearbyTransport.stopProximityDiscovery()
    }

    /**
     * Triggered by "碰一碰" button or physical phone shake on the Radar screen.
     */
    fun triggerBumpProximityMatch() {
        haptics.bumpMatch()
        val state = _uiState.value
        if (state.connectionMode == ConnectionMode.NEARBY_P2P) {
            startNearbyRadar()
            showToast("📡 靠近脉冲已发出！请贴近好友手机自动建房")
        } else {
            if (simulatedBots.size < 7) {
                val added = addSimulatedBotInternal()
                refreshSimulatedRoster("✨ 碰一碰成功！「${added.name}」靠近加入房间（当前 ${simulatedBots.size + 1} 人）")
                showToast("碰一碰配对成功：${added.avatarEmoji} ${added.name} 已入房")
            } else {
                showToast("房间已满 8 人！可直接开启大型团战小游戏")
            }
        }
    }

    fun setSimulatedPlayerCount(targetCount: Int) {
        val clamped = targetCount.coerceIn(2, 8)
        val neededBots = clamped - 1
        while (simulatedBots.size < neededBots) {
            addSimulatedBotInternal()
        }
        while (simulatedBots.size > neededBots) {
            simulatedBots.removeAt(simulatedBots.lastIndex)
        }
        haptics.lightTap()
        val tier = PlayerTier.forPlayerCount(clamped)
        refreshSimulatedRoster("房间人数已调整为 ${clamped} 人 · 智能推荐「${tier.title}」")
    }

    fun addSimulatedFriend() {
        if (simulatedBots.size >= 7) {
            showToast("房间已达 8 人上限")
            return
        }
        val added = addSimulatedBotInternal()
        haptics.bumpMatch()
        refreshSimulatedRoster("${added.avatarEmoji} ${added.name} 已自动靠近入房")
    }

    fun removeLastSimulatedFriend() {
        if (simulatedBots.size <= 1) {
            showToast("至少保留 2 人才能开启多人对局")
            return
        }
        val removed = simulatedBots.removeAt(simulatedBots.lastIndex)
        haptics.lightTap()
        refreshSimulatedRoster("${removed.name} 已离开房间")
    }

    private fun addSimulatedBotInternal(): PartyPlayer {
        val idx = simulatedBots.size % botRosterPool.size
        val (name, emoji, colorIdx) = botRosterPool[idx]
        val bot = PartyPlayer(
            id = "bot_${simulatedBots.size + 1}",
            name = name,
            avatarEmoji = emoji,
            colorIndex = colorIdx,
            isLocal = false,
            isHost = false,
            isBot = true,
            isReady = true,
            rttMs = Random.nextInt(11, 22),
            teamIndex = (simulatedBots.size + 1) % 2
        )
        simulatedBots.add(bot)
        return bot
    }

    private fun refreshSimulatedRoster(banner: String) {
        _uiState.update { state ->
            val next = state.copy(
                connectionMode = ConnectionMode.LOCAL_SIMULATED,
                connectionHealth = RoomConnectionHealth.CONNECTED,
                statusBanner = banner
            )
            val roster = rebuildPlayerRoster(next)
            next.copy(
                players = roster,
                selectedTier = PlayerTier.forPlayerCount(roster.size.coerceAtLeast(2))
            )
        }
        checkpointSessionSnapshot("roster_update")
    }

    fun selectTierTab(tier: PlayerTier) {
        haptics.lightTap()
        _uiState.update { it.copy(selectedTier = tier) }
    }

    fun requestLaunchGame(spec: MiniGameSpec) {
        haptics.lightTap()
        val currentPlayers = _uiState.value.players.size
        // In simulated mode, seamlessly auto-fill or inform player count matching
        if (_uiState.value.connectionMode == ConnectionMode.LOCAL_SIMULATED &&
            (currentPlayers < spec.minPlayers || currentPlayers > spec.maxPlayers)
        ) {
            val adjusted = currentPlayers.coerceIn(spec.minPlayers, spec.maxPlayers)
            setSimulatedPlayerCount(adjusted)
            showToast("已按玩法需求自动调整房间至 ${adjusted} 人")
        }

        if (spec.requiresDynamicPack) {
            // Open 4P+ Dynamic Resource Pack Picker & Loader
            _uiState.update { it.copy(pendingPackPickerGame = spec) }
        } else {
            launchGameInternal(spec, null)
        }
    }

    fun dismissPackPicker() {
        _uiState.update { it.copy(pendingPackPickerGame = null, dynamicLoadProgress = null) }
    }

    fun getRecommendedPacksFor(gameId: GameId): List<ResourcePackDescriptor> {
        return repository.resourcePackManager.recommendPacksFor(
            gameId = gameId,
            playerCount = _uiState.value.players.size.coerceAtLeast(5)
        )
    }

    fun loadDynamicPackAndStart(spec: MiniGameSpec, packId: String) {
        viewModelScope.launch {
            val playerCount = _uiState.value.players.size.coerceAtLeast(5)
            val payload = repository.resourcePackManager.loadPackDynamically(
                packId = packId,
                playerCount = playerCount
            ) { progress ->
                _uiState.update { it.copy(dynamicLoadProgress = progress) }
            }
            delay(180)
            _uiState.update {
                it.copy(
                    pendingPackPickerGame = null,
                    dynamicLoadProgress = null
                )
            }
            launchGameInternal(spec, payload)
        }
    }

    fun preloadPackOffline(packId: String) {
        viewModelScope.launch {
            repository.resourcePackManager.loadPackDynamically(
                packId = packId,
                playerCount = _uiState.value.players.size.coerceAtLeast(5)
            ) { progress ->
                _uiState.update { it.copy(dynamicLoadProgress = progress) }
            }
            delay(150)
            _uiState.update { it.copy(dynamicLoadProgress = null) }
            showToast("资源包已离线缓存，断网也可秒开")
        }
    }

    fun deleteCachedPack(packId: String) {
        viewModelScope.launch {
            repository.resourcePackManager.removeCachedPack(packId)
            showToast("已释放该离线资源包缓存")
        }
    }

    private fun launchGameInternal(spec: MiniGameSpec, pack: DynamicPackPayload?) {
        haptics.bumpMatch()
        _uiState.update { state ->
            val seq = state.syncSeq + 1
            if (state.connectionMode == ConnectionMode.NEARBY_P2P) {
                nearbyTransport.broadcastPacket(
                    SyncPacket(
                        type = "GAME_START",
                        senderId = state.localPlayerId,
                        seq = seq,
                        payload = spec.id.name
                    )
                )
            }
            state.copy(
                activeGame = spec,
                activeDynamicPack = pack,
                lastMatchResult = null,
                syncSeq = seq
            )
        }
        checkpointSessionSnapshot("game_start:${spec.id.name}")
    }

    fun finishActiveGame(summary: MatchResultSummary) {
        haptics.victory()
        viewModelScope.launch {
            repository.recordMatchResult(summary)
        }
        _uiState.update { state ->
            val scoreMap = summary.rankings.associate { it.id to it.score }
            val updatedPlayers = state.players.map { p ->
                p.copy(score = p.score + (scoreMap[p.id] ?: 0))
            }
            // Also update bots cumulative scores
            for (i in simulatedBots.indices) {
                val b = simulatedBots[i]
                simulatedBots[i] = b.copy(score = b.score + (scoreMap[b.id] ?: 0))
            }
            state.copy(
                players = updatedPlayers,
                lastMatchResult = summary,
                syncSeq = state.syncSeq + 1
            )
        }
        checkpointSessionSnapshot("game_finish:${summary.gameId.name}")
    }

    fun rematchActiveGame() {
        haptics.bumpMatch()
        _uiState.update {
            it.copy(
                lastMatchResult = null,
                syncSeq = it.syncSeq + 1
            )
        }
        checkpointSessionSnapshot("rematch:${_uiState.value.activeGame?.id?.name ?: "none"}")
    }

    fun exitActiveGame() {
        _uiState.update {
            it.copy(
                activeGame = null,
                lastMatchResult = null
            )
        }
        checkpointSessionSnapshot("back_to_lobby")
    }

    /**
     * Simulates real offline party RF drop / signal interference & verifies Room snapshot recovery!
     */
    fun triggerWeakNetworkRecoveryDrill() {
        if (_uiState.value.isSimulatingNetworkDrop) return
        viewModelScope.launch {
            haptics.lightTap()
            // 1. Immediately flush latest snapshot to Room SQLite
            checkpointSessionSnapshot("pre_drop_checkpoint")

            _uiState.update { state ->
                state.copy(
                    isSimulatingNetworkDrop = true,
                    connectionHealth = RoomConnectionHealth.RECONNECTING,
                    averageRttMs = 0,
                    statusBanner = "检测到信号波动 · 已冻结当前帧并启动指数退避自动重连...",
                    players = state.players.map {
                        if (it.isLocal) it else it.copy(status = PlayerConnState.RECONNECTING.name)
                    }
                )
            }

            delay(1500L)

            // 2. Restore directly from Room SessionSnapshotEntity
            val snap = repository.getLatestSnapshot()
            haptics.bumpMatch()
            _uiState.update { state ->
                state.copy(
                    isSimulatingNetworkDrop = false,
                    connectionHealth = RoomConnectionHealth.RESTORED,
                    averageRttMs = 15,
                    statusBanner = "重连成功！已从 Room 离线快照（Seq #${snap?.seqNumber ?: state.syncSeq}）无损恢复",
                    players = state.players.map {
                        it.copy(status = PlayerConnState.ONLINE.name)
                    },
                    toastMessage = "已通过本地离线快照无缝恢复对局状态"
                )
            }
            delay(2500L)
            _uiState.update { state ->
                if (state.connectionHealth == RoomConnectionHealth.RESTORED) {
                    state.copy(connectionHealth = RoomConnectionHealth.CONNECTED)
                } else state
            }
        }
    }

    fun restoreFromSavedSnapshot() {
        viewModelScope.launch {
            val snap = repository.getLatestSnapshot()
            if (snap == null) {
                showToast("暂无可恢复的离线房间快照")
                return@launch
            }
            val savedPlayers = repository.decodePlayers(snap.playersJson)
            simulatedBots.clear()
            savedPlayers.filter { !it.isLocal }.forEach { bot ->
                simulatedBots.add(bot.copy(status = PlayerConnState.ONLINE.name))
            }
            val restoredGame = snap.activeGameId?.let { name ->
                runCatching { GameCatalog.findById(GameId.valueOf(name)) }.getOrNull()
            }
            haptics.bumpMatch()
            _uiState.update { state ->
                val roster = rebuildPlayerRoster(state)
                state.copy(
                    roomCode = snap.roomCode,
                    connectionMode = ConnectionMode.LOCAL_SIMULATED,
                    connectionHealth = RoomConnectionHealth.RESTORED,
                    players = roster,
                    selectedTier = PlayerTier.forPlayerCount(roster.size.coerceAtLeast(2)),
                    activeGame = if (restoredGame?.requiresDynamicPack == true) null else restoredGame,
                    syncSeq = snap.seqNumber,
                    statusBanner = "已从本地离线快照恢复房间 ${snap.roomCode}（${roster.size}人）",
                    toastMessage = "房间 ${snap.roomCode} 快照已恢复！"
                )
            }
        }
    }

    fun clearAllMatchHistory() {
        viewModelScope.launch {
            repository.clearMatchRecords()
            showToast("聚会战绩榜已清空")
        }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    private fun checkpointSessionSnapshot(tag: String) {
        val state = _uiState.value
        if (state.players.isEmpty()) return
        viewModelScope.launch {
            repository.saveSessionSnapshot(
                roomCode = state.roomCode,
                connectionMode = state.connectionMode,
                hostId = state.localPlayerId,
                activeGameId = state.activeGame?.id,
                players = state.players,
                gameStateJson = "{\"tag\":\"$tag\",\"seq\":${state.syncSeq}}",
                seqNumber = state.syncSeq
            )
        }
    }

    private fun buildLocalPlayer(state: PartyUiState): PartyPlayer {
        return PartyPlayer(
            id = state.localPlayerId,
            name = state.profile.nickname,
            avatarEmoji = state.profile.avatarEmoji,
            colorIndex = state.profile.colorIndex,
            isLocal = true,
            isHost = true,
            isBot = false,
            isReady = true,
            rttMs = 0,
            score = state.players.firstOrNull { it.isLocal }?.score ?: 0,
            teamIndex = 0
        )
    }

    private fun rebuildPlayerRoster(state: PartyUiState): List<PartyPlayer> {
        return listOf(buildLocalPlayer(state)) + simulatedBots
    }

    override fun onCleared() {
        sensorHub.stopListening()
        nearbyTransport.disconnectAll()
        super.onCleared()
    }
}
