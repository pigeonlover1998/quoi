package quoi.api.abobaui.elements.impl

import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.elements.Element
import quoi.utils.ui.data.Radii
import quoi.utils.ui.rendering.Image
import quoi.utils.ui.rendering.NVGRenderer

class ImageElement(
    private val image: Image,
    constraints: Constraints,
    radius: Radii?,
) : Element(constraints) {

    private val radius: Radii = radius ?: Block.EMPTY_RADIUS

    init {
//        registerEvent(Lifetime.Initialised) {
//            NVGRenderer.createImage(image)
//            false
//        }
//        registerEvent(Lifetime.Uninitialised) {
//            NVGRenderer.deleteImage(image)
//            false
//        }
    }

    override fun draw() {
        NVGRenderer.image(image, x, y, width, height, radius)
    }
}

class GlImageElement(
    private val textureId: Int,
    constraints: Constraints,
    radius: Radii?,
) : Element(constraints) {

    private val radius: Radii = radius ?: Block.EMPTY_RADIUS
    private val nvgImage: Int = 0

    init {
        NVGRenderer.createNVGImage(textureId, width.toInt(), height.toInt())
    }

    override fun draw() {
        NVGRenderer.image(nvgImage, width, height, 0f, 0f, width, height, x, y, width, height, radius.topLeft)
    }
}