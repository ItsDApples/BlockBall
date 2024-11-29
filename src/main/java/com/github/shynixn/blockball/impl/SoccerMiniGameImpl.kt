package com.github.shynixn.blockball.impl

import com.github.shynixn.blockball.contract.*
import com.github.shynixn.blockball.entity.PlayerInformation
import com.github.shynixn.blockball.entity.SoccerArena
import com.github.shynixn.blockball.enumeration.GameState
import com.github.shynixn.blockball.enumeration.JoinResult
import com.github.shynixn.blockball.enumeration.Team
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.github.shynixn.mcutils.common.chat.ChatMessageService
import com.github.shynixn.mcutils.common.command.CommandService
import com.github.shynixn.mcutils.common.sound.SoundMeta
import com.github.shynixn.mcutils.common.sound.SoundService
import com.github.shynixn.mcutils.common.toLocation
import com.github.shynixn.mcutils.database.api.PlayerDataRepository
import com.github.shynixn.mcutils.packet.api.PacketService
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

open class SoccerMiniGameImpl constructor(
    arena: SoccerArena,
    playerDataRepository: PlayerDataRepository<PlayerInformation>,
    private val plugin: Plugin,
    private val placeHolderService: PlaceHolderService,
    private val bossBarService: BossBarService,
    private val chatMessageService: ChatMessageService,
    private val soundService: SoundService,
    language: Language,
    packetService: PacketService,
    scoreboardService: ScoreboardService,
    commandService: CommandService,
    soccerBallFactory: SoccerBallFactory
) : SoccerGameImpl(
    arena,
    placeHolderService,
    packetService,
    plugin,
    bossBarService,
    scoreboardService,
    soccerBallFactory,
    commandService,
    language,
    playerDataRepository
), SoccerMiniGame {

    /**
     * Is the lobby countdown active.
     */
    var lobbyCountDownActive: Boolean = false

    /**
     * Actual countdown.
     */
    var lobbyCountdown: Int = 20

    /**
     * Actual game coutndown.
     */
    override var gameCountdown: Int = 20

    /**
     * Index of the current match time.
     */
    override var matchTimeIndex: Int = 0

    /**
     * Returns the bling sound.
     */
    val blingSound: SoundMeta = SoundMeta().also {
        it.name = "BLOCK_NOTE_BLOCK_PLING,BLOCK_NOTE_PLING,NOTE_PLING"
        it.volume = 10.0
        it.pitch = 2.0
    }

    /**
     * Lets the given [player] leave join. Optional can the prefered
     * [team] be specified but the team can still change because of soccerArena settings.
     * Does nothing if the player is already in a Game.
     */
    override fun join(player: Player, team: Team?): JoinResult {
        if (playing) {
            return JoinResult.GAME_FULL
        }

        return super.join(player, team)
    }

    /**
     * Tick handle.
     */
    override fun handle(ticks: Int) {
        // Handle HubGame ticking.
        if (!arena.enabled || closing) {
            status = GameState.DISABLED
            close()
            if (ingamePlayersStorage.isNotEmpty()) {
                closing = true
            }
            return
        }

        if (status == GameState.DISABLED) {
            status = GameState.JOINABLE
        }

        if (Bukkit.getWorld(arena.meta.ballMeta.spawnpoint!!.world!!) == null) {
            return
        }

        if (arena.meta.hubLobbyMeta.resetArenaOnEmpty && ingamePlayersStorage.isEmpty() && (redScore > 0 || blueScore > 0)) {
            closing = true
        }

        if (ticks >= 20) {
            if (lobbyCountDownActive) {
                if (lobbyCountdown > 10) {
                    val amountPlayers = arena.meta.blueTeamMeta.maxAmount + arena.meta.redTeamMeta.maxAmount

                    if (ingamePlayersStorage.size >= amountPlayers) {
                        lobbyCountdown = 10
                    }
                }

                lobbyCountdown--

                ingamePlayersStorage.keys.toTypedArray().forEach { p ->
                    if (lobbyCountdown <= 10) {
                        p.exp = 1.0F - (lobbyCountdown.toFloat() / 10.0F)
                    }

                    p.level = lobbyCountdown
                }

                if (lobbyCountdown < 5) {
                    ingamePlayersStorage.keys.forEach { p ->
                        soundService.playSound(
                            p.location, arrayListOf(p), blingSound
                        )
                    }
                }

                if (lobbyCountdown <= 0) {
                    ingamePlayersStorage.keys.toTypedArray().forEach { p ->
                        if (lobbyCountdown <= 10) {
                            p.exp = 1.0F
                        }

                        p.level = 0
                    }

                    lobbyCountDownActive = false
                    playing = true
                    status = GameState.RUNNING
                    matchTimeIndex = -1
                    ballEnabled = true
                    switchToNextMatchTime()
                }
            }

            if (!lobbyCountDownActive) {
                if (canStartLobbyCountdown()) {
                    lobbyCountDownActive = true
                    lobbyCountdown = arena.meta.minigameMeta.lobbyDuration
                } else if (!playing) {
                    ingamePlayersStorage.keys.toTypedArray().forEach { p ->
                        chatMessageService.sendActionBarMessage(
                            p, placeHolderService.replacePlaceHolders(
                                arena.meta.minigameMeta.playersRequiredToStartMessage, p, this
                            )
                        )
                    }
                }
            }

            if (playing) {
                gameCountdown--

                ingamePlayersStorage.keys.toTypedArray().asSequence().forEach { p ->
                    if (gameCountdown <= 10) {
                        p.exp = gameCountdown.toFloat() / 10.0F
                    }

                    if (gameCountdown <= 5) {
                        soundService.playSound(
                            p.location, arrayListOf(p), blingSound
                        )
                    }

                    p.level = gameCountdown
                }

                if (gameCountdown <= 0) {
                    switchToNextMatchTime()
                }

                if (ingamePlayersStorage.isEmpty() || redTeam.size < arena.meta.redTeamMeta.minPlayingPlayers || blueTeam.size < arena.meta.blueTeamMeta.minPlayingPlayers) {
                    closing = true
                }
            }
        }

        // Handle SoccerBall.
        this.fixBallPositionSpawn()
        if (ticks >= 20) {
            this.handleBallSpawning()
        }

        // Update signs and protections.
        super.handleMiniGameEssentials(ticks)
    }

    /**
     * Closes the given game and all underlying resources.
     */
    override fun close() {
        if (closed) {
            return
        }
        status = GameState.DISABLED
        closed = true
        ingamePlayersStorage.keys.toTypedArray().forEach { p ->
            leave(p)
        }
        ingamePlayersStorage.clear()
        ball?.remove()
        doubleJumpCoolDownPlayers.clear()
        holograms.forEach { h -> h.remove() }
        holograms.clear()

        if (bossBar != null) {
            bossBarService.cleanResources(bossBar)
        }
    }

    /**
     * Actives the next match time. Closes the match if no match time is available.
     */
    override fun switchToNextMatchTime() {
        matchTimeIndex++

        val matchTimes = arena.meta.minigameMeta.matchTimes

        if (matchTimeIndex >= matchTimes.size) {
            closing = true
            return
        }

        val matchTime = matchTimes[matchTimeIndex]
        val isLastMatchTimeSwap = (matchTimeIndex + 1) >= matchTimes.size

        if (isLastMatchTimeSwap) {
            timeAlmostUp()
        }

        gameCountdown = matchTime.duration

        if (matchTime.isSwitchGoalsEnabled) {
            mirroredGoals = !mirroredGoals

            ingamePlayersStorage.values.forEach { e ->
                if (e.goalTeam != null) {
                    if (e.goalTeam == Team.RED) {
                        e.goalTeam = Team.BLUE
                    } else if (e.goalTeam == Team.BLUE) {
                        e.goalTeam = Team.RED
                    } else {
                        e.goalTeam = Team.REFEREE
                    }
                }
            }
        }

        ballEnabled = matchTime.playAbleBall

        if (!ballEnabled && ball != null && !ball!!.isDead) {
            ball!!.remove()
        }

        for (p in ingamePlayersStorage.keys) {
            if (matchTime.respawnEnabled || matchTimeIndex == 0) {
                respawn(p)
            }

            if (!matchTime.startMessageTitle.isBlank() || !matchTime.startMessageSubTitle.isBlank()) {
                val game = this
                plugin.launch {
                    delay(10.ticks)
                    chatMessageService.sendTitleMessage(
                        p,
                        placeHolderService.replacePlaceHolders(matchTime.startMessageTitle, null, game),
                        placeHolderService.replacePlaceHolders(matchTime.startMessageSubTitle, null, game),
                        matchTime.startMessageFadeIn,
                        matchTime.startMessageStay,
                        matchTime.startMessageFadeOut
                    )
                }
            }

            p.exp = 1.0F
        }
    }

    override fun setPlayerToArena(player: Player, team: Team) {
        val teamMeta = getTeamMetaFromTeam(team)
        player.teleport(teamMeta.lobbySpawnpoint!!.toLocation())
    }

    /**
     * Gets called when the game ends.
     */
    private fun timeAlmostUp() {
        when {
            redScore == blueScore -> {
                onMatchEnd(null)
                onDraw()
            }

            redScore > blueScore -> {
                onMatchEnd(Team.RED)
                onWin(Team.RED)
            }

            else -> {
                onMatchEnd(Team.BLUE)
                onWin(Team.BLUE)
            }
        }

        // OnWin sets game.closing to true.
        closing = false
    }

    /**
     * Returns if the lobby countdown can already be started.
     * @return canStart
     */
    private fun canStartLobbyCountdown(): Boolean {
        val amount = arena.meta.redTeamMeta.minAmount + arena.meta.blueTeamMeta.minAmount

        if (!playing && ingamePlayersStorage.size >= amount && ingamePlayersStorage.isNotEmpty()) {
            return true
        }

        return false
    }
}
