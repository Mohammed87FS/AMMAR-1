package alh.za.ammar.model

import kotlin.random.Random

data class Machine(
    // Use a random Int as a base for alarm request codes to ensure uniqueness and prevent crashes.
    val id: Int = Random.nextInt(1, 1_000_000_000),
    val createdAt: Long = System.currentTimeMillis(),
    val name: String,
    val totalProducts: Int,
    val productsPerDrop: Int,
    val timePerDropInSeconds: Double,
    val isStopped: Boolean = false,
    val stoppedAt: Long? = null
)
