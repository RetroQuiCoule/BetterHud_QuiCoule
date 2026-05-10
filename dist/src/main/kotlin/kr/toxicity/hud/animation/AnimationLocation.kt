package kr.toxicity.hud.animation

import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.equation.EquationTriple
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.util.getAsAnimationType

data class AnimationLocation(
    val type: AnimationType,
    val location: List<PixelLocation>
) {
    companion object {
        val zero = AnimationLocation(AnimationType.LOOP, listOf(PixelLocation.zero))
    }
    constructor(
        type: AnimationType,
        duration: Int,
        imageEquation: EquationTriple
    ): this(
        type,
        (0..<duration).map {
            val d = it.toDouble()
            PixelLocation(
                imageEquation.x evaluateToInt d,
                imageEquation.y evaluateToInt d,
                imageEquation.opacity evaluate d
            )
        }
    )

    constructor(section: YamlObject): this(
        section.getAsAnimationType("type"),
        section.getAsInt("duration", 20).coerceAtLeast(1).let { duration ->

            // Interception : Mode Translation Linéaire (Lerp) si les variables existent
            if (section["start-x"] != null || section["end-x"] != null || section["start-y"] != null || section["end-y"] != null) {
                val startX = section.getAsDouble("start-x", 0.0).toFloat()
                val startY = section.getAsDouble("start-y", 0.0).toFloat()
                val endX = section.getAsDouble("end-x", 0.0).toFloat()
                val endY = section.getAsDouble("end-y", 0.0).toFloat()
                val opacity = section.getAsDouble("opacity", 1.0)

                (0..<duration).map { d ->
                    // Calcul du pourcentage de progression
                    val progress = if (duration <= 1) 1f else d.toFloat() / (duration - 1).toFloat()
                    PixelLocation(
                        (startX + (endX - startX) * progress).toInt(),
                        (startY + (endY - startY) * progress).toInt(),
                        opacity
                    )
                }
            } else {
                // Fonctionnement classique de BetterHud (Équations)
                val imageEquation = EquationTriple(section)
                (0..<duration).map {
                    val d = it.toDouble()
                    PixelLocation(
                        imageEquation.x evaluateToInt d,
                        imageEquation.y evaluateToInt d,
                        imageEquation.opacity evaluate d
                    )
                }
            }
        }
    )
}