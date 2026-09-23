package com.memoria.idedikate.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.memoria.idedikate.model.MemorialItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Memorial pins backed by the Firestore "memorials" collection.
 * Firestore's offline cache keeps pins on-device and queues writes made while offline.
 */
class MapViewModel : ViewModel() {
    private val collection = FirebaseFirestore.getInstance().collection(COLLECTION)
    private var registration: ListenerRegistration? = null

    private val _memorials = MutableStateFlow<List<MemorialItem>>(emptyList())
    val memorials: StateFlow<List<MemorialItem>> = _memorials.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /** Live-loads pins inside [bounds]. Firestore only allows a range filter on one field, so longitude is filtered locally. */
    fun setVisibleBounds(bounds: LatLngBounds) {
        registration?.remove()
        registration = collection
            .whereGreaterThanOrEqualTo(FIELD_LATITUDE, bounds.southwest.latitude)
            .whereLessThanOrEqualTo(FIELD_LATITUDE, bounds.northeast.latitude)
            .limit(MAX_PINS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to load memorials", error)
                    _syncError.value = "Couldn't load memorials: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                _memorials.value = snapshot?.documents
                    .orEmpty()
                    .mapNotNull { it.toMemorialItem() }
                    .filter { bounds.containsLongitude(it.longitude) }
            }
    }

    /** Writes a new pin. The listener shows it immediately; [onFailure] fires if the server rejects it. */
    fun dropPin(latLng: LatLng, message: String, onFailure: (Exception) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onFailure(IllegalStateException("Not signed in"))
            return
        }
        collection.document()
            .set(
                mapOf(
                    FIELD_LATITUDE to latLng.latitude,
                    FIELD_LONGITUDE to latLng.longitude,
                    FIELD_MESSAGE to message,
                    FIELD_OWNER_UID to uid,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save memorial", e)
                onFailure(e)
            }
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    override fun onCleared() {
        registration?.remove()
        super.onCleared()
    }

    private fun DocumentSnapshot.toMemorialItem(): MemorialItem? {
        val lat = getDouble(FIELD_LATITUDE) ?: return null
        val lng = getDouble(FIELD_LONGITUDE) ?: return null
        return MemorialItem(
            id = id,
            latitude = lat,
            longitude = lng,
            message = getString(FIELD_MESSAGE).orEmpty(),
            ownerUid = getString(FIELD_OWNER_UID).orEmpty()
        )
    }

    private fun LatLngBounds.containsLongitude(lng: Double): Boolean {
        val west = southwest.longitude
        val east = northeast.longitude
        // Bounds crossing the antimeridian have west > east
        return if (west <= east) lng in west..east else lng >= west || lng <= east
    }

    private companion object {
        const val TAG = "MapViewModel"
        const val COLLECTION = "memorials"
        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_MESSAGE = "message"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_CREATED_AT = "createdAt"
        const val MAX_PINS = 500L
    }
}
