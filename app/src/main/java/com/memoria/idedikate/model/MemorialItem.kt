package com.memoria.idedikate.model

data class MemorialItem(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val message: String = "",
    val ownerUid: String = ""
)
