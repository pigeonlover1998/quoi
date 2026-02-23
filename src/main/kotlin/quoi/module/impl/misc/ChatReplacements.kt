package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.module.Module
import quoi.module.impl.misc.Chat.socialCommands
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.ChatUtils.say
import quoi.utils.StringUtils.noControlCodes

object ChatReplacements : Module("Chat Replacements") { // THIS IS A TEMP MODULE. todo replace with custom hiders
    private val chatEmotes by BooleanSetting("Chat emotes")
    private val cleanerDungeons by BooleanSetting("Cleaner dungeons")
    private val cleanerPf by BooleanSetting("Cleaner PF")
    private val hideOtherMessages by BooleanSetting("Hide useless messages")
    private val hideMoreMessages by BooleanSetting("Hide even more useless messages")

    private val hideDiscordWarnings by BooleanSetting("Hide discord warnings", desc = "Hides Discord warning messages.")
    private val hideMicrosoftWarnings by BooleanSetting("Hide microsoft warnings", desc = "Hides Microsoft account warnings.")

    private val hideActionbar by BooleanSetting("Hide actionbar", desc = "Hides ALL actionbar messages/contents.")
    private val hideScoreboardShit by BooleanSetting("Hide scoreboard shit", desc = "Hides the Server ID and www.hypixel.net")
    private val hideEmptyChats by BooleanSetting("Hide empty chat messages", desc = "Hides chat messages with no text.")

    @JvmStatic val shouldHideServerId get() = this.enabled && hideScoreboardShit

    init {
//        on<ChatEvent.Packet> { event ->
//            if (handleChatMessage(event.message)) event.cancel()
//        }

        on<ChatEvent.Receive> {
            if (handleChatMessage(message)) cancel()
            if (hideEmptyChats && message.isBlank()) cancel()
        }

        on<ChatEvent.Sent> {
            if (!chatEmotes || message.startsWith("/") && !socialCommands.any { message.startsWith(it) }) return@on

            var replaced = false
            val words = message.split(" ").toMutableList()

            for (i in words.indices) {
                emojiMap[words[i]]?.let {
                    replaced = true
                    words[i] = it
                }
            }

            if (!replaced) return@on

            cancel()
            say(words.joinToString(" "))
        }
    }

    @JvmStatic
    val shouldHideActionBar get() = this.enabled && hideActionbar

    data class Replacement(val pattern: Regex, val replacement: String)

    val emojiMap = hashMapOf(
        "<3" to "❤",
        "o/" to "( ﾟ◡ﾟ)/",
        ":star:" to "✮",
        ":yes:" to "✔",
        ":no:" to "✖",
        ":java:" to "☕",
        ":arrow:" to "➜",
        ":shrug:" to "¯\\_(ツ)_/¯",
        ":tableflip:" to "(╯°□°）╯︵ ┻━┻",
        ":totem:" to "☉_☉",
        ":typing:" to "✎...",
        ":maths:" to "√(π+x)=L",
        ":snail:" to "@'-'",
        ":thinking:" to "(0.o?)",
        ":gimme:" to "༼つ◕_◕༽つ",
        ":wizard:" to "(' - ')⊃━☆ﾟ.*･｡ﾟ",
        ":pvp:" to "⚔",
        ":peace:" to "✌",
        ":puffer:" to "<('O')>",
        "h/" to "ヽ(^◇^*)/",
        ":sloth:" to "(・⊝・)",
        ":dog:" to "(ᵔᴥᵔ)",
        ":dj:" to "ヽ(⌐■_■)ノ♬",
        ":yey:" to "ヽ (◕◡◕) ﾉ",
        ":snow:" to "☃",
        ":dab:" to "<o/",
        ":cat:" to "= ＾● ⋏ ●＾ =",
        ":cute:" to "(✿◠‿◠)",
        ":skull:" to "☠"
    )

    private val dungeonPrefix = "§dDungeon§f > "

    private val rareDropsToRemove = listOf(
        "RARE DROP! Machine Gun Shortbow",
        "RARE DROP! Beating Heart",
        "RARE DROP! Zombie Commander Boots",
        "RARE DROP! Skeleton Lord Chestplate",
        "RARE DROP! Earth Shard"
    )

    private val toRemove = listOf(
        Regex("^There are blocks in the way!"),
        Regex("^You cannot use abilities in this room!"),

        Regex("^You haven't claimed your Spooky Rewards yet!"),
        Regex("^Talk to the Spooky Man in the Hub!"),
        Regex("^Profile ID:"),
        Regex("^You are playing on profile:"),
        Regex("RARE REWARD! (.+) found a (.+) in their (.+) Chest!"),

        Regex("^You are not allowed to use Potion Effects while in Dungeon.*stored\\."),
        Regex("^\\[Healer].*"),
        Regex("^\\[Mage].*"),
        Regex("^\\[Berserk].*"),
        Regex("^\\[Archer].*"),
        Regex("^\\[Tank].*"),
        Regex("^RIGHT CLICK on a WITHER door.*"),
        Regex("^(.+) has obtained Superboom TNT(.+)"),
        Regex("^RIGHT CLICK on the BLOOD DOOR.*"),
        Regex("^     Granted you .+\\.$"),
        Regex("^     Also granted you .+ & .+\\."),
        Regex("^A Blessing of .+ was picked up!$"),
        Regex("^.+ has obtained Blessing of .+!"),
        Regex("^has obtained Revive Stone!"),
        Regex("^This menu is disabled here!"),
        Regex("^The Lost Adventurer used Dragon's Breath on you!"),
        Regex("^A Crypt Wither Skull exploded, hitting you for (.+) damage."),
        Regex("^(.+) has obtained Premium Flesh!")
    )

    private val toReplace = listOf(
        Replacement(Regex("^Your (.+) stats are doubled because you are the only player using this class!$"), "§7Recieved double class stats. (No dupe)"),
        Replacement(Regex("^Starting in (\\d) (.+)"), "§aStarting in $1..."),
        Replacement(Regex("^(.+) Milestone (.+): You have (.+)$"), "§6Milestone $2"),
        Replacement(Regex("^DUNGEON BUFF! (.+) found a Blessing of (Power|Life|Stone|Wisdom) (.+)! ?(.+)?$"), "§7$2 $3"),
        Replacement(Regex("^DUNGEON BUFF! A Blessing of (Power|Life|Stone|Wisdom|Time) (.+) was found! (.+)$"), "§7$1 $2"),
        Replacement(Regex("^A Blessing of (Power|Life|Stone|Wisdom) (.+) was found! (.+)$"), "§7$1"),
        Replacement(Regex("^ESSENCE! (.+) found x10 (Ice|Spider|Gold|Diamond) Essence!$"), "§b$2 Essence."),
        Replacement(Regex("^(.+) found a Wither Essence! Everyone gains an extra essence!$"), "§bWither Essence."),
        Replacement(Regex("^You hear the sound of something opening\\.\\.\\.$"), "§7You used a lever."),
        Replacement(Regex("^This lever has already been used\\.$"), "§cThis lever has been used."),
        Replacement(Regex("^This chest has already been searched!$"), "§cThis chest has been searched!"),
        Replacement(Regex("^That chest is locked!$"), "§cThat chest is locked!"),
        Replacement(Regex("^(.+) is ready to use! Press DROP to activate it!$"), "§9Ultimate Available"),
        Replacement(Regex("^(.+) has obtained Wither Key!$"), "§eWither Key picked up!"),
        Replacement(Regex("^A Wither Key was picked up!$"), "§eWither Key picked up!"),
        Replacement(Regex("^(.+) has obtained Blood Key!$"), "§cBlood Key picked up!"),
        Replacement(Regex("^A Blood Key was picked up!$"), "§cBlood Key picked up"),
        Replacement(Regex("^◕ You picked up a (.+) from (.+) healing you for (.+) and granting you \\+(.+)% (.+) for 10 seconds.$"), "§e$1 picked up §7(§e+§c$3§7)§e!")
    )

    private val otherPatterns = listOf(
        Regex("§f +§r§7You are now §r§.Event Level §r§.*§r§7!"),
        Regex("§f +§r§7You earned §r§.* Event Silver§r§7!"),
        Regex("§f +§r§.§k#§r§. LEVEL UP! §r§.§k#"),
        Regex("§aYou earned §r§2.* GEXP (§r§a\\+ §r§.* Event EXP )?§r§afrom playing SkyBlock!"),

        Regex("^§.* §r§7has been promoted to §r§7\\[.*§r§7] §r§.*§r§7!"),
        Regex("^§7Your §r§aRabbit Barn §r§7capacity has been increased to §r§a.* Rabbits§r§7!"),
        Regex("^§7You will now produce §r§6.* Chocolate §r§7per click!"),
        Regex("^§7You upgraded to §r§d.*?§r§7!"),
        Regex("^§d§lHOPPITY'S HUNT §r§dA §r§.Chocolate (.+) Egg §r§dhas appeared!"),

        Regex("^§6§k§lA§r §c§lFIRE SALE §r§6§k§lA(?:\\n|.)*"),
        Regex("^§c♨ §eFire Sales for .* §eare starting soon!"),
        Regex("^§c\\s*♨ .* (?:Skin|Rune|Dye) §e(?:for a limited time )?\\(.* §eleft\\)(?:§c|!)"),
        Regex("^§c♨ §eVisit the Community Shop in the next §c.* §eto grab yours! §a§l\\[WARP]"),
        Regex("^§c♨ §eA Fire Sale for .* §eis starting soon!"),
        Regex("^§c♨ §r§eFire Sales? for .* §r§eended!"),
        Regex("^§c {3}♨ §eAnd \\d+ more!"),

        Regex("^§.§l\\+(.*) Kill Combo (.*)"),
        Regex("^§cYour Kill Combo has expired! You reached a (.*) Kill Combo!"),
        Regex("^§6§l\\+50 Kill Combo"),

        Regex("^§aYou are playing on profile: §e"),
        Regex("^§8Profile ID: "),

        Regex("§6§lRARE REWARD! (.*) §r§efound a (.*) §r§ein their (.*) Chest§r§e!"),

        Regex("§f +§r§7You are now §r§.Event Level §r§.*§r§7!"),
        Regex("§f +§r§7You earned §r§.* Event Silver§r§7!"),
        Regex("§f +§r§.§k#§r§. LEVEL UP! §r§.§k#"),
        Regex("§aYou earned §r§2.* GEXP (§r§a\\+ §r§.* Event EXP )?§r§afrom playing SkyBlock!")
    )

    private val otherPatternsNoControlCodes = listOf(
        Regex("Sending to server .+"),
        Regex("Rabbit .+"),
        Regex("Wait a moment..."),
        Regex("DUPLICATE RABBIT! .+"),
        Regex("Putting item in escrow..."),
        Regex("Setting up the auction..."),
        Regex("You are not allowed to use Potion Effects while in Dungeon, therefore all active effects have been paused and stored. They will be restored when you leave Dungeon!"),
        Regex("The BLOOD DOOR has been opened!"),
        Regex("A shiver runs down your spine..."),
        Regex("Moved .+ from your Sacks to your inventory."),
        Regex(" ❣ .+"),
        Regex(" ☠ .+"),
        Regex("ESSENCE! .+"),
        Regex("◕ .+"),
        Regex("Warping..."),
        Regex("  ➤ .+"),
        Regex("✦ .+"),
        Regex("\\[SKULL] .+"),
        Regex("Someone else is currently reviving that player!"),
        Regex("Queueing your party..."),
        Regex("De-listing your group..."),
        Regex("Only the instance creator can re-queue!"),
        Regex("Depositing coins..."),
        Regex("Withdrawing coins..."),
        Regex("You can only use this item inside dungeons!"),
        Regex("The dungeon hasn't started yet!"),
        Regex("You have teleported to .+"),
        Regex("Attempting to add you to the party..."),
        Regex("You're already in this channel!"),
        Regex("You bought .+"),
        Regex("You sold .+"),
        Regex("This menu is currently occupied!"),
        Regex("You may only use this command after 4s on the server!"),
        Regex("The .+ you for .+ damage."),
        Regex("You hear something open..."),
        Regex("You found a Secret Redstone Key!"),
        Regex("Be careful! Using Ender Pearls on this island will anger nearby Endermen!"),
        Regex("\\[Bazaar] .+"),
        Regex("Putting coins in escrow..."),
        Regex("Strike using the .+ attunement on your dagger!"),
        Regex("Your hit was reduced by Hellion Shield!"),
        Regex("Processing purchase..."),
        Regex("Claiming BIN auction..."),
        Regex("Visit the Auction House to collect your item!"),
        Regex("RARE! .+"),
        Regex("SWEET! .+"),
        Regex("COMMON! .+"),
        Regex("ALLOWANCE! You earned .+ coins!"),
        Regex("You are sending commands too fast! Please slow down."),
        Regex("You haven't claimed your Holidays Rewards yet!"),
        Regex("Talk to the Gingerbread Man in the Hub!"),
        Regex(".+ FIRE SALE .+"),
        Regex("♨ Selling multiple items for a limited time!"),
        Regex("♨ .+ \\(.+ left\\)"),
        Regex("♨ \\[WARP] To Elizabeth in the next .+ to grab yours!"),
        Regex("Your bone plating reduced the damage you took by .+!"),
        Regex("Warping you to your SkyBlock island..."),
        Regex("You earned .+ Event EXP from playing SkyBlock!"),
        Regex("Watchdog has banned .+ players in the last 7 days."),
        Regex("Error initializing players: undefined"),
        Regex("Goldor's TNT Trap hit you for .+ true damage."),
        Regex("This Terminal doesn't seem to be responsive at the moment."),
        Regex("Whow! Slow down there!"),
        Regex("⚠ Storm is enraged! ⚠"),
        Regex("Giga Lightning.+"),
        Regex("Necron's Nuclear Frenzy hit you for .+ damage."),
        Regex("Woah slow down, you're doing that too fast!"),
        Regex("Command Failed: This command is on cooldown! Try again in about a second!"),
        Regex("Someone has already activated this lever!"),
        Regex("Goldor's Greatsword hit you for .+ damage."),
        Regex("A mystical force in this room prevents you from using that ability!"),
        Regex("The Frozen Adventurer used Ice Spray on you!"),
        Regex("It isn't your turn!"),
        Regex("Don't move diagonally! Bad!"),
        Regex("Oops! You stepped on the wrong block!"),
        Regex("Used Ragnarok!"),
        Regex("Your Auto Recombobulator recombobulated .+"),
        Regex("Blacklisted modifications are a bannable offense!"),
        Regex("\\[WATCHDOG ANNOUNCEMENT]"),
        Regex("Staff have banned an additional .+"),
        Regex("Your Ultimate is currently on cooldown for .+ more seconds."),
        Regex("ESSENCE! .+ found .+ Essence!"),
        Regex("You hear the sound of something opening..."),
        Regex("You sold .+ x.* for .+"),
        Regex("You don't have enough space in your inventory to pick up this item!.*"),
        Regex("Inventory full\\? Don't forget to check out your Storage inside the SkyBlock Menu!"),
        Regex("Your Berserk ULTIMATE Ragnarok is now available!"),
        Regex("This item's ability is temporarily disabled!"),
        Regex("Throwing Axe is now available!"),
        Regex("Used Throwing Axe!"),
        Regex("\\[STATUE].+"),
        Regex("PUZZLE SOLVED!.+"),
        Regex("DUNGEON BUFF! .+"),
        Regex("You summoned your.+"),
        Regex("\\[BOMB] Creeper:.+"),
        Regex("\\[Sacks] .+ item.+"),
        Regex("The .+ Trap hit you for .+ damage!"),
        Regex("Healer Milestone.+"),
        Regex("Archer Milestone.+"),
        Regex("Mage Milestone.+"),
        Regex("Tank Milestone.+"),
        Regex("Berserk Milestone.+"),
        Regex(".+ joined the lobby! .*"),
        Regex("Welcome to Hypixel SkyBlock!"),
        Regex("Latest update: SkyBlock .+"),
        Regex(".+ is now ready!"),
        Regex("Queuing... .+"),
        Regex(".+ Milestone .+:.+ "),
        Regex("RIGHT CLICK on .+ to open it. .+"),
        Regex(".+ Mort: .+"),
        Regex("Your .+ hit .+ for [\\d,.]+ damage."),
        Regex("You do not have enough mana to do this!"),
        Regex(".+Kill Combo+"),
        Regex(".+ healed you for .+ health!"),
        Regex("You earned .+ GEXP .*"),
        Regex(".+ unlocked .+ Essence!"),
        Regex(".+ unlocked .+ Essence x\\d+!"),
        Regex("This item is on cooldown.+"),
        Regex("This ability is on cooldown.+"),
        Regex("You do not have the key for this door!"),
        Regex("The Stormy .+ struck you for .+ damage!"),
        Regex("Please wait a few seconds between refreshing!"),
        Regex("You cannot move the silverfish in that direction!"),
        Regex("You cannot hit the silverfish while it's moving!"),
        Regex("Your Kill Combo has expired! You reached a .+ Kill Combo!"),
        Regex("Your active Potion Effects have been paused and stored. They will be restored when you leave Dungeons! You are not allowed to use existing Potion Effects while in Dungeons."),
        Regex(".+ has obtained Blood Key!"),
        Regex("The Flamethrower hit you for .+ damage!"),
        Regex(".+ found a Wither Essence! Everyone gains an extra essence!"),
        Regex(".+ is ready to use! Press DROP to activate it!"),
        Regex("This creature is immune to this kind of magic!"),
        Regex("FISHING FESTIVAL The festival is now underway! Break out your fishing rods and watch out for sharks!"),
        Regex("Starting in .+"),
        Regex("Your .+ stats are doubled because you are the only player using this class!"),
        Regex("\\[Healer] .+"),
        Regex("\\[Tank] .+"),
        Regex("\\[Archer] .+"),
        Regex("\\[Berserk] .+"),
        Regex("\\[Mage] .+"),
        Regex("BONUS! Temporarily earn .+ more skill experience!"),
        Regex("\n" +
                "➔ Welcome to the Prototype Lobby\n" +
                "All games in this lobby are currently in development.\n" +
                "Click here to leave feedback! ➤ https://hypixel.net/PTL\n"),
        Regex("You received .+ for killing .+"),
        Regex("SALT .+"),
        Regex("You caught a .+ Shard!"),
        Regex("You caught .+ Shards!"),
        Regex("DAVID: .+"),
        Regex("Profile ID: .+"),
        Regex("BUFF! .+"),
        Regex(".+ has obtained Wither Key!"),
        Regex(".+ opened a WITHER door!"),
        Regex("Used Healing Circle!"),
        Regex("Healing Circle is now available!"),
        Regex("\\[BOSS] .+"),
        Regex("  ൠ .+"),
        Regex("Your Garden is no longer infested and your ☘ Farming Fortune has returned to normal!"),
        Regex("Party Finder > Your dungeon group is full! Click here to warp to the dungeon!"),
        Regex("You formed a tether with .+!"),
        Regex("Couldn't warp you! Try again later. \\(PLAYER_TRANSFER_COOLDOWN\\)"),
        Regex("Your .+ healed your entire team for .+ health and shielded them for .+!"),
        Regex(".+ activated a terminal! .+"),
        Regex(".+ activated a lever! .+"),
        Regex(".+ completed a device! .+"),
        Regex("The Core entrance is opening!"),
        Regex("The gate has been destroyed!"),
        Regex("Your .+ is now available!"),
        Regex("UNIVERSAL INCOME: You gained .+ Coins."),
        Regex("A total of .+ Coins have been distributed across .+ players."),
        Regex("The Energy Laser is charging up!"),
        Regex("That item cannot be sold!"),
        Regex(".+ picked up an Energy Crystal!"),
        Regex(".+ Energy Crystals are now active!"),
        Regex("Not enough mana! Creeper Veil De-activated!"),
        Regex("⚠ .+"),
        Regex("Creeper Veil Activated!"),
        Regex("Creeper Veil De-activated!"),
        Regex("The gate will open in 5 seconds!"),
        Regex("Claiming upgrade..."),
        Regex("Starting profile upgrade..."),
        Regex("Starting account upgrade..."),
        Regex("You claimed the .+ upgrade!"),
        Regex("You started the .+ upgrade!"),
        Regex("SPOOKY FESTIVAL .+"),
        Regex("SALT: .+"),
        Regex("Woah! Slow down there!"),
        Regex("CHARM .+"),
        Regex("You may only use this menu after 4s on the server!"),
        Regex("Evacuating to Hub..."),
        Regex(" >>> \\[MVP\\+\\+] .+ slid into the lobby! <<<"),
        Regex("\\[MVP\\+] .+ slid into the lobby!"),
        Regex("You are not allowed to use that command as a spectator!"),
        Regex("Boomer .+"),
        Regex("Your .+ saved your life!"),
        Regex("Your .+ leveled up to level .+!"),
        Regex("Warning! The instance will .+"),
        Regex("Your .+ saved you from certain death!"),
        Regex("Second Wind Activated! Your Spirit Mask saved your life!"),
        Regex("Your Tuning Points were auto-assigned as convenience!"),
        Regex("A .+ was picked up!"),
        Regex("Bonzo's .+"),
        Regex("Stormy .+"),
        Regex("Goldor's .+"),
        Regex("Necron's .+"),
        Regex("Maxor's .+"),
        Regex("Storm's .+"),
        Regex("Livid's .+"),
        Regex("Sadan's .+"),
        Regex("A Crypt Wither Skull exploded, hitting you for .+"),
        Regex("     Granted you .+ ❁ Strength."),
        Regex("A mystical force prevents you digging in this room!"),
        Regex("Mute silenced you!"),
        Regex("A Event: New Year's Celebration! A"),
        Regex("A Everyone is having a party in the Village!"),
        Regex("A CLICK HERE to get your SPECIAL new year cake!"),
        Regex("RARE DROP! .+"),
        Regex("HOPPITY'S HUNT You found a .+"),
        Regex("You found a journal .+"),
        Regex("You have already collected this .+ Try again when it respawns!"),
        Regex("You have been re-queued!"),
        Regex("A Prince falls. +1 Bonus Score"),
        Regex("\\[NPC] .+"),
        Regex("  Clicking sketchy links can result in your account"),
        Regex("  being stolen!"),
        Regex("  Link looks suspicious\\? - Don't click it!"),
        Regex("✆ .+"),
        Regex(" ☺ .+"),
        Regex("You used a .+!"),
        Regex("Please wait..."),
        Regex("Submitting Report..."),
        Regex("A mystical force prevents you digging there!"),
        Regex("A mystical force prevents you from digging that block!"),
        Regex("The Time Tower is already active!"),
        Regex("Autopet equipped your .+"),
        Regex("\\[CROWD] .+"),
        Regex("You cannot drop items yet!"),
        Regex("There are no reachable enemies nearby!"),
        Regex("The wind has changed direction!"),
        Regex("""^Unknown command\. Type "/help" for help\. \('.+'\)$"""),
        Regex(".+ joined the lobby!")
    )

    private val pfClassChangeRegex = Regex("""^Party Finder > (.+?) set their class to (\w+) Level (\d+)!$""")
    private val pfJoinRegex = Regex("""^Party Finder > (.+?) joined the dungeon group! \((\w+) Level (\d+)\)$""")

    private val pfReplaceMap = mapOf(
        "Party Finder > Your group has been de-listed!" to "§dPF > §aParty Delisted.",
        "Party Finder > Your party has been queued in the party finder!" to "§dPF > §aParty Queued.",
        "Party Finder > Your group has been removed from the party finder!" to "§dPF > §cParty Removed.",
        "Refreshing..." to "§dPF > §aRefreshing.",
        "Party Finder > You are already in a party!" to "§dPF > §cAlready In Party.",
        "Party Finder > Your party has been queued in the dungeon finder!" to "§dPF > §aParty Queued.",
        "Party Finder > This group has been de-listed." to "§dPF > §cGroup Delisted.",
        "Party Finder > This group is full and has been de-listed!" to "§dPF > §cGroup Delisted.",
        "Party Finder > You are already in a group!" to "§dPF > §cAlready In Group.",
        "Party Finder > This group doesn't exist!" to "§dPF > §cGroup Doesn't Exist.",
        "Party Finder > This group is full!" to "§dPF > §cGroup Full."
    )

    private val discordWarningRegex = Regex("""Please be mindful of Discord links in chat as they may pose a security risk""")
    private val microsoftWarningRegex = Regex(
        """-----------------------------------------------------
You should NEVER enter your Microsoft account details anywhere but on official Microsoft services!

External links from untrusted sources should be avoided.
-----------------------------------------------------"""
    )

    private fun handleChatMessage(message: String): Boolean {
        val noCodes = message.noControlCodes
        if (cleanerDungeons) {
            for (r in toReplace) {
                val m = r.pattern.find(noCodes) ?: continue
                modMessage(m.value.replace(r.pattern, r.replacement), prefix = dungeonPrefix)
                return true
            }
            if (rareDropsToRemove.contains(noCodes)) return true
            if (toRemove.any { it.containsMatchIn(noCodes) }) return true
        }
        if (cleanerPf) {
            pfReplaceMap[noCodes]?.let { new ->
                modMessage(new, prefix = "")
                return true
            }

            pfClassChangeRegex.find(noCodes)?.let { match ->
                val player = match.groupValues[1]
                val clazz = match.groupValues[2]
                val level = match.groupValues[3]
                val cleanedMsg = "§dPF > §b$player §echanged to §b$clazz $level§e!"
                modMessage(cleanedMsg, prefix = "")
                return true
            }

            pfJoinRegex.find(noCodes)?.let { match ->
                val player = match.groupValues[1]
                val clazz = match.groupValues[2]
                val level = match.groupValues[3]
                val cleanedMsg = "§dPF > §b$player §ejoined the group! (§b$clazz $level§e)"
                modMessage(cleanedMsg, prefix = "")
                return true
            }
        }

        if (hideOtherMessages && otherPatterns.any { it.containsMatchIn(message) }) return true
        if (hideMoreMessages && otherPatternsNoControlCodes.any { it.containsMatchIn(noCodes)}) return true

        if (hideDiscordWarnings && discordWarningRegex.containsMatchIn(message)) {
            val cleaned = message.replace(discordWarningRegex, "").trimEnd()
            modMessage(cleaned, prefix = "")
            return true
        }

        if (hideMicrosoftWarnings && microsoftWarningRegex.containsMatchIn(noCodes)) {
            return true
        }

        return false
    }
}