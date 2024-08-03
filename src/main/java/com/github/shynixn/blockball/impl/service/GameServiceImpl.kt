package com.github.shynixn.blockball.impl.service

import com.github.shynixn.blockball.contract.*
import com.github.shynixn.blockball.entity.*
import com.github.shynixn.blockball.enumeration.GameType
import com.github.shynixn.blockball.impl.SoccerHubGameImpl
import com.github.shynixn.blockball.impl.SoccerMiniGameImpl
import com.github.shynixn.blockball.impl.exception.SoccerGameException
import com.github.shynixn.mcutils.common.ConfigurationService
import com.github.shynixn.mcutils.common.chat.ChatMessageService
import com.github.shynixn.mcutils.common.command.CommandService
import com.github.shynixn.mcutils.common.repository.Repository
import com.github.shynixn.mcutils.common.sound.SoundService
import com.github.shynixn.mcutils.common.toVector3d
import com.github.shynixn.mcutils.database.api.PlayerDataRepository
import com.github.shynixn.mcutils.packet.api.PacketService
import com.github.shynixn.mcutils.sign.SignService
import com.google.inject.Inject
import kotlinx.coroutines.runBlocking
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.*
import java.util.logging.Level

class GameServiceImpl @Inject constructor(
    private val arenaRepository: Repository<SoccerArena>,
    private val configurationService: ConfigurationService,
    private val plugin: Plugin,
    private val playerDataRepository: PlayerDataRepository<PlayerInformation>,
    private val placeHolderService: PlaceHolderService,
    private val bossBarService: BossBarService,
    private val chatMessageService: ChatMessageService,
    private val soundService: SoundService,
    private val packetService: PacketService,
    private val scoreboardService: ScoreboardService,
    private val commandService: CommandService,
    private val soccerBallFactory: SoccerBallFactory,
    private val language: BlockBallLanguage,
    private val signService: SignService
) : GameService, Runnable {
    private val games = ArrayList<SoccerGame>()
    private var ticks: Int = 0

    /**
     * Init.
     */
    init {
        plugin.server.scheduler.runTaskTimer(
            plugin, Runnable { this.run() }, 0L, 1L
        )
    }

    /**
     * Reloads all games.
     */
    override suspend fun reloadAll() {
        close()

        val arenas = arenaRepository.getAll()

        for (arena in arenas) {
            reload(arena)
        }
    }

    /**
     * Reloads the specific game.
     */
    override suspend fun reload(arena: SoccerArena) {
        // A game with the same arena name is currently running. Stop it and reboot it.
        val existingGame = getByName(arena.name)

        if (existingGame != null) {
            existingGame.close()
            games.remove(existingGame)
            plugin.logger.log(Level.INFO, "Stopped game '" + arena.name + "'.")
        }

        // Enable signs, if they are already added, the call does nothing.
        for (sign in arena.meta.getAllSigns()) {
            sign.tag = arena.name
            signService.addSign(sign)
        }

        if (arena.enabled) {
            validateGame(arena)

            val game: SoccerGame = when (arena.gameType) {
                GameType.HUBGAME -> SoccerHubGameImpl(
                    arena,
                    playerDataRepository,
                    plugin,
                    placeHolderService,
                    bossBarService,
                    language,
                    packetService,
                    scoreboardService,
                    soccerBallFactory,
                    chatMessageService,
                    commandService
                )

                GameType.MINIGAME -> SoccerMiniGameImpl(
                    arena,
                    playerDataRepository,
                    plugin,
                    placeHolderService,
                    bossBarService,
                    chatMessageService,
                    configurationService,
                    soundService,
                    language,
                    packetService,
                    scoreboardService,
                    commandService,
                    soccerBallFactory
                )

                else -> throw RuntimeException("GameType ${arena.gameType} not supported!")
            }

            games.add(game)
            plugin.logger.log(Level.INFO, "Game '" + arena.name + "' is ready.")
        } else {
            plugin.logger.log(Level.INFO, "Cannot boot game '" + arena.name + "' because it is not enabled.")
        }
    }

    /**
     * When an object implementing interface `Runnable` is used
     * to create a thread, starting the thread causes the object's
     * `run` method to be called in that separately executing
     * thread.
     *
     *
     * The general contract of the method `run` is that it may
     * take any action whatsoever.
     *
     * @see java.lang.Thread.run
     */
    override fun run() {
        games.toTypedArray().forEach { game ->
            if (game.closed) {
                runBlocking {
                    reload(game.arena)
                }
            } else {
                game.handle(ticks)
            }
        }

        if (ticks >= 20) {
            ticks = 0
        }

        ticks++
    }

    /**
     * Returns all currently loaded games on the server.
     */
    override fun getAll(): List<SoccerGame> {
        return games
    }

    /**
     * Tries to locate a game this player is playing.
     */
    override fun getByPlayer(player: Player): SoccerGame? {
        for (game in games) {
            if (game.ingamePlayersStorage.containsKey(player)) {
                return game
            }
        }

        return null
    }

    /**
     * Tries to locate a game of the given name.
     */
    override fun getByName(name: String): SoccerGame? {
        for (game in games) {
            if (game.arena.name.equals(name, true)) {
                return game
            }
        }

        return null
    }

    /**
     * Closes all games permanently and should be executed on server shutdown.
     */
    override fun close() {
        for (game in this.games) {
            try {
                game.close()
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Failed to dispose game.", e)
            }
        }

        games.clear()
    }


    private fun validateGame(arena: SoccerArena) {
        if (arena.meta.ballMeta.spawnpoint == null) {
            arena.enabled = false
            throw SoccerGameException(arena, "Set the leave spawnpoint values in arena ${arena.name}!")
        }
    }
}
