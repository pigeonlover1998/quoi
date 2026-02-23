package quoi.utils.ui.data

enum class Anchor(val x: Float, val y: Float) {
    TopLeft(0f, 0f), TopRight(1f, 0f),
    BottomLeft(0f, 1f), BottomRight(1f, 1f),
    Centre(0.5f, 0.5f);
}