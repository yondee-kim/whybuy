package com.whybuy.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_app")
data class TargetAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String = "SHOPPING",
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)