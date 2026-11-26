package com.example.data

import com.squareup.moshi.JsonClass

enum class PlayerTier(
    val title: String,
    val badge: String,
    val subtitle: String,
    val minPlayers: Int,
    val maxPlayers: Int
) {
    DUO("双人精操", "2人", "极限操作与实时物理对抗", 2, 2),
    TRIO("三人混战", "3人", "体感甩锅与三角博弈混战", 3, 3),
    QUAD("四人协作", "4人", "2v2体感协同与极速反应战", 4, 4),
    PARTY_PLUS("4人+团战", "5-8人", "动态资源加载·团队协作狂欢", 5, 8);

    companion object {
        fun forPlayerCount(count: Int): PlayerTier = when {
            count <= 2 -> DUO
            count == 3 -> TRIO
            count == 4 -> QUAD
            else -> PARTY_PLUS
        }
    }
}

enum class GameId {
    AIR_HOCKEY,
    REFLEX_DUEL,
    HOT_POTATO,
    TURF_SPRINT,
    TILT_BALANCE,
    QUAD_CLASH,
    UNDERCOVER_SPY,
    TEAM_HEADS_UP
}

data class MiniGameSpec(
    val id: GameId,
    val title: String,
    val subtitle: String,
    val tier: PlayerTier,
    val minPlayers: Int,
    val maxPlayers: Int,
    val mechanicsTag: String,
    val interactionHint: String,
    val emoji: String,
    val requiresDynamicPack: Boolean = false
)

object GameCatalog {
    val allGames: List<MiniGameSpec> = listOf(
        // 2P High-Skill Control
        MiniGameSpec(
            id = GameId.AIR_HOCKEY,
            title = "极速霓虹弹球",
            subtitle = "60fps实时物理对战，拖拽击球器并释放冲刺暴扣，率先拿5分胜出！",
            tier = PlayerTier.DUO,
            minPlayers = 2,
            maxPlayers = 2,
            mechanicsTag = "高操作·物理微操",
            interactionHint = "双人桌面对决 · 触控+震动碰撞",
            emoji = "🏒"
        ),
        MiniGameSpec(
            id = GameId.REFLEX_DUEL,
            title = "微操节拍对决",
            subtitle = "真假信号闪现瞬间出击！支持机身倾斜举盾防反，精准度决定生死。",
            tier = PlayerTier.DUO,
            minPlayers = 2,
            maxPlayers = 2,
            mechanicsTag = "高操作·毫秒反制",
            interactionHint = "倾斜举盾 + 极速抢拍反制",
            emoji = "⚡"
        ),
        // 3P Chaos & Mind Games
        MiniGameSpec(
            id = GameId.HOT_POTATO,
            title = "热土豆传炸弹",
            subtitle = "心跳震动越跳越快！摇晃手机或左右划屏把炸弹甩给身边好友，看谁最后引爆！",
            tier = PlayerTier.TRIO,
            minPlayers = 3,
            maxPlayers = 3,
            mechanicsTag = "线下体感·甩锅博弈",
            interactionHint = "摇晃手机 / 左右划屏传给邻座",
            emoji = "💣"
        ),
        MiniGameSpec(
            id = GameId.TURF_SPRINT,
            title = "三色领地冲刺",
            subtitle = "三人实时抢占脉冲多巴胺格子，点击连击涂色并拦截对手高分能量块。",
            tier = PlayerTier.TRIO,
            minPlayers = 3,
            maxPlayers = 3,
            mechanicsTag = "三人混战·高频同步",
            interactionHint = "实时三方占格 + 冲刺偷分",
            emoji = "🎨"
        ),
        // 4P 2v2 Co-op & Competitive
        MiniGameSpec(
            id = GameId.TILT_BALANCE,
            title = "2v2 体感天平",
            subtitle = "红蓝双人组队！队友同时倾斜手机共同稳住轨道能量球，喊出默契口令冲刺终点。",
            tier = PlayerTier.QUAD,
            minPlayers = 4,
            maxPlayers = 4,
            mechanicsTag = "2v2协同·陀螺仪体感",
            interactionHint = "线下口令协作 · 双机同步倾斜",
            emoji = "⚖️"
        ),
        MiniGameSpec(
            id = GameId.QUAD_CLASH,
            title = "四方撞色抢拍",
            subtitle = "文字与颜色错位干扰！4人同屏极速抢答，连击触发全员冰冻干扰。",
            tier = PlayerTier.QUAD,
            minPlayers = 4,
            maxPlayers = 4,
            mechanicsTag = "四人混战·色词干扰",
            interactionHint = "高燃连击 + 干扰对手冰冻",
            emoji = "🌈"
        ),
        // 4P+ (5-8P) Team Collaboration + Dynamic Resource Pack Loading
        MiniGameSpec(
            id = GameId.UNDERCOVER_SPY,
            title = "派对卧底大侦探",
            subtitle = "按5-8人动态配比平民/卧底/白板并按需加载词库包，倾斜防偷看+面对面发言投票！",
            tier = PlayerTier.PARTY_PLUS,
            minPlayers = 5,
            maxPlayers = 8,
            mechanicsTag = "动态词库·面对面推理",
            interactionHint = "长按看词·倾斜隐藏·实时投票",
            emoji = "🕵️",
            requiresDynamicPack = true
        ),
        MiniGameSpec(
            id = GameId.TEAM_HEADS_UP,
            title = "疯狂比划接力",
            subtitle = "红蓝两队轮番上阵！手机朝向队友，前倾“答对”后仰“跳过”，全员实时同步比分。",
            tier = PlayerTier.PARTY_PLUS,
            minPlayers = 5,
            maxPlayers = 8,
            mechanicsTag = "动态题包·团队体感接力",
            interactionHint = "手机朝外 · 前倾得分 / 后仰跳过",
            emoji = "🎭",
            requiresDynamicPack = true
        )
    )

    fun gamesForTier(tier: PlayerTier): List<MiniGameSpec> = allGames.filter { it.tier == tier }
    fun findById(id: GameId): MiniGameSpec = allGames.first { it.id == id }
}

enum class ConnectionMode {
    NEARBY_P2P,
    LOCAL_SIMULATED
}

enum class RoomConnectionHealth {
    IDLE,
    SCANNING,
    CONNECTED,
    RECONNECTING,
    RESTORED
}

enum class PlayerConnState {
    ONLINE,
    RECONNECTING
}

@JsonClass(generateAdapter = true)
data class PartyPlayer(
    val id: String,
    val name: String,
    val avatarEmoji: String,
    val colorIndex: Int,
    val isLocal: Boolean = false,
    val isHost: Boolean = false,
    val isBot: Boolean = false,
    val isReady: Boolean = true,
    val rttMs: Int = 14,
    val status: String = PlayerConnState.ONLINE.name,
    val score: Int = 0,
    val teamIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class SyncPacket(
    val type: String, // JOIN, READY, PING, PONG, GAME_START, INPUT, STATE, SNAPSHOT, GAME_OVER
    val senderId: String,
    val seq: Long,
    val timestampMs: Long = System.currentTimeMillis(),
    val payload: String = ""
)

data class MatchResultSummary(
    val gameId: GameId,
    val gameTitle: String,
    val playerCount: Int,
    val mvpName: String,
    val mvpEmoji: String,
    val mvpScore: Int,
    val localWon: Boolean,
    val rankings: List<PartyPlayer>,
    val note: String
)
