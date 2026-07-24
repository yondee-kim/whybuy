package com.whybuy.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_rule",
    foreignKeys = [
        ForeignKey(
            entity = TargetAppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AppRuleEntity(
    @PrimaryKey val packageName: String,

    /** EVERY_TIME / ONCE_A_DAY / MAX_PER_DAY / ONCE_A_WEEK / MIN_INTERVAL */
    val frequencyType: String = "ONCE_A_DAY",
    val frequencyValue: Int? = null,

    /** ALWAYS / ONLY_BETWEEN / EXCEPT_BETWEEN */
    val windowType: String = "ALWAYS",
    /** 자정 기준 분 단위 (0~1439) */
    val startTime: Int? = null,
    val endTime: Int? = null,

    /** EVERYDAY / WEEKDAY / WEEKEND / CUSTOM */
    val dayRuleType: String = "EVERYDAY",
    /** 비트마스크: 월1 화2 수4 목8 금16 토32 일64 */
    val customDays: Int? = null,

    /** 이 시각까지 멈춤. null이면 활성 */
    val pausedUntil: Long? = null,

    val updatedAt: Long = System.currentTimeMillis()
)