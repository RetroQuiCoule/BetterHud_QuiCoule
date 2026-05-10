package kr.toxicity.hud.hud

import com.google.gson.JsonArray
import kr.toxicity.hud.api.configuration.HudComponentSupplier
import kr.toxicity.hud.api.configuration.HudObjectType
import kr.toxicity.hud.api.hud.Hud
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.update.UpdateEvent
import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.configuration.HudConfiguration
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.animation.AnimationType
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.manager.LayoutManager
import kr.toxicity.hud.pack.PackGenerator
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.manager.EncodeManager
import kr.toxicity.hud.placeholder.PlaceholderSource
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.util.*
import kr.toxicity.hud.layout.LayoutGroup // <-- Nouvel import ajouté ici !

class HudImpl(
    override val id: String,
    resource: GlobalResource,
    section: YamlObject
) : Hud, HudConfiguration, PlaceholderSource by PlaceholderSource.Impl(section) {

    private var imageChar = 0xCE000

    val newChar
        get() = (++imageChar).parseChar()

    private val imageEncoded = "hud_${id}_image".encodeKey(EncodeManager.EncodeNamespace.FONT)
    val imageKey = createAdventureKey(imageEncoded)
    var jsonArray: JsonArray? = JsonArray()
    private val spaces = intKeyMapOf<String>()
    private val default = ConfigManagerImpl.defaultHud.contains(id) || section.getAsBoolean("default", false)
    var textIndex = 0
    private val tick = section.getAsLong("tick", 1)

    fun getOrCreateSpace(int: Int): String = spaces.computeIfAbsent(int) {
        newChar
    }

    private val elements = section["layouts"]?.asObject().ifNull { "layout configuration not set." }.mapSubConfiguration { s, yamlObject ->
        val layout = yamlObject["name"]?.asString().ifNull { "name value not set: $s" }.let {
            LayoutManager.getLayout(it).ifNull { "this layout doesn't exist: $it" }
        }
        var gui = GuiLocation(yamlObject)
        yamlObject["gui"]?.asObject()?.let {
            gui += GuiLocation(it)
        }
        val pixel = yamlObject["pixel"]?.asObject()?.let {
            PixelLocation(it)
        }  ?: PixelLocation.zero
        HudAnimation(
            layout, // On injecte le layout pour accéder à ses conditions
            layout.animation.type,
            layout.animation.location.map {
                HudParser(
                    this@HudImpl,
                    resource,
                    layout,
                    gui,
                    it + pixel
                )
            }
        )
    }.ifEmpty {
        throw RuntimeException("layout is empty.")
    }
    init {
        jsonArray?.let { array ->
            if (spaces.isNotEmpty()) array += jsonObjectOf(
                "type" to "space",
                "advances" to jsonObjectOf(*spaces.map {
                    it.value to it.key
                }.toTypedArray())
            )
            PackGenerator.addTask(resource.font + "$imageEncoded.json") {
                jsonObjectOf("providers" to array).toByteArray()
            }
        }
        jsonArray = null
    }

    override fun getType(): HudObjectType<*> {
        return HudObjectType.HUD
    }

    override fun tick(): Long = tick

    private val conditions = section.toConditions(this) build UpdateEvent.EMPTY

    override fun createRenderer(player: HudPlayer): HudComponentSupplier<Hud> {
        // Création des états isolés pour chaque Layout du HUD
        val layoutEvaluators = elements.map { hudAnim ->
            val layoutConds = hudAnim.layout.conditions build UpdateEvent.EMPTY
            val evaluators = hudAnim.elements.map { p ->
                runByTick(tick, { player.tick }, p.getComponent(player))
            }

            var startTick = -1L
            var wasVisible = false

            // Retourne une fonction qui gère sa propre visibilité
            {
                val isLayoutVisible = layoutConds(player)
                if (isLayoutVisible) {
                    if (!wasVisible) {
                        startTick = player.tick
                    }
                    wasVisible = true
                    val localTick = if (startTick >= 0L) player.tick - startTick else 0L

                    hudAnim.animationType.choose(evaluators, localTick)()
                } else {
                    wasVisible = false
                    null // Ignore ce layout s'il n'est pas censé être visible
                }
            }
        }

        return HudComponentSupplier.of(this) {
            if (conditions(player)) {
                // Exécute tous les layouts et filtre ceux qui sont cachés (null)
                layoutEvaluators.mapNotNull { it() }
            } else {
                emptyList()
            }
        }
    }

    override fun getName(): String = id
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HudImpl

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun isDefault(): Boolean = default

    private class HudAnimation(
        val layout: LayoutGroup,
        val animationType: AnimationType,
        val elements: List<HudParser>
    )
}