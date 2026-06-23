package net.minusmc.minusbounce.utils.click

import com.google.common.collect.Lists
import net.minusmc.minusbounce.event.ClickingEvent
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.Listenable
import net.minusmc.minusbounce.utils.click.MathUtil.getAverage
import net.minusmc.minusbounce.utils.click.MathUtil.getCps
import net.minusmc.minusbounce.utils.click.MathUtil.getDistinct
import net.minusmc.minusbounce.utils.click.MathUtil.getKurtosis
import net.minusmc.minusbounce.utils.click.MathUtil.getSkewness
import net.minusmc.minusbounce.utils.click.MathUtil.getStandardDeviation
import net.minusmc.minusbounce.utils.click.MathUtil.getVariance
import net.minusmc.minusbounce.utils.particles.EvictingList
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt


object Config {
    val AUTOCLICKER_A_MAX_CPS = 17.0..25.0
    const val AUTOCLICKER_T_MIN_KURTOSIS = 13.0
}

/**
 * fmcpe
 *
 * 7/17/24
 * ClickHandle. Handle ClickingEvent. Bring legit clicking pattern into client
 */
object ClickHandle: Listenable {
    val clickProcessor = ClickProcessor()

    /***
     * Handle checks
     */
    @EventTarget
    fun onUpdate(event: ClickingEvent){
        clickProcessor.handleArmAnimation()

        if(!canClick()) {
            event.cancelEvent()
        }
    }

    private fun canClick(): Boolean{
        return AutoClickA.handle()
            && AutoClickB.handle()
            && AutoClickC.handle()
            && AutoClickD.handle()
            && AutoClickE.handle()
            && AutoClickF.handle()
            && AutoClickG.handle()
            && AutoClickH.handle()
            && AutoClickI.handle()
            && AutoClickJ.handle()
            && AutoClickK.handle()
            && AutoClickL.handle()
            && AutoClickM.handle()
            && AutoClickN.handle()
            && AutoClickO.handle()
            && AutoClickP.handle()
            && AutoClickQ.handle()
            && AutoClickR.handle()
            && AutoClickS.handle()
            && AutoClickT.handle()
    }

    override fun handleEvents() = true
}

/***
 * AutoClickA: "Left-clicking too quickly."
 */
object AutoClickA {
    private val samples = Lists.newLinkedList<Long>()
    private var lastSwing = 0L

    fun handle(): Boolean {
        val now = System.currentTimeMillis()
        val delay = now - lastSwing
        samples.add(delay)
        if (samples.size == 20) {
            val cps = getCps(samples)
            if (cps in Config.AUTOCLICKER_A_MAX_CPS && cps > 5.0) {
                return false
            }
            samples.clear()
        }
        lastSwing = now

        return true
    }
}

/***
 * AutoClickB: "Too low standard deviation."
 */
object AutoClickB {
    private val samples = Lists.newLinkedList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }

        samples.add(delay)
        if (samples.size == 50) {
            if (getStandardDeviation(samples) < 167.0) {
                return false
            }
            samples.clear()
        }

        return true
    }
}

/***
 * AutoClickC: "Rounded CPS values."
 */
object AutoClickC {
    private val samples = Lists.newLinkedList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }

        samples.add(delay)
        if (samples.size == 20) {
            val cps = getCps(samples)
            val difference = abs(Math.round(cps) - cps)
            if (difference < 0.08) {
                return false
            }
            samples.clear()
        }

        return true
    }
}

/***
 * AutoClickD: "Too low skewness values."
 */
object AutoClickD {
    private val samples = Lists.newLinkedList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }

        samples.add(delay)
        if (samples.size == 50) {
            val skewness = getSkewness(samples)
            if (skewness < -0.01) {
                return false
            }
            samples.clear()
        }

        return true
    }
}

/***
 * AutoClickE: "Too low variance."
 */
object AutoClickE {
    private val samples = Lists.newLinkedList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 50) {
            val variance = getVariance(samples)
            val scaled = variance / 1000.0
            if (scaled < 28.2) {
                return false
            }
            samples.clear()
        }

        return true
    }
}

/**
 * AutoClickF: "Not enough distinct values."
 */
object AutoClickF {
    private val samples = Lists.newLinkedList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 50) {
            val distinct = getDistinct(samples)
            if (distinct < 13) {
                return false
            }
            samples.clear()
        }

        return true
    }
}

/**
 * AutoClickG: "Too low outliers."
 */
object AutoClickG {
    private val samples = Lists.newLinkedList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 50) {
            val outliers = samples.stream().filter { l -> l > 150L }.count()
            if (outliers < 3) {
                return false
            }
            samples.clear()
        }

        return true
    }
}

/**
 * AutoClickH: "Similar deviation values."
 */
object AutoClickH {
    private val samples = Lists.newLinkedList<Long>()
    private var lastDeviation = 0.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 20) {
            val deviation = getStandardDeviation(samples)
            val difference = abs(deviation - lastDeviation)
            if (difference < 7.52) {
                return false
            }
            lastDeviation = deviation
            samples.clear()
        }

        return true
    }
}

/**
 * AutoClickI: "Too low kurtosis."
 */
object AutoClickI {
    private val samples = Lists.newLinkedList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 50) {
            val kurtosis = getKurtosis(samples)
            val scaled = kurtosis / 1000.0
            if (scaled < 41.78) {
                return false
            }
            samples.clear()
        }
        return true
    }
}

/**
 * AutoClickJ: "Impossible consistency."
 */
object AutoClickJ {
    private val samples = Lists.newArrayList<Long>()

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 10) {
            samples.sort()
            val range: Long = samples[samples.size - 1] - samples[0]
            if (range < 50L) {
                return false
            }
            samples.clear()
        }
        return true
    }
}

/**
 * AutoClickK: "Similar average values."
 */
object AutoClickK {
    private val samples = Lists.newLinkedList<Long>()
    private var lastAverage = 0.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 50) {
            val average = getAverage(samples)
            val difference: Double = abs(average - lastAverage)
            if (difference < 2.56) {
                return false
            }
            lastAverage = average
            samples.clear()
        }

        return true
    }
}

/**
 * AutoClickL: "Similar kurtosis values."
 */
object AutoClickL {
    private val samples = Lists.newLinkedList<Long>()
    private var lastKurtosis = 0.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 50) {
            val kurtosis = getKurtosis(samples) / 1000.0
            if (abs(kurtosis - lastKurtosis) < 8.35) {
                return false
            }
            lastKurtosis = kurtosis
            samples.clear()
        }
        return true
    }
}

/**
 * AutoClickM: "Similar variance values."
 */
object AutoClickM {
    private val samples = Lists.newLinkedList<Long>()
    private var lastVariance = 0.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 50) {
            val variance = getVariance(samples) / 1000.0
            if (abs(variance - lastVariance) < 5.28) {
                return false
            }
            lastVariance = variance
            samples.clear()
        }
        return true
    }
}

/**
 * AutoClickN: "Low deviation difference."
 */
object AutoClickN {
    private val samples = EvictingList<Long>(25)
    private var lastDeviation = 0.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.isFull) {
            val deviation = getStandardDeviation(samples)
            val difference: Double = abs(deviation - lastDeviation)
            val average: Double = abs(deviation + lastDeviation) / 2.0
            if (difference < 0.25 && average < 150.0) {
                return false
            }
            lastDeviation = deviation
        }

        return true
    }
}

/**
 * AutoClickO: "Impossible spike in CPS."
 */
object AutoClickO {
    private val samples = Lists.newLinkedList<Long>()
    private var lastCps = -1.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 10) {
            val cps = getCps(samples)
            if (lastCps > 0.0) {
                val difference = abs(cps - lastCps)
                val average = (lastCps + cps) / 2.0
                val invalid = average > 9.25 && difference > 2.8
                if (invalid) {
                    return false
                }
            }
            lastCps = cps
            samples.clear()
        }
        return true
    }
}

/**
 * AutoClickP: "Identical statistical values."
 */
object AutoClickP {
    private val samples = Lists.newLinkedList<Long>()
    private var lastDeviation = 0.0
    private var lastSkewness = 0.0
    private var lastKurtosis = 0.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.size == 15) {
            val deviation = getStandardDeviation(samples)
            val skewness = getSkewness(samples)
            val kurtosis = getKurtosis(samples)
            if (deviation == lastDeviation && skewness == lastSkewness && kurtosis == lastKurtosis) {
                return false
            }
            lastDeviation = deviation
            lastSkewness = skewness
            lastKurtosis = kurtosis
            samples.clear()
        }

        return true
    }
}

/**
 * AutoClickQ: "Too low average deviation."
 */
object AutoClickQ {
    private var samples = EvictingList<Long>(40)
    private var lastDeviation = 0.0

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }

        samples.add(delay)
        if (samples.isFull) {
            val deviation = getStandardDeviation(samples)
            val difference = abs(deviation - lastDeviation)
            val average = abs(deviation + lastDeviation) / 2.0
            if (difference < 3.0 && average < 150.0) {
                return false
            }
            lastDeviation = deviation
        }

        return true
    }
}

/**
 * AutoClickR: "Impossible consistency."
 */
object AutoClickR {
    private val samples = EvictingList<Long>(30)

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.isFull) {
            if (samples.stream().filter { l: Long -> l > 150L }.count().toInt() == 0) {
                return false
            }
        }
        return true
    }
}

/**
 * AutoClickS: "Checks for amount of distinct delays."
 */
object AutoClickS {
    private val samples = EvictingList<Long>(25)

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.isFull) {
            val distinct = getDistinct(samples)
            if (distinct < 6) {
                return false
            }
        }
        return true
    }
}

/**
 * AutoClickT: "Too low kurtosis"
 */
object AutoClickT {
    private val samples = EvictingList<Long>(25)

    fun handle(): Boolean {
        val delay = ClickHandle.clickProcessor.delay
        if (delay > 5000L) {
            samples.clear()
            return true
        }
        samples.add(delay)
        if (samples.isFull) {
            val kurtosis = getKurtosis(samples) / 1000.0
            ClickHandle.clickProcessor.kurtosis = kurtosis
            if (kurtosis < Config.AUTOCLICKER_T_MIN_KURTOSIS) {
                return false
            }
        }
        return true
    }
}

/***
 * ClickProcessor
 */
data class ClickProcessor(
    var lastSwing: Long = -1L,
    var delay: Long = 0,
    var lastInteractEntity: Long = System.currentTimeMillis(),
    val samples: EvictingList<Long> = EvictingList(20),
    var cps: Double = 0.0,
    var kurtosis: Double = 0.0,
) {
    fun handleArmAnimation() {
        val now = System.currentTimeMillis()
        if (lastSwing > 0L) {
            delay = now - lastSwing
            samples.add(delay)
            if (samples.isFull) {
                cps = getCps(samples)
            }
        }
        lastSwing = now
    }
}


/***
 * MathUtils
 */
object MathUtil {
    fun wrappedDifference(number1: Double, number2: Double): Double {
        return min(
            abs(number1 - number2),
            min(abs(number1 - 360) - abs(number2 - 0), abs(number2 - 360) - abs(number1 - 0))
        )
    }
    fun getVariance(data: Collection<Number>): Double {
        var count = 0
        var sum = 0.0
        var variance = 0.0
        for (number in data) {
            sum += number.toDouble()
            ++count
        }
        val average = sum / count
        for (number in data) {
            variance += (number.toDouble() - average).pow(2.0)
        }
        return variance
    }

    fun getStandardDeviation(data: Collection<Number>): Double {
        val variance = getVariance(data)
        return sqrt(variance)
    }

    fun getSkewness(data: Collection<Number>): Double {
        var sum = 0.0
        var count = 0
        val numbers = Lists.newArrayList<Double>()
        for (number in data) {
            sum += number.toDouble()
            ++count
            numbers.add(number.toDouble())
        }
        numbers.sort()
        val mean = sum / count
        val median = if ((count % 2 != 0)) numbers[count / 2] else ((numbers[(count - 1) / 2] + numbers[count / 2]) / 2.0)
        val variance = getVariance(data)
        return 3.0 * (mean - median) / variance
    }

    fun getAverage(data: Collection<Number>?): Double {
        if (data.isNullOrEmpty()) {
            return 0.0
        }
        var sum = 0.0
        for (number in data) {
            sum += number.toDouble()
        }
        return sum / data.size
    }

    fun getKurtosis(data: Collection<Number>): Double {
        var sum = 0.0
        var count = 0
        for (number in data) {
            sum += number.toDouble()
            ++count
        }
        if (count < 3.0) {
            return 0.0
        }
        val efficiencyFirst = count * (count + 1.0) / ((count - 1.0) * (count - 2.0) * (count - 3.0))
        val efficiencySecond: Double = 3.0 * (count - 1.0).pow(2.0) / ((count - 2.0) * (count - 3.0))
        val average = sum / count
        var variance = 0.0
        var varianceSquared = 0.0
        for (number2 in data) {
            variance += (average - number2.toDouble()).pow(2.0)
            varianceSquared += (average - number2.toDouble()).pow(4.0)
        }
        return efficiencyFirst * (varianceSquared / (variance / sum).pow(2.0)) - efficiencySecond
    }

    fun round(value: Double, places: Int): Double {
        require(places >= 0)
        return BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toDouble()
    }

    fun getCps(data: Collection<Number>?): Double {
        return 20.0 / getAverage(data) * 50.0
    }

    fun getDistinct(data: Collection<Number?>): Int {
        return data.stream().distinct().count().toInt()
    }
}