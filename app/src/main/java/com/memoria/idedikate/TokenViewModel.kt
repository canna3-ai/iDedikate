package com.memoria.idedikate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.memoria.idedikate.ads.RewardAdType
import com.memoria.idedikate.model.MemorialOfferings
import com.memoria.idedikate.model.OfferingType
import com.memoria.idedikate.model.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The signed-in user's wallet, stored in Firestore at `wallets/{uid}` so it follows the account
 * across devices. Firestore's cache keeps it usable offline, and firestore.rules validates every
 * change (reward amounts, rate limit, memorial costs).
 */
class TokenViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var registration: ListenerRegistration? = null
    private var listeningUid: String? = null

    private val balances = MutableStateFlow<Map<RewardAdType, Int>>(emptyMap())

    val tokens: StateFlow<Int> = balance(RewardAdType.REWARDED_TOKENS)
    val plaques: StateFlow<Int> = balance(RewardAdType.REWARDED_DISPLAY)
    val incenseSticks: StateFlow<Int> = balance(RewardAdType.REWARDED_INCENSE)
    val flowers: StateFlow<Int> = balance(RewardAdType.REWARDED_FLOWERS)
    val candles: StateFlow<Int> = balance(RewardAdType.REWARDED_CANDLES)

    /** Offerings the user owns and can place at a memorial. */
    val offeringStock: StateFlow<MemorialOfferings> = balances
        .map { b ->
            MemorialOfferings(
                plaques = b[RewardAdType.REWARDED_DISPLAY] ?: 0,
                incenseSticks = b[RewardAdType.REWARDED_INCENSE] ?: 0,
                flowers = b[RewardAdType.REWARDED_FLOWERS] ?: 0,
                candles = b[RewardAdType.REWARDED_CANDLES] ?: 0
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MemorialOfferings())

    // Fires immediately with the current user, then on every sign-in/out
    private val authStateListener = FirebaseAuth.AuthStateListener { listenTo(it.currentUser?.uid) }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    /**
     * Credits the configured reward for [type]. The rules only accept exactly that amount, at most
     * once every 15 seconds, so [onFailure] fires if the write is rejected.
     */
    fun addReward(type: RewardAdType, onFailure: (Exception) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure(IllegalStateException("Not signed in"))
            return
        }
        Wallet.ref(db, uid)
            .update(
                mapOf(
                    Wallet.field(type) to FieldValue.increment(type.configuredRewardAmount.toLong()),
                    Wallet.FIELD_LAST_REWARD_AT to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to credit ${type.name}", e)
                onFailure(e)
            }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        registration?.remove()
        super.onCleared()
    }

    private fun listenTo(uid: String?) {
        if (uid == listeningUid) return
        registration?.remove()
        registration = null
        listeningUid = uid
        balances.value = emptyMap()
        if (uid == null) return

        val ref = Wallet.ref(db, uid)
        registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Rejected with PERMISSION_DENIED once the user signs out; that's expected
                if (auth.currentUser != null) Log.e(TAG, "Failed to load wallet", error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            if (!snapshot.exists()) {
                // Only trust "missing" from the server, not from an empty offline cache
                if (!snapshot.metadata.isFromCache) createWallet(ref)
                return@addSnapshotListener
            }
            val data = snapshot.data.orEmpty()
            // Older wallets store flowers/candles as "fruit"/"food": move them once to the new fields
            Wallet.legacyMigration(data)?.let { update ->
                ref.update(update).addOnFailureListener { e -> Log.e(TAG, "Failed to migrate wallet", e) }
            }
            // Until the migration lands, show the balances from the old field names
            val legacyKeys = OfferingType.entries.associate { it.rewardAdType to it.legacyFirestoreKey }
            balances.value = RewardAdType.entries.associateWith { type ->
                val value = snapshot.getLong(Wallet.field(type)) ?: legacyKeys[type]?.let { snapshot.getLong(it) }
                (value ?: 0L).toInt()
            }
        }
    }

    private fun createWallet(ref: DocumentReference) {
        // Transaction so two devices signing in at once can't both create it
        db.runTransaction { transaction ->
            if (!transaction.get(ref).exists()) transaction.set(ref, Wallet.starter())
        }.addOnFailureListener { e -> Log.e(TAG, "Failed to create wallet", e) }
    }

    private fun balance(type: RewardAdType): StateFlow<Int> =
        balances.map { it[type] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private companion object {
        const val TAG = "TokenViewModel"
    }
}
