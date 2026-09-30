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
import com.google.firebase.firestore.Query
import com.memoria.idedikate.model.MemorialItem
import com.memoria.idedikate.model.MemorialOfferings
import com.memoria.idedikate.model.OfferingType
import com.memoria.idedikate.model.Wallet
import com.memoria.idedikate.model.PinVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Memorial pins backed by the Firestore "memorials" collection.
 * Firestore's offline cache keeps pins on-device and queues writes made while offline.
 *
 * Firestore rules aren't filters, so visibility is loaded with one query per audience
 * (public pins, the user's own pins, pins shared with the user's email) and merged here.
 */
class MapViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection(COLLECTION)
    private val registrations = mutableListOf<ListenerRegistration>()
    private val results = mutableMapOf<String, List<MemorialItem>>()
    private var loadedForUid: String? = null

    private val _memorials = MutableStateFlow<List<MemorialItem>>(emptyList())
    val memorials: StateFlow<List<MemorialItem>> = _memorials.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    // Memorials tab: all of the user's memorials and those shared with them, not limited to the map area
    private val listRegistrations = mutableListOf<ListenerRegistration>()
    private var listUid: String? = null

    private val _myMemorials = MutableStateFlow<List<MemorialItem>>(emptyList())
    val myMemorials: StateFlow<List<MemorialItem>> = _myMemorials.asStateFlow()

    private val _sharedWithMe = MutableStateFlow<List<MemorialItem>>(emptyList())
    val sharedWithMe: StateFlow<List<MemorialItem>> = _sharedWithMe.asStateFlow()

    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String? get() = auth.currentUser?.email?.lowercase()

    // This ViewModel is activity-scoped, so drop another user's pins as soon as they sign out
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser?.uid != loadedForUid) stopListening()
        listenForList(firebaseAuth.currentUser?.uid)
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    /** The map asks for location once per app session, not every time the Map tab opens. */
    var locationPermissionRequested = false

    /**
     * Live-loads visible pins inside [bounds], filtering both latitude and longitude in the query so
     * the per-query limit only counts pins that are actually on screen.
     */
    fun setVisibleBounds(bounds: LatLngBounds) {
        val uid = currentUid ?: return
        if (uid != loadedForUid) stopListening()
        registrations.forEach { it.remove() }
        registrations.clear()
        loadedForUid = uid

        // Bounds crossing the antimeridian have west > east: query each side of it separately
        val west = bounds.southwest.longitude
        val east = bounds.northeast.longitude
        val longitudeRanges = if (west <= east) listOf(west to east) else listOf(west to 180.0, -180.0 to east)
        val audiences = buildList {
            add(SOURCE_PUBLIC to collection.whereEqualTo(FIELD_VISIBILITY, PinVisibility.PUBLIC.firestoreValue))
            add(SOURCE_OWN to collection.whereEqualTo(FIELD_OWNER_UID, uid))
            currentEmail?.let { email -> add(SOURCE_SHARED to collection.whereArrayContains(FIELD_SHARED_WITH, email)) }
        }
        val sources = mutableSetOf<String>()
        for ((audience, query) in audiences) {
            longitudeRanges.forEachIndexed { i, (fromLng, toLng) ->
                val source = "$audience-$i"
                sources += source
                listen(source, query, bounds.southwest.latitude, bounds.northeast.latitude, fromLng, toLng)
            }
        }
        // Keep showing the previous pins until the new queries answer, except from queries that are gone
        if (results.keys.retainAll(sources)) publish()
    }

    /**
     * Creates a memorial and pays for it (1 token + its [offerings]) in one batched write, so one
     * can't happen without the other; the rules check the deduction against the memorial. The
     * listeners show both changes immediately, and Firestore rolls them back locally if the
     * server rejects the batch, in which case [onFailure] fires. [onSuccess] fires once the
     * server has accepted it (so not while offline).
     */
    fun placeMemorial(
        latLng: LatLng,
        message: String,
        visibility: PinVisibility,
        sharedWith: List<String>,
        offerings: MemorialOfferings,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = currentUid
        if (uid == null) {
            onFailure(IllegalStateException("Not signed in"))
            return
        }
        val memorialRef = collection.document()
        val memorial = mapOf(
            FIELD_LATITUDE to latLng.latitude,
            FIELD_LONGITUDE to latLng.longitude,
            FIELD_MESSAGE to message,
            FIELD_OWNER_UID to uid,
            FIELD_VISIBILITY to visibility.firestoreValue,
            FIELD_SHARED_WITH to normalizeSharedWith(visibility, sharedWith),
            FIELD_OFFERINGS to offerings.toFirestore(),
            FIELD_CREATED_AT to FieldValue.serverTimestamp()
        )
        val payment = buildMap<String, Any> {
            put(Wallet.FIELD_TOKENS, FieldValue.increment(-Wallet.MEMORIAL_TOKEN_COST.toLong()))
            OfferingType.entries.forEach { type ->
                offerings[type].takeIf { it > 0 }?.let { put(type.firestoreKey, FieldValue.increment(-it.toLong())) }
            }
            put(Wallet.FIELD_LAST_MEMORIAL_ID, memorialRef.id)
        }

        db.batch()
            .set(memorialRef, memorial)
            .update(Wallet.ref(db, uid), payment)
            .commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to place memorial", e)
                onFailure(e)
            }
    }

    /** Changes who can see one of the user's own pins. */
    fun updateVisibility(
        pinId: String,
        visibility: PinVisibility,
        sharedWith: List<String>,
        onFailure: (Exception) -> Unit
    ) {
        collection.document(pinId)
            .update(
                mapOf(
                    FIELD_VISIBILITY to visibility.firestoreValue,
                    FIELD_SHARED_WITH to normalizeSharedWith(visibility, sharedWith)
                )
            )
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update memorial $pinId", e)
                onFailure(e)
            }
    }

    fun deletePin(pinId: String, onFailure: (Exception) -> Unit) {
        collection.document(pinId)
            .delete()
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete memorial $pinId", e)
                onFailure(e)
            }
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        registrations.forEach { it.remove() }
        listRegistrations.forEach { it.remove() }
    }

    private fun listenForList(uid: String?) {
        if (uid == listUid) return
        listRegistrations.forEach { it.remove() }
        listRegistrations.clear()
        listUid = uid
        _myMemorials.value = emptyList()
        _sharedWithMe.value = emptyList()
        if (uid == null) return

        listRegistrations += listenNewestFirst(collection.whereEqualTo(FIELD_OWNER_UID, uid), _myMemorials)
        currentEmail?.let { email ->
            listRegistrations += listenNewestFirst(collection.whereArrayContains(FIELD_SHARED_WITH, email), _sharedWithMe)
        }
    }

    // Sorted locally: ordering in the query would need an extra composite index
    private fun listenNewestFirst(query: Query, target: MutableStateFlow<List<MemorialItem>>): ListenerRegistration =
        query.limit(MAX_LIST_ITEMS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser != null) Log.e(TAG, "Failed to load memorial list", error)
                return@addSnapshotListener
            }
            target.value = snapshot?.documents.orEmpty()
                .mapNotNull { it.toMemorialItem() }
                // A just-placed memorial has no server time yet; it's the newest
                .sortedByDescending { if (it.createdAtMillis == 0L) Long.MAX_VALUE else it.createdAtMillis }
        }

    private fun listen(source: String, query: Query, south: Double, north: Double, west: Double, east: Double) {
        registrations += query
            .whereGreaterThanOrEqualTo(FIELD_LATITUDE, south)
            .whereLessThanOrEqualTo(FIELD_LATITUDE, north)
            .whereGreaterThanOrEqualTo(FIELD_LONGITUDE, west)
            .whereLessThanOrEqualTo(FIELD_LONGITUDE, east)
            .limit(MAX_PINS_PER_QUERY)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Listeners are rejected with PERMISSION_DENIED once the user signs out; that's expected
                    if (auth.currentUser == null) return@addSnapshotListener
                    Log.e(TAG, "Failed to load $source memorials", error)
                    _syncError.value = "Couldn't load memorials: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                results[source] = snapshot?.documents
                    .orEmpty()
                    .mapNotNull { it.toMemorialItem() }
                publish()
            }
    }

    private fun publish() {
        // The user's own public/shared pins come back from more than one query
        _memorials.value = results.values.flatten().distinctBy { it.id }
    }

    private fun stopListening() {
        registrations.forEach { it.remove() }
        registrations.clear()
        results.clear()
        loadedForUid = null
        publish()
    }

    private fun normalizeSharedWith(visibility: PinVisibility, sharedWith: List<String>): List<String> =
        if (visibility == PinVisibility.SHARED) {
            sharedWith.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        } else {
            emptyList()
        }

    private fun DocumentSnapshot.toMemorialItem(): MemorialItem? {
        val lat = getDouble(FIELD_LATITUDE) ?: return null
        val lng = getDouble(FIELD_LONGITUDE) ?: return null
        @Suppress("UNCHECKED_CAST")
        return MemorialItem(
            id = id,
            latitude = lat,
            longitude = lng,
            message = getString(FIELD_MESSAGE).orEmpty(),
            ownerUid = getString(FIELD_OWNER_UID).orEmpty(),
            visibility = PinVisibility.fromFirestore(getString(FIELD_VISIBILITY)),
            sharedWith = (get(FIELD_SHARED_WITH) as? List<String>).orEmpty(),
            offerings = MemorialOfferings.fromFirestore(get(FIELD_OFFERINGS) as? Map<*, *>),
            createdAtMillis = getTimestamp(FIELD_CREATED_AT)?.toDate()?.time ?: 0
        )
    }

    private companion object {
        const val TAG = "MapViewModel"
        const val COLLECTION = "memorials"
        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_MESSAGE = "message"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_VISIBILITY = "visibility"
        const val FIELD_SHARED_WITH = "sharedWith"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_OFFERINGS = "offerings"
        const val SOURCE_PUBLIC = "public"
        const val SOURCE_OWN = "own"
        const val SOURCE_SHARED = "shared"
        const val MAX_PINS_PER_QUERY = 500L
        const val MAX_LIST_ITEMS = 200L
    }
}
