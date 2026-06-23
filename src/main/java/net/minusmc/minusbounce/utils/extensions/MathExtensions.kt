/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 * 
 * This code is from CCBlueX/LiquidBounce. Please credit them when using this code in your repository.
 */

package net.minusmc.minusbounce.utils.extensions

import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import net.minecraft.util.Vec3i
import net.minusmc.minusbounce.utils.Rotation

/**
 * Provides:
 * ```
 * val (x, y, z) = blockPos
 */
operator fun Vec3i.component1() = x
operator fun Vec3i.component2() = y
operator fun Vec3i.component3() = z

/**
 * Provides:
 * ```
 * val (x, y, z) = vec
 */
operator fun Vec3.component1() = xCoord
operator fun Vec3.component2() = yCoord
operator fun Vec3.component3() = zCoord

/**
 * Provides:
 * ```
 * val (x, y, z) = mc.thePlayer
 */
operator fun Entity.component1() = posX
operator fun Entity.component2() = posY
operator fun Entity.component3() = posZ

/**
 * Provides:
 * ```
 * val (width, height) = ScaledResolution(mc)
 */
operator fun ScaledResolution.component1() = this.scaledWidth
operator fun ScaledResolution.component2() = this.scaledHeight

/**
 * Provides:
 * ```
 * val (yaw, pitch) = Rotation
 */
operator fun Rotation.component1(): Float = this.yaw
operator fun Rotation.component2(): Float = this.pitch

/**
 * Provides
 * ```
 * sin(Float), cos(Float), tan(Float) = Double
 *
 * sin(Deg), cos(Deg), tan(Deg) = Rad (Double)
 */
fun sinD(n: Float): Double = kotlin.math.sin(n).toDouble()
fun cosD(n: Float): Double = kotlin.math.cos(n).toDouble()
fun tanD(n: Float): Double = kotlin.math.tan(n).toDouble()

fun sinR(n: Float): Double = kotlin.math.sin(n.toRadiansD())
fun cosR(n: Float): Double = kotlin.math.cos(n.toRadiansD())
fun tanR(n: Float): Double = kotlin.math.tan(n.toRadiansD())

/**
 * Provides:
 * `vec + othervec`, `vec - othervec`, `vec * number`, `vec / number`
 * */
operator fun Vec3.plus(num: Double): Vec3 = add(Vec3(num, num, num))
fun Vec3.plus(x: Double, y: Double, z: Double): Vec3 = add(Vec3(x, y, z))
operator fun Vec3.plus(vec: Vec3): Vec3 = add(vec)
operator fun Vec3.minus(vec: Vec3): Vec3 = subtract(vec)
operator fun Vec3.minus(vec: BlockPos): Vec3 = subtract(Vec3(vec))
operator fun Vec3.times(vec: Vec3): Vec3 = Vec3(xCoord * vec.xCoord, yCoord * vec.yCoord, zCoord * vec.zCoord)
operator fun Vec3.times(number: Double) = Vec3(xCoord * number, yCoord * number, zCoord * number)
operator fun Vec3.div(number: Double) = times(1 / number)

operator fun Vec3i.plus(n: Double) = Vec3i(x + n, y + n, z + n)
operator fun Vec3i.times(n: Double) = Vec3i(x * n, y * n, z * n)

fun EntityPlayer.getDistance(blockPos: Vec3i) = getDistance(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
fun Vec3.toFloatTriple() = Triple(xCoord.toFloat(), yCoord.toFloat(), zCoord.toFloat())
fun AxisAlignedBB.expand(d: Double): AxisAlignedBB = expand(d, d, d)
operator fun BlockPos.plus(i: Int): BlockPos = add(i, i, i)

fun Float.toRadians() = this * 0.01745329251f
fun Float.toRadiansD() = toRadians().toDouble()
fun Float.toDegrees() = this * 57.29578f
fun Float.toDegreesD() = toDegrees().toDouble()

fun Double.toRadians() = this * 0.017453292
fun Double.toRadiansF() = toRadians().toFloat()
fun Double.toDegrees() = this * 57.295779513
fun Double.toDegreesF() = toDegrees().toFloat()

/**
 * Provides: (step is 0.1 by default)
 * ```
 *      for (x in 0.1..0.9 step 0.05) {}
 *      for (y in 0.1..0.9) {}
 */
class RangeIterator(private val range: ClosedFloatingPointRange<Double>, private val step: Double = 0.1): Iterator<Double> {
	private var value = range.start

	override fun hasNext() = value < range.endInclusive

	override fun next(): Double {
		val returned = value
		value = (value + step).coerceAtMost(range.endInclusive)
		return returned
	}
}
operator fun ClosedFloatingPointRange<Double>.iterator() = RangeIterator(this)
infix fun ClosedFloatingPointRange<Double>.step(step: Double) = RangeIterator(this, step)

fun ClosedFloatingPointRange<Float>.random(): Double {
	require(start.isFinite())
	require(endInclusive.isFinite())
	return start + (endInclusive - start) * Math.random()
}
