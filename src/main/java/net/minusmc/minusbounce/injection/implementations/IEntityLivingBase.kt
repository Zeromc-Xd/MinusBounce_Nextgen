package net.minusmc.minusbounce.injection.implementations

import net.minecraft.util.Vec3

interface IEntityLivingBase {
  var realPosX: Double
  var realPosY: Double
  var realPosZ: Double

  var serverPosition: Vec3
}
