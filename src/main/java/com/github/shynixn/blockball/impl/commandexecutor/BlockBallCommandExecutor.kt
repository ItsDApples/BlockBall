package com.github.shynixn.blockball.impl.commandexecutor

import com.github.shynixn.blockball.BlockBallLanguageImpl
import com.github.shynixn.blockball.contract.BlockBallLanguage
import com.github.shynixn.blockball.contract.GameService
import com.github.shynixn.blockball.entity.SoccerArena
import com.github.shynixn.blockball.entity.TeamMeta
import com.github.shynixn.blockball.enumeration.*
import com.github.shynixn.blockball.impl.exception.SoccerGameException
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mcutils.common.*
import com.github.shynixn.mcutils.common.chat.ChatMessageService
import com.github.shynixn.mcutils.common.command.CommandBuilder
import com.github.shynixn.mcutils.common.command.CommandMeta
import com.github.shynixn.mcutils.common.command.CommandType
import com.github.shynixn.mcutils.common.command.Validator
import com.github.shynixn.mcutils.common.repository.CacheRepository
import com.github.shynixn.mcutils.sign.SignService
import com.google.inject.Inject
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.*
import java.util.logging.Level

class BlockBallCommandExecutor @Inject constructor(
    private val arenaRepository: CacheRepository<SoccerArena>,
    private val gameService: GameService,
    private val plugin: Plugin,
    private val configurationService: ConfigurationService,
    private val language: BlockBallLanguage,
    private val signService: SignService,
    chatMessageService: ChatMessageService
) {
    private val arenaTabs: suspend (s: CommandSender) -> List<String> = {
        arenaRepository.getAll().map { e -> e.name }
    }
    private val coroutineExecutor = object : CoroutineExecutor {
        override fun execute(f: suspend () -> Unit) {
            plugin.launch {
                f.invoke()
            }
        }
    }

    private val remainingStringValidator = object : Validator<String> {
        override suspend fun transform(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return openArgs.joinToString(" ")
        }
    }
    private val maxLengthValidator = object : Validator<String> {
        override suspend fun validate(
            sender: CommandSender,
            prevArgs: List<Any>,
            argument: String,
            openArgs: List<String>
        ): Boolean {
            return argument.length < 20
        }

        override suspend fun message(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return language.maxLength20Characters
        }
    }
    private val gameMustNotExistValidator = object : Validator<String> {
        override suspend fun validate(
            sender: CommandSender,
            prevArgs: List<Any>,
            argument: String,
            openArgs: List<String>
        ): Boolean {
            val existingArenas = arenaRepository.getAll()
            return existingArenas.firstOrNull { e -> e.name.equals(argument, true) } == null
        }

        override suspend fun message(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return language.gameAlreadyExistsMessage.format(openArgs[0])
        }
    }
    private val gameMustExistValidator = object : Validator<SoccerArena> {
        override suspend fun transform(
            sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>
        ): SoccerArena? {
            val existingArenas = arenaRepository.getAll()
            return existingArenas.firstOrNull { e -> e.name.equals(openArgs[0], true) }
        }

        override suspend fun message(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return language.gameDoesNotExistMessage.format(openArgs[0])
        }
    }
    private val teamValidator = object : Validator<Team> {
        override suspend fun transform(
            sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>
        ): Team? {
            return try {
                Team.valueOf(openArgs[0].uppercase(Locale.ENGLISH))
            } catch (e: Exception) {
                return null
            }
        }

        override suspend fun message(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return language.teamDoesNotExistMessage.format(openArgs[0])
        }
    }

    private val teamMetaValidator = object : Validator<TeamMeta> {
        override suspend fun transform(
            sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>
        ): TeamMeta? {
            val team: Team = try {
                Team.valueOf(openArgs[0].uppercase(Locale.ENGLISH))
            } catch (e: Exception) {
                return null
            }
            val arena = prevArgs[prevArgs.size - 1] as SoccerArena
            val teamMeta = if (team == Team.RED) {
                arena.meta.redTeamMeta
            } else {
                arena.meta.blueTeamMeta
            }
            return teamMeta
        }

        override suspend fun message(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return language.teamDoesNotExistMessage.format(openArgs[0])
        }
    }

    private val locationTypeValidator = object : Validator<LocationType> {
        override suspend fun transform(
            sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>
        ): LocationType? {
            return LocationType.values().firstOrNull { e -> e.id.equals(openArgs[0], true) }
        }

        override suspend fun message(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return language.locationTypeDoesNotExistMessage
        }
    }
    private val signTypeValidator = object : Validator<SignType> {
        override suspend fun transform(
            sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>
        ): SignType? {
            return SignType.values().firstOrNull { e -> e.name.equals(openArgs[0], true) }
        }

        override suspend fun message(sender: CommandSender, prevArgs: List<Any>, openArgs: List<String>): String {
            return language.signTypeDoesNotExistMessage
        }
    }

    init {
        val mcCart = CommandBuilder(plugin, coroutineExecutor, "blockball", chatMessageService) {
            usage(language.commandUsage.translateChatColors())
            description(language.commandDescription)
            aliases(plugin.config.getStringList("commands.blockball.aliases"))
            permission(Permission.COMMAND)
            permissionMessage(language.noPermissionMessage.translateChatColors())
            subCommand("create") {
                permission(Permission.EDIT_GAME)
                builder().argument("name").validator(maxLengthValidator).validator(maxLengthValidator)
                    .validator(gameMustNotExistValidator).tabs { listOf("<name>") }.argument("displayName")
                    .validator(remainingStringValidator).tabs { listOf("<displayName>") }
                    .execute { sender, name, displayName -> createArena(sender, name, displayName) }
            }
            subCommand("delete") {
                permission(Permission.EDIT_GAME)
                builder().argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .execute { sender, arena -> deleteArena(sender, arena) }
            }
            subCommand("list") {
                permission(Permission.EDIT_GAME)
                builder().execute { sender -> listArena(sender) }
            }
            subCommand("toggle") {
                permission(Permission.EDIT_GAME)
                builder().argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .execute { sender, arena -> toggleGame(sender, arena) }
            }
            subCommand("join") {
                noPermission()
                builder().argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .executePlayer({ language.commandSenderHasToBePlayer }) { sender, arena ->
                        joinGame(
                            sender, arena.name
                        )
                    }.argument("team").validator(teamValidator).tabs { listOf("red", "blue") }
                    .executePlayer({ language.commandSenderHasToBePlayer }) { sender, arena, team ->
                        joinGame(sender, arena.name, team)
                    }
            }
            subCommand("leave") {
                noPermission()
                builder().executePlayer({ language.commandSenderHasToBePlayer }) { sender -> leaveGame(sender) }
            }
            helpCommand()
            subCommand("location") {
                permission(Permission.EDIT_GAME)
                builder().argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .argument("type").validator(locationTypeValidator).tabs { LocationType.values().map { e -> e.id } }
                    .executePlayer({ language.commandSenderHasToBePlayer }) { player, arena, locationType ->
                        setLocation(player, arena, locationType)
                    }
            }
            subCommand("inventory") {
                permission(Permission.EDIT_GAME)
                builder().argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .argument("team").validator(teamMetaValidator).tabs { listOf("red", "blue") }
                    .executePlayer({ language.commandSenderHasToBePlayer }) { player, arena, meta ->
                        setInventory(player, arena, meta)
                    }
            }
            subCommand("armor") {
                permission(Permission.EDIT_GAME)
                builder().argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .argument("team").validator(teamMetaValidator).tabs { listOf("red", "blue") }
                    .executePlayer({ language.commandSenderHasToBePlayer }) { player, arena, meta ->
                        setArmor(player, arena, meta)
                    }
            }
            subCommand("sign") {
                permission(Permission.EDIT_GAME)
                builder().argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .argument("type").validator(signTypeValidator).tabs { listOf("join", "leave") }
                    .executePlayer({ language.commandSenderHasToBePlayer }) { player, arena, signType ->
                        setSign(player, arena, signType)
                    }
            }
            subCommand("reload") {
                permission(Permission.EDIT_GAME)
                builder()
                    .execute { sender ->
                        reloadArena(sender, null)
                    }
                    .argument("name").validator(gameMustExistValidator).tabs(arenaTabs)
                    .execute { sender, arena ->
                        reloadArena(sender, arena)
                    }
            }

        }
        mcCart.build()
    }

    private suspend fun createArena(sender: CommandSender, name: String, displayName: String) {
        val arena = SoccerArena()
        arena.name = name
        arena.displayName = displayName
        arenaRepository.save(arena)
        sender.sendMessage(language.gameCreatedMessage.format(name))
    }

    private suspend fun deleteArena(sender: CommandSender, arena: SoccerArena) {
        val runningGame = gameService.getAll().firstOrNull { e -> e.arena.name.equals(arena.name, true) }
        runningGame?.close()
        arenaRepository.delete(arena)
        sender.sendMessage(language.deletedGameMessage.format(arena.name))
    }

    private suspend fun toggleGame(sender: CommandSender, arena: SoccerArena) {
        try {
            arena.enabled = !arena.enabled
            gameService.reload(arena)
            sender.sendMessage(language.enabledArenaMessage.format(arena.enabled.toString()))
        } catch (e: SoccerGameException) {
            arena.enabled = !arena.enabled
            sender.sendMessage(ChatColor.RED.toString() + "Failed to reload soccerArena ${e.arena.name}.")
            sender.sendMessage(e.message)
            return
        }
        arenaRepository.save(arena)
        sender.sendMessage(language.reloadedGameMessage.format(arena.name))
    }

    private suspend fun setInventory(player: Player, arena: SoccerArena, teamMetadata: TeamMeta) {
        teamMetadata.inventory = player.inventory.contents.clone().map { e ->
            val yamlConfiguration = YamlConfiguration()
            yamlConfiguration.set("item", e)
            yamlConfiguration.saveToString()
        }.toTypedArray()
        arenaRepository.save(arena)
        player.sendMessage(language.updatedInventoryMessage)
    }

    private suspend fun setArmor(player: Player, arena: SoccerArena, teamMeta: TeamMeta) {
        teamMeta.armor = player.inventory.armorContents.clone().map { e ->
            val yamlConfiguration = YamlConfiguration()
            yamlConfiguration.set("item", e)
            yamlConfiguration.saveToString()
        }.toTypedArray()
        arenaRepository.save(arena)
        player.sendMessage(language.updatedArmorMessage)
    }

    private fun CommandBuilder.permission(permission: Permission) {
        this.permission(permission.permission)
    }

    private suspend fun listArena(sender: CommandSender) {
        val existingArenas = arenaRepository.getAll()

        sender.sendMessage("---------BlockBall---------")
        for (arena in existingArenas) {
            if (arena.enabled) {
                sender.sendMessage(ChatColor.GRAY.toString() + arena.name + " [${arena.displayName.translateChatColors()}" + ChatColor.GRAY + "] " + ChatColor.GREEN + "[enabled]")
            } else {
                sender.sendMessage(ChatColor.GRAY.toString() + arena.name + " [${arena.displayName.translateChatColors()}" + ChatColor.GRAY + "] " + ChatColor.RED + "[disabled]")

            }

            sender.sendMessage()
        }
        sender.sendMessage("----------┌1/1┐----------")
    }

    private fun joinGame(player: Player, name: String, team: Team? = null) {
        for (game in gameService.getAll()) {
            if (game.getPlayers().contains(player)) {
                if (game.arena.name.equals(name, true)) {
                    // Do not leave, if it is the same game.
                    return
                }

                game.leave(player)
            }
        }

        val game = gameService.getByName(name)

        if (game == null) {
            player.sendMessage(language.gameDoesNotExistMessage.format(name))
            return
        }

        if (!player.hasPermission(
                Permission.JOIN.permission.replace(
                    "[name]",
                    game.arena.name
                )
            ) && !player.hasPermission(Permission.JOIN.permission.replace("[name]", "*"))
        ) {
            player.sendMessage(language.noPermissionForGameMessage.format(game.arena.name))
            return
        }

        val joinResult = game.join(player, team)

        if (team != null && joinResult == JoinResult.TEAM_FULL) {
            if (team == Team.BLUE) {
                return joinGame(player, name, Team.RED)
            } else {
                return joinGame(player, name, Team.BLUE)
            }
        }

        if (joinResult == JoinResult.GAME_FULL || joinResult == JoinResult.GAME_ALREADY_RUNNING) {
            player.sendMessage(language.gameIsFullMessage)
            return
        }

        if (joinResult == JoinResult.SUCCESS_BLUE) {
            player.sendMessage(language.joinTeamBlueMessage)
        } else if (joinResult == JoinResult.SUCCESS_RED) {
            player.sendMessage(language.joinTeamRedMessage)
        }
    }

    private fun leaveGame(player: Player) {
        var leftGame = false

        for (game in gameService.getAll()) {
            if (game.getPlayers().contains(player)) {
                game.leave(player)
                leftGame = true
            }
        }

        if (leftGame) {
            player.sendMessage(language.leftGameMessage)
        }
    }

    private suspend fun setLocation(player: Player, arena: SoccerArena, locationType: LocationType) {
        if (locationType == LocationType.LOBBY_RED) {
            arena.meta.redTeamMeta.lobbySpawnpoint = player.location.toVector3d()
        }
        arenaRepository.save(arena)
        player.sendMessage(language.spawnPointSetMessage.format(locationType.name.uppercase(), player.location))
    }

    private suspend fun setSign(sender: Player, arena: SoccerArena, signType: SignType) {
        if (signType == SignType.JOIN) {
            sender.sendMessage(language.rightClickOnSignMessage)
            signService.addSignByRightClick(sender) { sign ->
                sign.let {
                    it.line1 = "%mctennis_lang_joinSignLine1%"
                    it.line2 = "%mctennis_lang_joinSignLine2%"
                    it.line3 = "%mctennis_lang_joinSignLine3%"
                    it.line4 = "%mctennis_lang_joinSignLine4%"
                    it.cooldown = 20
                    it.update = 40
                    it.commands = mutableListOf(CommandMeta().also {
                        it.command = "/mctennis join ${arena.name}"
                        it.type = CommandType.PER_PLAYER
                    })
                }

                if (arena.meta.lobbyMeta.joinSigns.firstOrNull { e -> e.isSameSign(sign) } == null) {
                    arena.meta.lobbyMeta.joinSigns.add(sign)
                }

                plugin.launch {
                    arenaRepository.save(arena)
                    gameService.reload(arena)
                    sender.sendMessage(language.addedSignMessage)
                }
            }
        } else if (signType == SignType.LEAVE) {
            sender.sendMessage(language.rightClickOnSignMessage)
            signService.addSignByRightClick(sender) { sign ->
                sign.let {
                    it.line1 = "%mctennis_lang_leaveSignLine1%"
                    it.line2 = "%mctennis_lang_leaveSignLine2%"
                    it.line3 = "%mctennis_lang_leaveSignLine3%"
                    it.line4 = "%mctennis_lang_leaveSignLine4%"
                    it.cooldown = 20
                    it.update = 40
                    it.commands = mutableListOf(CommandMeta().also {
                        it.command = "/mctennis leave"
                        it.type = CommandType.PER_PLAYER
                    })
                }

                if (arena.meta.lobbyMeta.joinSigns.firstOrNull { e -> e.isSameSign(sign) } == null) {
                    arena.meta.lobbyMeta.joinSigns.add(sign)
                }

                plugin.launch {
                    arenaRepository.save(arena)
                    gameService.reload(arena)
                    sender.sendMessage(language.addedSignMessage)
                }
            }
        }
    }


    private suspend fun reloadArena(sender: CommandSender, arena: SoccerArena?) {
        try {
            arenaRepository.clearCache()
        } catch (e: SoccerGameException) {
            sender.sendMessage(ChatColor.RED.toString() + "Failed to reload arenas.")
            sender.sendMessage(e.message)
            return
        }

        if (arena == null) {
            plugin.reloadConfig()
            val languageDef = configurationService.findValue<String>("language")
            plugin.reloadTranslation(languageDef, BlockBallLanguageImpl::class.java, "en_us")
            plugin.logger.log(Level.INFO, "Loaded language file $languageDef.properties.")

            try {
                arenaRepository.clearCache()
                gameService.reloadAll()
            } catch (e: SoccerGameException) {
                sender.sendMessage(ChatColor.RED.toString() + "Failed to reload soccerArena ${e.arena.name}.")
                sender.sendMessage(e.message)
                return
            }

            sender.sendMessage(language.reloadedAllGamesMessage)
            return
        }

        try {
            arenaRepository.clearCache()
            gameService.reload(arena)
        } catch (e: SoccerGameException) {
            sender.sendMessage(ChatColor.RED.toString() + "Failed to reload soccerArena ${e.arena.name}.")
            sender.sendMessage(e.message)
            return
        }
        sender.sendMessage(language.reloadedGameMessage.format(arena.name))
        return
    }
}
