package alh.za.ammar.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlin.math.roundToLong

data class Machine(
    // Use a random Int as a base for alarm request codes to ensure uniqueness and prevent crashes.
    val id: Int = Random.nextInt(1, 1_000_000_000),
    val createdAt: Long = System.currentTimeMillis(),
    val name: String,
    val totalProducts: Int,
    val currentProducts: Int = 0,
    val productsPerDrop: Int,
    val timePerDropInSeconds: Double,
    val isStopped: Boolean = false,
    val stoppedAt: Long? = null
)

fun Machine.completedProducts(): Int = currentProducts.coerceIn(0, totalProducts.coerceAtLeast(0))

fun Machine.remainingProducts(): Int = (totalProducts - completedProducts()).coerceAtLeast(0)

fun Machine.cycleCount(): Int {
    val remainingProducts = remainingProducts()
    if (remainingProducts <= 0 || productsPerDrop <= 0) {
        return 0
    }

    return (remainingProducts + productsPerDrop - 1) / productsPerDrop
}

fun Machine.cycleDurationMillis(): Long = (timePerDropInSeconds * 1000).roundToLong()

fun Machine.totalDurationMillis(): Long = cycleCount() * cycleDurationMillis()

fun Machine.finishedAtMillis(): Long = createdAt + totalDurationMillis()

fun Machine.remainingMillis(now: Long = System.currentTimeMillis()): Long {
    val elapsed = if (isStopped && stoppedAt != null) {
        stoppedAt - createdAt
    } else {
        now - createdAt
    }

    return (totalDurationMillis() - elapsed).coerceAtLeast(0)
}

fun Machine.isFinished(now: Long = System.currentTimeMillis()): Boolean = remainingMillis(now) <= 0L

fun formatClockTime(timestampMillis: Long, locale: Locale = Locale.getDefault()): String {
    val formatter = SimpleDateFormat("HH:mm:ss", locale)
    return formatter.format(Date(timestampMillis))
}
