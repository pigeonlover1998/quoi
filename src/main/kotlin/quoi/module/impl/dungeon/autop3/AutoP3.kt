package quoi.module.impl.dungeon.autop3

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import quoi.QuoiMod.scope
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.KeyEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.config.ConfigList
import quoi.config.ConfigSystem
import quoi.config.configPath
import quoi.config.typeName
import quoi.config.typedEntries
import quoi.utils.key
import quoi.utils.rotation.ClientRotationHandler
import quoi.utils.rotation.ClientRotationProvider
import java.io.File
import quoi.annotations.AlwaysActive
import quoi.module.Module
import quoi.module.impl.dungeon.autop3.rings.P3Action
import quoi.module.impl.dungeon.autop3.rings.StopAction
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.render.*
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw
import kotlin.coroutines.cancellation.CancellationException

@AlwaysActive
object AutoP3 : Module(
    "Auto P3",
    desc = "/p3"
), ClientRotationProvider {
    private val renderMode by selector("Render mode", "Nodes", listOf("Nodes", "Wireframe", "Filled", "Flat", "Cylinder", "Corners", "Label", "Circle", "Silent"))
    private val multicolour by switch("Multicolour")
    private val colour by colourPicker("Colour", Colour.CYAN).visibleIf { !multicolour && renderMode.selected != "Silent" }
    private val fillColour by colourPicker("Fill colour", Colour.CYAN.withAlpha(0.5f), allowAlpha = true).visibleIf { (renderMode.selected == "Filled" || renderMode.selected == "Flat" || renderMode.selected == "Nodes") && !multicolour }
    private val activeCol by colourPicker("Active colour", Colour.WHITE).visibleIf { renderMode.selected != "Silent" }
    val strafe45 by switch("45", true)
    private val triggerKey by keybind("Trigger", GLFW.GLFW_MOUSE_BUTTON_1).onPress {
        trigger()
    }

    val actionEntries by lazy { typedEntries<P3Action>() }

    private val colourDropdown by text("Colours").visibleIf { multicolour && renderMode.selected != "Silent" }
    private val colours = actionEntries.associate { (name, action) ->
        val set = colourPicker(name, action().colour).childOf(::colourDropdown)
        this.register(set)
        name to set
    }
    private val fillColourDropdown by text("Fill colours").visibleIf { (renderMode.selected == "Filled" || renderMode.selected == "Flat" || renderMode.selected == "Nodes") && multicolour }
    private val fillColours = actionEntries.associate { (name, action) ->
        val set = colourPicker(name, action().colour.withAlpha(0.5f), allowAlpha = true).json("$name fill").childOf(::fillColourDropdown)
        this.register(set)
        name to set
    }
    private val thickness by slider("Thickness", 4f, 1f, 8f, 0.5f).visibleIf { renderMode.selected != "Silent" }
    val height by slider("Height", 0.1f, 0.1f, 1f, 0.1f).visibleIf { renderMode.selected != "Silent" }
    private val depth by switch("Depth", true)
    private val feedback by switch("Feedback")

    private val autop3Folder = File(configPath, "autop3").apply { mkdirs() }
    private val currentConfigFile = File(autop3Folder, ".current")
    var currentConfig = currentConfigFile.takeIf { it.exists() }?.readText()?.trim() ?: "default"
        internal set(value) {
            field = value
            currentConfigFile.writeText(value)
        }
    
    private fun configFile(name: String) = File(autop3Folder, "$name.json")
    
    private val loadedRings = ConfigSystem.load(configFile(currentConfig)) { mutableListOf<P3Ring>() }
    val rings = ConfigList(loadedRings, {
        ConfigSystem.save(configFile(currentConfig), loadedRings)
    }, {
        val new = ConfigSystem.load(configFile(currentConfig)) { mutableListOf<P3Ring>() }
        loadedRings.clear()
        loadedRings.addAll(new)
    })

    var editMode = false
        internal set
    
    @JvmStatic
    var desync = false
        private set
    
    private var lastDesync = false
    
    var edgeActive = false
        internal set

    internal val activeRings = mutableListOf<P3Ring>()
    private var currentJob: Job? = null
    val undoStack = mutableListOf<List<Pair<Int, P3Ring>>>()

    internal val cmd = BaseCommand("p3", "ap3")
    internal val add = cmd.sub("add").description("Adds a ring.").suggests("add", actionEntries.map { it.first })

    init {
        registerCommands()

        on<TickEvent.End> {
            if (!desync && lastDesync && player != null) {
                player.yRot = ClientRotationHandler.clientYaw
                player.xRot = ClientRotationHandler.clientPitch
            }
            lastDesync = desync
        }

        on<TickEvent.Start> {
            ClientRotationHandler.tick()
            
            if (!enabled || editMode || mc.player == null) return@on
            
            desync = false
            
            val playerPos = player.position()
            val oldPos = player.oldPosition()
            
            val sorted = rings.filter { it.updateState(playerPos, oldPos) && (activeRings.isEmpty() || activeRings.all { active -> it.action.priority >= active.action.priority }) }
                .sortedByDescending { it.action.priority }
            
            if (sorted.isEmpty()) return@on
            
            activeRings.removeIf { !it.active }
            val temp = mutableListOf<P3Ring>()
            var stop = false
            
            for (ring in sorted) {
                temp.add(ring)
                if (ring.checkTriggerArg()) continue
                if (ring.action.isStop()) stop = true
                ring.setTriggered()
                ring.setActive()
                if (feedback) {
                    val msg = ring.action.feedbackMessage()
                    if (msg.isNotEmpty()) modMessage(msg)
                }
                
                currentJob = scope.launch {
                    runCatching {
                        ring.delay?.let {
                            delay(it.toLong())
                            if (!ring.isInNode(player.position(), player.oldPosition())) return@launch
                        }
                        ring.action.execute(player)
                        ring.subActions.forEach { it.execute(player) }
                    }.onFailure { error ->
                        if (error is CancellationException) return@launch
                        modMessage(
                            ChatUtils.button(
                                "&cError executing ring &e${ring.action.typeName} &7(click to copy)",
                                command = "/quoidev copy ${error.stackTraceToString()}",
                                hoverText = "Click to copy"
                            )
                        )
                        error.printStackTrace()
                    }
                }
                
                if (!ring.action.execute()) break
            }
            
            if (stop) {
                activeRings.removeIf { ring ->
                    if (ring.action.shouldStop()) {
                        ring.setInactive()
                        true
                    } else false
                }
            }
            
            for (newRing in temp) {
                if (newRing.action is quoi.module.impl.dungeon.autop3.rings.WalkAction) {
                    activeRings.removeIf { it.action is quoi.module.impl.dungeon.autop3.rings.WalkAction && it != newRing }
                }
            }
            
            activeRings.addAll(temp.filter { it !in activeRings })
        }
        
        on<KeyEvent.Input> {
            if (!enabled || editMode) return@on
            
            if (activeRings.isEmpty()) {
                desync = false
                return@on
            }
            
            for (i in activeRings.indices.reversed()) {
                val ring = activeRings[i]
                if (!ring.active) continue
                
                val shouldRemove = ring.action.tick(player, clientInput, input)
                ring.subActions.forEach { it.tick(player, clientInput, input) }
                
                if (shouldRemove) {
                    ring.setInactive()
                    activeRings.removeAt(i)
                }
            }
        }
        
        on<RenderEvent.World> {
            if (!enabled && !editMode) return@on
            if (renderMode.selected == "Silent") return@on

            rings.forEach { ring ->
                val col = if (ring.active) activeCol else ring.colour()
                val fillCol = ring.fillColour()
                ring.render(ctx, col, fillCol, renderMode.selected, thickness, height, depth)
            }
        }

        on<RenderEvent.Overlay> {
            if (!editMode) return@on
            val text = "Edit mode"
            val x = (scaledWidth - text.noControlCodes.width()) / 2f
            val y = scaledHeight / 2f + 10
            ctx.drawText(text, x, y)
        }

        on<WorldEvent.Change> {
            activeRings.clear()
            currentJob?.cancel()
            rings.forEach { it.reset() }
        }
        
        on<PacketEvent.Received> {
            when (packet) {
                is net.minecraft.network.protocol.game.ClientboundOpenScreenPacket -> {
                    val title = packet.title.string
                    if (quoi.api.skyblock.dungeon.Dungeon.terminalTitles.any { title.contains(it) }) {
                        consumeTerm()
                    }
                }
                is net.minecraft.network.protocol.game.ClientboundContainerClosePacket -> {
                    if (quoi.api.skyblock.dungeon.Dungeon.inTerminal) {
                        consumeTermClose()
                    }
                }
            }
        }
        
        on<PacketEvent.Sent> {
            when (packet) {
                is net.minecraft.network.protocol.game.ServerboundContainerClosePacket -> {
                    if (quoi.api.skyblock.dungeon.Dungeon.inTerminal) {
                        consumeTermClose()
                    }
                }
            }
        }
        
        on<KeyEvent.Press> {
            if (key == mc.options.keyUp.key.value || key == mc.options.keyDown.key.value || 
                key == mc.options.keyLeft.key.value || key == mc.options.keyRight.key.value) {
                activeRings.removeIf { ring ->
                    if (ring.action is quoi.module.impl.dungeon.autop3.rings.WalkAction) {
                        ring.setInactive()
                        true
                    } else false
                }
            }
        }
        
        on<KeyEvent.Input> {
            if (!edgeActive || player == null || !player.onGround() || mc.options.keyJump.isDown) return@on
            if (player.isShiftKeyDown || mc.options.keyShift.isDown) return@on
            
            val dist = 0.001
            val box = player.boundingBox
            val adjustedBox = box.move(0.0, -0.5, 0.0).inflate(-dist, 0.0, -dist)
            val blockCollisions = mc.level?.getBlockCollisions(player, adjustedBox)
            
            if (blockCollisions?.iterator()?.hasNext() == true) return@on
            
            edgeActive = false
            input.jump = true
        }
        
        onToggle(true)
    }
    
    override fun isClientRotationActive() = enabled && desync
    
    override fun allowClientKeyInputs() = true
    
    private fun onDesyncEnable() {
        ClientRotationHandler.registerProvider(this)
        if (player != null) {
            ClientRotationHandler.setYaw(player.yRot)
        }
    }
    
    fun setDesync(bl: Boolean) {
        if (bl && !desync && !lastDesync) onDesyncEnable()
        desync = bl
    }
    
    private fun trigger() {
        if (mc.player == null) return
        val playerPos = player.position()
        val oldPos = player.oldPosition()
        rings.filter { it.isInNode(playerPos, oldPos) }.forEach { 
            it.consumeTrigger()
            it.consumeTermClose()
        }
        if (!quoi.api.skyblock.dungeon.Dungeon.inTerminal) {
            rings.filter { it.isInNode(playerPos, oldPos) }.forEach { 
                it.consumeTerm()
            }
        }
    }
    
    private fun consumeTerm() {
        if (mc.player == null) return
        val playerPos = player.position()
        val oldPos = player.oldPosition()
        rings.filter { it.isInNode(playerPos, oldPos) }.forEach { it.consumeTerm() }
    }
    
    private fun consumeTermClose() {
        if (mc.player == null) return
        val playerPos = player.position()
        val oldPos = player.oldPosition()
        rings.filter { it.isInNode(playerPos, oldPos) }.forEach { it.consumeTermClose() }
    }

    private fun P3Ring.colour() = if (multicolour) colours[this.action.typeName]?.value ?: Colour.WHITE else colour
    private fun P3Ring.fillColour() = if (multicolour) fillColours[this.action.typeName]?.value ?: Colour.WHITE else fillColour

    internal val currentYaw get() = player.yaw
    internal val currentPitch get() = player.pitch
    internal val currentSlot get() = player.inventory.selectedSlot
    
    internal fun loadConfig(name: String) {
        try {
            currentConfig = name
            rings.reload()
            modMessage("Loaded &e${rings.size}&r rings from &e$name&r")
            activeRings.clear()
            currentJob?.cancel()
        } catch (e: Exception) {
            modMessage("&cFailed to load config &e$name&c: ${e.message}")
            e.printStackTrace()
        }
    }
    
    internal fun listConfigs(): List<String> {
        return autop3Folder.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
}
