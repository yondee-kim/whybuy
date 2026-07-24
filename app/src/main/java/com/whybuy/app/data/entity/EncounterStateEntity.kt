package com.whybuy.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encounter_state")
data class EncounterStateEntity(
    @PrimaryKey val packageName: String,
    val lastShownAt: Long = 0L,
    val todayCount: Int = 0,
    val todayDate: String = "",
    val weekCount: Int = 0,
    val weekKey: String = "",
    val consecutiveSkip: Int = 0
)