package com.memoria.idedikate

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.memoria.idedikate.ads.RewardAdType
import com.memoria.idedikate.model.MemorialOfferings
import com.memoria.idedikate.model.OfferingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.walletDataStore by preferencesDataStore(name = "wallet")

/** Wallet balances, persisted on-device with DataStore and kept separately per signed-in user. */
class TokenViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.walletDataStore
    private val auth = FirebaseAuth.getInstance()

    private val uid = MutableStateFlow(auth.currentUser?.uid)
    private val authStateListener = FirebaseAuth.AuthStateListener { uid.value = it.currentUser?.uid }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    val tokens: StateFlow<Int> = balance(RewardAdType.REWARDED_TOKENS)
    val plaques: StateFlow<Int> = balance(RewardAdType.REWARDED_DISPLAY)
    val incenseSticks: StateFlow<Int> = balance(RewardAdType.REWARDED_INCENSE)
    val fruitOfferings: StateFlow<Int> = balance(RewardAdType.REWARDED_FRUITS)
    val foodOfferings: StateFlow<Int> = balance(RewardAdType.REWARDED_FOOD)

    /** Offerings the user owns and can place at a memorial. */
    val offeringStock: StateFlow<MemorialOfferings> =
        combine(plaques, incenseSticks, fruitOfferings, foodOfferings) { plaques, incense, fruit, food ->
            MemorialOfferings(plaques = plaques, incenseSticks = incense, fruit = fruit, food = food)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MemorialOfferings())

    fun addTokens(amount: Int) = addItem(RewardAdType.REWARDED_TOKENS, amount)

    /** Deducts [amount] tokens if the balance allows it. Returns true on success. */
    suspend fun spendTokens(amount: Int): Boolean {
        var spent = false
        // DataStore serialises edits, so check-and-deduct is atomic
        dataStore.edit { prefs ->
            val key = key(uid.value, RewardAdType.REWARDED_TOKENS)
            val current = prefs.balanceOf(key, RewardAdType.REWARDED_TOKENS)
            if (current >= amount) {
                prefs[key] = current - amount
                spent = true
            }
        }
        return spent
    }

    /**
     * Deducts [tokens] plus every offering in [offerings] in a single edit, so nothing is spent
     * unless the user can afford all of it. Returns true on success.
     */
    suspend fun spendForMemorial(tokens: Int, offerings: MemorialOfferings): Boolean {
        val cost = buildMap {
            put(RewardAdType.REWARDED_TOKENS, tokens)
            OfferingType.entries.forEach { type -> merge(type.rewardAdType, offerings[type], Int::plus) }
        }.filterValues { it > 0 }

        var spent = false
        dataStore.edit { prefs ->
            val currentUid = uid.value
            val affordable = cost.all { (type, amount) -> prefs.balanceOf(key(currentUid, type), type) >= amount }
            if (affordable) {
                cost.forEach { (type, amount) ->
                    val key = key(currentUid, type)
                    prefs[key] = prefs.balanceOf(key, type) - amount
                }
                spent = true
            }
        }
        return spent
    }

    /** Returns what [spendForMemorial] took, e.g. when the server rejects the memorial. */
    fun refundMemorial(tokens: Int, offerings: MemorialOfferings) {
        if (tokens > 0) addItem(RewardAdType.REWARDED_TOKENS, tokens)
        OfferingType.entries.forEach { type ->
            offerings[type].takeIf { it > 0 }?.let { addItem(type.rewardAdType, it) }
        }
    }

    fun addItem(type: RewardAdType, amount: Int) {
        val key = key(uid.value, type)
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[key] = prefs.balanceOf(key, type) + amount }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    private fun balance(type: RewardAdType): StateFlow<Int> =
        combine(uid, dataStore.data) { currentUid, prefs -> prefs.balanceOf(key(currentUid, type), type) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private fun Preferences.balanceOf(key: Preferences.Key<Int>, type: RewardAdType): Int =
        this[key] ?: if (type == RewardAdType.REWARDED_TOKENS) STARTER_TOKENS else 0

    private fun key(uid: String?, type: RewardAdType) = intPreferencesKey("${uid ?: "guest"}_${type.name}")

    private companion object {
        const val STARTER_TOKENS = 5
    }
}
