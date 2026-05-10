package kr.toxicity.hud.renderer

import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.update.UpdateEvent
import kr.toxicity.hud.image.ImageComponent
import kr.toxicity.hud.layout.ImageLayout
import kr.toxicity.hud.manager.PlaceholderManagerImpl
import kr.toxicity.hud.manager.PlayerManagerImpl
import kr.toxicity.hud.util.*
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

class ImageRenderer(
    layout: ImageLayout,
    component: ImageComponent
) : ImageLayout by layout, HudRenderer {
    private val followHudPlayer = follow?.let {
        PlaceholderManagerImpl.find(it, this).assertString("This placeholder is not a string: $it")
    }
    private val component = component.apply(outline, color)

    override fun render(event: UpdateEvent): TickProvider<HudPlayer, PixelComponent> {
        val cond = conditions build event
        val listen = component.listener(event)
        val follow = followHudPlayer?.build(event)

        val stackGetter = stack?.build(event)
        val maxStackGetter = maxStack?.build(event)

        val mapper = component mapper event
        val colorApply = colorOverrides(event)

        // Maps pour garder l'état local de visibilité pour chaque joueur
        // Le WeakHashMap évite les fuites de mémoire (memory leaks) à la déconnexion
        val startTicks = WeakHashMap<HudPlayer, Long>()
        val wasVisible = WeakHashMap<HudPlayer, Boolean>()

        return tickProvide(tick) build@ { player, frame ->
            val selected = mapper(player)

            val stackFrame = (stackGetter?.value(player) as? Number)?.toDouble() ?: 0.0
            val maxStackFrame = (maxStackGetter?.value(player) as? Number)?.toInt()?.coerceAtLeast(1) ?: ceil(stackFrame).toInt()

            var target = player
            follow?.let {
                PlayerManagerImpl.getHudPlayer(it.value(player).toString())?.let { p ->
                    target = p
                } ?: run {
                    if (cancelIfFollowerNotExists) return@build EMPTY_PIXEL_COMPONENT
                }
            }

            val isVisible = cond(target)

            if (isVisible) {
                val lastVisible = wasVisible[target] ?: false

                // Si ce n'était pas visible au tick précédent, c'est une nouvelle apparition !
                if (!lastVisible) {
                    startTicks[target] = frame
                }
                wasVisible[target] = true

                // Calcul du tick local (Démarre à 0 au moment de l'apparition)
                val startTick = startTicks[target] ?: frame
                val localFrame = frame - startTick

                if (maxStackFrame > 1) {
                    if (stackFrame <= 0.0) return@build EMPTY_PIXEL_COMPONENT
                    var empty = EMPTY_PIXEL_COMPONENT
                    val range = 0..<maxStackFrame
                    for (i in if (reversed) range.reversed() else range) {
                        // Renommé en stackIndex pour éviter le conflit avec le frame global
                        val stackIndex = ((stackFrame - i - 0.1) * selected.images.size)
                            .roundToInt()
                            .coerceAtLeast(0)
                            .coerceAtMost(selected.images.lastIndex)
                        empty = empty.append(space, selected.images[stackIndex])
                    }
                    empty.applyColor(colorApply(target))
                } else {
                    // On injecte le localFrame au lieu du global frame pour réparer le PLAY_ONCE
                    component.type.getComponent(listen, localFrame, selected, target).applyColor(colorApply(target))
                }
            } else {
                wasVisible[target] = false // Réinitialise l'état quand l'image disparait
                if (clearListener) listen.clear(player)
                EMPTY_PIXEL_COMPONENT
            }
        }
    }

    fun max() = component.max
}