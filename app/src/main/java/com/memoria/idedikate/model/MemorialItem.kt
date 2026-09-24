package com.memoria.idedikate.model

import com.memoria.idedikate.ads.RewardAdType
import kotlinx.serialization.Serializable

enum class PinVisibility(val firestoreValue: String, val label: String) {
    PRIVATE("private", "Private"),
    SHARED("shared", "Shared"),
    PUBLIC("public", "Public");

    companion object {
        // Unknown/missing values fall back to the most restrictive option
        fun fromFirestore(value: String?): PinVisibility =
            entries.firstOrNull { it.firestoreValue == value } ?: PRIVATE
    }
}

/** Wallet items that can be offered at a memorial, and the rewarded ad that earns each one. */
enum class OfferingType(val label: String, val firestoreKey: String, val rewardAdType: RewardAdType) {
    PLAQUE("Memorial plaque", "plaques", RewardAdType.REWARDED_DISPLAY),
    INCENSE("Incense sticks", "incenseSticks", RewardAdType.REWARDED_INCENSE),
    FRUIT("Fruit offerings", "fruit", RewardAdType.REWARDED_FRUITS),
    FOOD("Food offerings", "food", RewardAdType.REWARDED_FOOD)
}

/** Quantity of each offering placed at a memorial. */
@Serializable
data class MemorialOfferings(
    val plaques: Int = 0,
    val incenseSticks: Int = 0,
    val fruit: Int = 0,
    val food: Int = 0
) {
    operator fun get(type: OfferingType): Int = when (type) {
        OfferingType.PLAQUE -> plaques
        OfferingType.INCENSE -> incenseSticks
        OfferingType.FRUIT -> fruit
        OfferingType.FOOD -> food
    }

    fun with(type: OfferingType, quantity: Int): MemorialOfferings = when (type) {
        OfferingType.PLAQUE -> copy(plaques = quantity)
        OfferingType.INCENSE -> copy(incenseSticks = quantity)
        OfferingType.FRUIT -> copy(fruit = quantity)
        OfferingType.FOOD -> copy(food = quantity)
    }

    val isEmpty: Boolean get() = OfferingType.entries.all { this[it] == 0 }

    /** e.g. "1 plaque, 5 incense, 2 fruit" */
    fun summary(): String = listOfNotNull(
        plaques.takeIf { it > 0 }?.let { "$it plaque${if (it > 1) "s" else ""}" },
        incenseSticks.takeIf { it > 0 }?.let { "$it incense" },
        fruit.takeIf { it > 0 }?.let { "$it fruit" },
        food.takeIf { it > 0 }?.let { "$it food" }
    ).joinToString(", ").ifEmpty { "No offerings" }

    fun toFirestore(): Map<String, Int> = OfferingType.entries.associate { it.firestoreKey to this[it] }

    companion object {
        const val MAX_PER_ITEM = 99

        fun fromFirestore(data: Map<*, *>?): MemorialOfferings =
            OfferingType.entries.fold(MemorialOfferings()) { acc, type ->
                acc.with(type, (data?.get(type.firestoreKey) as? Number)?.toInt()?.coerceIn(0, MAX_PER_ITEM) ?: 0)
            }
    }
}

data class MemorialItem(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val message: String = "",
    val ownerUid: String = "",
    val visibility: PinVisibility = PinVisibility.PRIVATE,
    /** Lower-cased Google account emails; only non-empty when [visibility] is SHARED. */
    val sharedWith: List<String> = emptyList(),
    val offerings: MemorialOfferings = MemorialOfferings()
)

/** What the AR screen needs to place a memorial. Serializable so it can travel in a nav key. */
@Serializable
data class ArMemorial(
    val title: String,
    val offerings: MemorialOfferings
)

fun MemorialItem.toArMemorial() = ArMemorial(title = message, offerings = offerings)
