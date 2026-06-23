package net.minusmc.minusbounce.utils.movement

enum class MovementFixType(val correctInput: Boolean, val correctYaw: Boolean) {
    NONE(false, false),
    NORMAL(false, true),
    FULL(true, true)
}
