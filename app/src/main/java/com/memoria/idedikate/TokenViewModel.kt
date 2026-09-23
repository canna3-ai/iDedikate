package com.memoria.idedikate

import androidx.lifecycle.ViewModel
import com.memoria.idedikate.ads.RewardAdType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TokenViewModel : ViewModel() {
    private val _tokens = MutableStateFlow(0)
    val tokens: StateFlow<Int> = _tokens.asStateFlow()

    private val _plaques = MutableStateFlow(0)
    val plaques: StateFlow<Int> = _plaques.asStateFlow()

    private val _incenseSticks = MutableStateFlow(0)
    val incenseSticks: StateFlow<Int> = _incenseSticks.asStateFlow()

    private val _fruitOfferings = MutableStateFlow(0)
    val fruitOfferings: StateFlow<Int> = _fruitOfferings.asStateFlow()

    private val _foodOfferings = MutableStateFlow(0)
    val foodOfferings: StateFlow<Int> = _foodOfferings.asStateFlow()

    fun addTokens(amount: Int) {
        _tokens.update { it + amount }
    }

    fun addItem(type: RewardAdType, amount: Int) {
        when (type) {
            RewardAdType.REWARDED_TOKENS -> _tokens.update { it + amount }
            RewardAdType.REWARDED_DISPLAY -> _plaques.update { it + amount }
            RewardAdType.REWARDED_INCENSE -> _incenseSticks.update { it + amount }
            RewardAdType.REWARDED_FRUITS -> _fruitOfferings.update { it + amount }
            RewardAdType.REWARDED_FOOD -> _foodOfferings.update { it + amount }
        }
    }
}
