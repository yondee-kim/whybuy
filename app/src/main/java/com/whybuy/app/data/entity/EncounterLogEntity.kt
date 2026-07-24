package com.whybuy.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "encounter_log",
    indices = [
        Index("serviceDate"),
        Index("packageName", "shownAt")
    ]
)
data class EncounterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val shownAt: Long,
    /** 새벽 4시 기준 날짜 yyyy-MM-dd */
    val serviceDate: String,
    val emotionCode: String? = null,
    val emotionMemo: String? = null,
    val decisionCode: String? = null,
    val endReason: String,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)