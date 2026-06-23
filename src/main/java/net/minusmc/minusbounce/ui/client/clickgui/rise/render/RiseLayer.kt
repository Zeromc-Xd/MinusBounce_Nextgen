package net.minusmc.minusbounce.ui.client.clickgui.rise.render

class RiseLayer(private val strength: Float = 12f) {
    private val tasks = arrayListOf<() -> Unit>()

    fun add(task: () -> Unit) {
        tasks += task
    }

    fun run() {
        if (tasks.isEmpty()) return
        RiseRenderUtil.bloom(strength, { tasks.forEach { it() } }, { tasks.forEach { it() } })
    }

    fun clear() {
        tasks.clear()
    }
}
