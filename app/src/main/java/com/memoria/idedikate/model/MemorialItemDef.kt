package com.memoria.idedikate.model

data class MemorialItemDef(
    val id: String,
    val name: String,
    val geometryDescription: String,
    val referenceSize: String,
    val anchorType: String,
    val altitude: Float = 0f,
    val q_x: Float = 0f,
    val q_y: Float = 0f,
    val q_z: Float = 0f,
    val q_w: Float = 1f,
    val offset_x: Float = 0f,
    val offset_y: Float = 0f,
    val offset_z: Float = 0f,
    val offset_yaw: Float = 0f
)
