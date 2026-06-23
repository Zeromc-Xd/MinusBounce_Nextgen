package net.minusmc.minusbounce.ui.client.clickgui.rise.render

class RiseAnimation(var easing: RiseEasing = RiseEasing.LINEAR, var duration: Long = 300L) {
    private var value = 0.0
    private var startValue = 0.0
    private var destinationValue = 0.0
    private var startTime = System.currentTimeMillis()

    fun reset(newValue: Double = 0.0) {
        value = newValue
        startValue = newValue
        destinationValue = newValue
        startTime = System.currentTimeMillis()
    }

    fun run(target: Double): Double {
        val now = System.currentTimeMillis()
        if (destinationValue != target) {
            destinationValue = target
            startValue = value
            startTime = now
        }
        val progress = ((now - startTime).toDouble() / duration.coerceAtLeast(1L).toDouble()).coerceIn(0.0, 1.0)
        val eased = easing.transform(progress)
        value = startValue + (destinationValue - startValue) * eased
        if (progress >= 1.0) value = destinationValue
        return value
    }

    fun getValue() = value
}
