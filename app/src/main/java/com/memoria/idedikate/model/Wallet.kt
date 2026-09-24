package com.memoria.idedikate.model

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.memoria.idedikate.ads.RewardAdType

/**
 * Layout of the Firestore wallet document `wallets/{uid}`.
 * firestore.rules enforces the same field names, starter balance, reward amounts and memorial cost,
 * so keep the two in sync.
 */
object Wallet {
    const val COLLECTION = "wallets"
    const val FIELD_TOKENS = "tokens"
    const val FIELD_LAST_REWARD_AT = "lastRewardAt"
    /** Set by the memorial batch so the rules can check the deduction against that memorial's offerings. */
    const val FIELD_LAST_MEMORIAL_ID = "lastMemorialId"

    const val MEMORIAL_TOKEN_COST = 1

    /** What a brand-new wallet starts with (enough for a first memorial with offerings). */
    val STARTER_BALANCE: Map<RewardAdType, Int> = mapOf(
        RewardAdType.REWARDED_TOKENS to 2,
        RewardAdType.REWARDED_DISPLAY to 1,
        RewardAdType.REWARDED_INCENSE to 4,
        RewardAdType.REWARDED_FLOWERS to 4,
        RewardAdType.REWARDED_CANDLES to 2
    )

    fun ref(db: FirebaseFirestore, uid: String): DocumentReference = db.collection(COLLECTION).document(uid)

    /** Offering balances share their field names with [MemorialOfferings] in memorial documents. */
    fun field(type: RewardAdType): String = when (type) {
        RewardAdType.REWARDED_TOKENS -> FIELD_TOKENS
        RewardAdType.REWARDED_DISPLAY -> OfferingType.PLAQUE.firestoreKey
        RewardAdType.REWARDED_INCENSE -> OfferingType.INCENSE.firestoreKey
        RewardAdType.REWARDED_FLOWERS -> OfferingType.FLOWERS.firestoreKey
        RewardAdType.REWARDED_CANDLES -> OfferingType.CANDLES.firestoreKey
    }

    /**
     * Wallets created before flowers and candles replaced fruit and food still hold "fruit"/"food".
     * Returns the one-time update that moves those balances to the new fields (allowed by the
     * rules only in exactly this form), or null if the wallet is already up to date.
     */
    fun legacyMigration(data: Map<String, Any>): Map<String, Any>? {
        val renames = OfferingType.entries.filter { it.legacyFirestoreKey != null && it.legacyFirestoreKey in data }
        if (renames.isEmpty() || renames.any { it.firestoreKey in data }) return null
        return renames.fold(mapOf()) { update, type ->
            update + mapOf(
                type.firestoreKey to data.getValue(type.legacyFirestoreKey!!),
                type.legacyFirestoreKey to FieldValue.delete()
            )
        }
    }

    fun starter(): Map<String, Any> =
        RewardAdType.entries.associate { field(it) to (STARTER_BALANCE[it] ?: 0) } +
            (FIELD_LAST_REWARD_AT to FieldValue.serverTimestamp())
}
