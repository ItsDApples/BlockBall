package unittest

import com.github.shynixn.blockball.api.business.service.ConfigurationService
import com.github.shynixn.blockball.api.business.service.PersistenceStatsService
import com.github.shynixn.blockball.api.business.service.ScoreboardService
import com.github.shynixn.blockball.api.business.service.StatsCollectingService
import com.github.shynixn.blockball.api.persistence.entity.Stats
import com.github.shynixn.blockball.bukkit.logic.business.service.StatsCollectingServiceImpl
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

/**
 * Created by Shynixn 2018.
 * <p>
 * Version 1.2
 * <p>
 * MIT License
 * <p>
 * Copyright (c) 2018 by Shynixn
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
class StatsCollectingServiceTest {
    /**
     * Given
     *      a valid player with no stats scoreboard
     * When
     *      cleanResources is called
     * Then
     *     should return immediatly.
     */
    @Test
    fun cleanResources_PlayerWithNoScoreboard_ShouldReturnImmediatly() {
        // Arrange
        val classUnderTest = createWithDependencies()
        val player = Mockito.mock(Player::class.java)

        // Act
        val value = classUnderTest.cleanResources(player)

        // Assert
        Assertions.assertEquals(Unit, value)
    }

    /**
     * Given
     *      an invalid player
     * When
     *      cleanResources is called
     * Then
     *     should throw exception.
     */
    @Test
    fun cleanResources_InvalidPlayer_ShouldThrowException() {
        // Arrange
        val classUnderTest = createWithDependencies()

        // Act
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            classUnderTest.cleanResources("I'm a player!")
        }
    }

    companion object {
        fun createWithDependencies(
            configurationService: ConfigurationService = MockedConfigurationService(),
            persistenceStatsService: PersistenceStatsService = MockedPersistenceStatsService(),
            scoreboardService: ScoreboardService = MockedScoreboardService(),
            plugin: Plugin = Mockito.mock(Plugin::class.java)
        ): StatsCollectingService {
            if (Bukkit.getServer() == null) {
                val server = Mockito.mock(Server::class.java)
                `when`(server.logger).thenReturn(Logger.getGlobal())
                Bukkit.setServer(server)
            }

            val server = Bukkit.getServer()

            `when`(plugin.server).thenReturn(Bukkit.getServer())
            val scheduler = Mockito.mock(BukkitScheduler::class.java)

            `when`(server.logger).thenReturn(Logger.getGlobal())
            `when`(server.scheduler).thenReturn(scheduler)

            return StatsCollectingServiceImpl(plugin, configurationService, persistenceStatsService, scoreboardService)
        }
    }

    class MockedScoreboardService : ScoreboardService {
        /**
         * Sets the configuration of the given scoreboard.
         */
        override fun <S> setConfiguration(scoreboard: S, displaySlot: Any, title: String) {
        }

        /**
         * Sets the [text] at the given [scoreboard] and [lineNumber].
         */
        override fun <S> setLine(scoreboard: S, lineNumber: Int, text: String) {
        }
    }

    class MockedPersistenceStatsService : PersistenceStatsService {
        /**
         * Returns all stored stats.
         */
        override fun getAll(): CompletableFuture<List<Stats>> {
            throw IllegalArgumentException()
        }

        /**
         * Returns the amount of stored stats.
         */
        override fun size(): CompletableFuture<Int> {
            throw IllegalArgumentException()
        }

        /**
         * Returns the [Stats] from the given [player] or allocates a new one.
         */
        override fun <P> getOrCreateFromPlayer(player: P): CompletableFuture<Stats> {
            throw IllegalArgumentException()
        }

        /**
         * Saves the given [Stats] to the storage.
         */
        override fun <P> save(player: P, stats: Stats): CompletableFuture<Void?> {
            throw IllegalArgumentException()
        }
    }

    class MockedConfigurationService : ConfigurationService {
        /**
         * Tries to load the config value from the given [path].
         * Throws a [IllegalArgumentException] if the path could not be correctly
         * loaded.
         */
        override fun <C> findValue(path: String): C {
            throw IllegalArgumentException()
        }

        /**
         * Tries to load the config values into the given configuration [clazz] from the given [path]
         * Throws a [IllegalArgumentException] if the path could not be correctly
         * loaded.
         */
        override fun <C> findConfiguration(clazz: Class<C>, path: String): C {
            throw IllegalArgumentException()
        }
    }
}