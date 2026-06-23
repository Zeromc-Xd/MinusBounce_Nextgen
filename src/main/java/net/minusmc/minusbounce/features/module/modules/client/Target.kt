package net.minusmc.minusbounce.features.module.modules.client

import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.value.BoolValue

@ModuleInfo(name = "Target", description = "Select target to KillAura", category = ModuleCategory.CLIENT, onlyEnable = true)
class Target: Module(){
    val players = BoolValue("Players", true)
    val mobs = BoolValue("Mobs", true)
    val ageable = BoolValue("Ageable", false)
    val animals = BoolValue("Animals", true)
    val invisible = BoolValue("Invisible", true)
    val friend = BoolValue("Friend", false)
    val dead = BoolValue("Dead", true)

    override fun handleEvents(): Boolean = true
}