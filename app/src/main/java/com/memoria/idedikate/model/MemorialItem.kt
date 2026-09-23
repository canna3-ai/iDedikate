package com.memoria.idedikate.model

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

data class MemorialItem(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val message: String = "",
    val ownerUid: String = "",
    val visibility: PinVisibility = PinVisibility.PRIVATE,
    /** Lower-cased Google account emails; only non-empty when [visibility] is SHARED. */
    val sharedWith: List<String> = emptyList()
)
