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

/**
 * Wallet items that can be offered at a memorial, and the rewarded ad that earns each one.
 * [legacyFirestoreKey] is the field name used before flowers and candles replaced fruit and food.
 */
enum class OfferingType(
    val label: String,
    val firestoreKey: String,
    val rewardAdType: RewardAdType,
    val legacyFirestoreKey: String? = null
) {
    PLAQUE("Memorial plaque", "plaques", RewardAdType.REWARDED_DISPLAY),
    INCENSE("Incense sticks", "incenseSticks", RewardAdType.REWARDED_INCENSE),
    FLOWERS("Flowers", "flowers", RewardAdType.REWARDED_FLOWERS, legacyFirestoreKey = "fruit"),
    CANDLES("Candles", "candles", RewardAdType.REWARDED_CANDLES, legacyFirestoreKey = "food")
}

/** Quantity of each offering placed at a memorial. */
@Serializable
data class MemorialOfferings(
    val plaques: Int = 0,
    val incenseSticks: Int = 0,
    val flowers: Int = 0,
    val candles: Int = 0
) {
    operator fun get(type: OfferingType): Int = when (type) {
        OfferingType.PLAQUE -> plaques
        OfferingType.INCENSE -> incenseSticks
        OfferingType.FLOWERS -> flowers
        OfferingType.CANDLES -> candles
    }

    fun with(type: OfferingType, quantity: Int): MemorialOfferings = when (type) {
        OfferingType.PLAQUE -> copy(plaques = quantity)
        OfferingType.INCENSE -> copy(incenseSticks = quantity)
        OfferingType.FLOWERS -> copy(flowers = quantity)
        OfferingType.CANDLES -> copy(candles = quantity)
    }

    val isEmpty: Boolean get() = OfferingType.entries.all { this[it] == 0 }

    /** e.g. "1 plaque, 5 incense, 2 flowers, 1 candle" */
    fun summary(): String = listOfNotNull(
        plaques.takeIf { it > 0 }?.let { "$it plaque${if (it > 1) "s" else ""}" },
        incenseSticks.takeIf { it > 0 }?.let { "$it incense" },
        flowers.takeIf { it > 0 }?.let { "$it flower${if (it > 1) "s" else ""}" },
        candles.takeIf { it > 0 }?.let { "$it candle${if (it > 1) "s" else ""}" }
    ).joinToString(", ").ifEmpty { "No offerings" }

    fun toFirestore(): Map<String, Int> = OfferingType.entries.associate { it.firestoreKey to this[it] }

    companion object {
        const val MAX_PER_ITEM = 99

        /** Also reads memorials saved before the rename, whose offerings use "fruit"/"food". */
        fun fromFirestore(data: Map<*, *>?): MemorialOfferings =
            OfferingType.entries.fold(MemorialOfferings()) { acc, type ->
                val raw = data?.get(type.firestoreKey) ?: type.legacyFirestoreKey?.let { data?.get(it) }
                acc.with(type, (raw as? Number)?.toInt()?.coerceIn(0, MAX_PER_ITEM) ?: 0)
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
    val offerings: MemorialOfferings = MemorialOfferings(),
    /** When it was placed (server time); 0 until the server confirms a new memorial. */
    val createdAtMillis: Long = 0
)

/** What the AR screen needs to place a memorial. Serializable so it can travel in a nav key. */
@Serializable
data class ArMemorial(
    val title: String,
    val offerings: MemorialOfferings,
    /** Where the memorial was placed; used to show it at its real-world spot. */
    val latitude: Double? = null,
    val longitude: Double? = null
)

fun MemorialItem.toArMemorial() =
    ArMemorial(title = message, offerings = offerings, latitude = latitude, longitude = longitude)
