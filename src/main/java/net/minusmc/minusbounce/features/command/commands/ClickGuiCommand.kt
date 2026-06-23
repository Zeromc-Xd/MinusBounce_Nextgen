package net.minusmc.minusbounce.features.command.commands

import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.features.command.Command
import net.minusmc.minusbounce.features.module.modules.client.ClickGUI

class ClickGuiCommand : Command("clickgui", arrayOf("cg")) {
    private fun saveClickGuiValues() {
        MinusBounce.fileManager.saveConfig(MinusBounce.fileManager.valuesConfig)
    }

    override fun execute(args: Array<String>) {
        if (args.size < 2) {
            chatSyntax("clickgui <style/scale> <value>")
            return
        }
        when (args[1].lowercase()) {
            "style" -> {
                if (args.size < 3) {
                    chatSyntax("clickgui style <rise/minus>")
                    return
                }
                when (args[2].lowercase()) {
                    "rise" -> {
                        ClickGUI.styleValue.set("Rise")
                        saveClickGuiValues()
                        ClickGUI.reloadOpenGui()
                        chat("ClickGUI style set to rise.")
                        playEdit()
                    }
                    "minus" -> {
                        ClickGUI.styleValue.set("Minus")
                        saveClickGuiValues()
                        ClickGUI.reloadOpenGui()
                        chat("ClickGUI style set to minus.")
                        playEdit()
                    }
                    else -> chatSyntax("clickgui style <rise/minus>")
                }
            }
            "scale" -> {
                if (args.size < 3) {
                    chatSyntax("clickgui scale <0.5-2.0>")
                    return
                }
                when (args[2].lowercase()) {
                    "rise" -> {
                        ClickGUI.styleValue.set("Rise")
                        saveClickGuiValues()
                        ClickGUI.reloadOpenGui()
                        chat("ClickGUI style set to rise.")
                        playEdit()
                        return
                    }
                    "minus" -> {
                        ClickGUI.styleValue.set("Minus")
                        saveClickGuiValues()
                        ClickGUI.reloadOpenGui()
                        chat("ClickGUI style set to minus.")
                        playEdit()
                        return
                    }
                }
                val scale = args[2].toFloatOrNull()
                if (scale == null) {
                    chatSyntax("clickgui scale <0.5-2.0>")
                    return
                }
                ClickGUI.scaleValue.set(scale.coerceIn(0.5f, 2.0f))
                saveClickGuiValues()
                ClickGUI.reloadOpenGui()
                chat("Rise ClickGUI scale set to ${ClickGUI.scaleValue.get()}.")
                playEdit()
            }
            else -> chatSyntax("clickgui <style/scale> <value>")
        }
    }

    override fun tabComplete(args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("style", "scale").filter { it.startsWith(args[0], true) }
            2 -> when {
                args[0].equals("style", true) -> listOf("rise", "minus").filter { it.startsWith(args[1], true) }
                args[0].equals("scale", true) -> listOf("0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "rise", "minus").filter { it.startsWith(args[1], true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
