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

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _isUserLoggedIn.value = firebaseAuth.currentUser != null
        }
    }

    fun handleGoogleCredential(idToken: String) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    _isUserLoggedIn.value = true
                }
                .addOnFailureListener {
                    // Handle failure
                    Log.e("AuthViewModel", "Google sign-in failed", it)
                }
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Failed to handle Google credential", e)
        }
    }
    
    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Sign out failed", e)
        }
    }
}
