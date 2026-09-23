package com.memoria.idedikate.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _isUserLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _isUserLoggedIn.value = firebaseAuth.currentUser != null
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    fun handleGoogleCredential(idToken: String) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    _isUserLoggedIn.value = true
                    _errorMessage.value = null
                }
                .addOnFailureListener {
                    // Handle failure
                    Log.e("AuthViewModel", "Google sign-in failed", it)
                    _errorMessage.value = "Sign in failed: ${it.localizedMessage}"
                }
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Failed to handle Google credential", e)
            _errorMessage.value = "An error occurred: ${e.localizedMessage}"
        }
    }
    
    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Sign out failed", e)
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }
}
