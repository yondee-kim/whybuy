package com.whybuy.app.service

import android.util.Log
import com.whybuy.app.core.ServiceDate
import com.whybuy.app.data.WhyBuyDatabase
import com.whybuy.app.domain.AppRule
import com.whybuy.app.domain.RuleResult
import com.whybuy.app.domain.SkipReason
import com.whybuy.app.domain.dayOfWeekOf
import com.whybuy.app.domain.localTimeOf

class RuleEngine(private val db: WhyBuyDatabase) {

    suspend fun evaluate(packageName: String, now: Long): RuleResult {
        val target = db.targetAppDao().findByPackage(packageName)
        if (target == null || !target.enabled) {
            return RuleResult.Skip(SkipReason.NOT_TARGET)
        }

        val ruleEntity = db.appRuleDao().findByPackage(packageName)
            ?: return RuleResult.Show   // 규칙이 없으면 일단 보여준다

        val rule = AppRule.from(ruleEntity)

        if (rule.isPausedAt(now)) {
            return RuleResult.Skip(SkipReason.APP_PAUSED)
        }

        if (!rule.dayRule.matches(dayOfWeekOf(now))) {
            return RuleResult.Skip(SkipReason.DAY_MISMATCH)
        }

        if (!rule.timeWindow.contains(localTimeOf(now))) {
            return RuleResult.Skip(SkipReason.TIME_MISMATCH)
        }

        val state = db.encounterDao().findState(packageName)
        val today = ServiceDate.of(now)
        val week = ServiceDate.weekKey(now)

        if (!rule.frequency.allows(state, now, today, week)) {
            return RuleResult.Skip(SkipReason.FREQUENCY_LIMIT)
        }

        return RuleResult.Show
    }

    /** 편이가 실제로 등장한 뒤 카운터를 올린다 */
    suspend fun recordShown(packageName: String, now: Long) {
        val dao = db.encounterDao()
        val today = ServiceDate.of(now)
        val week = ServiceDate.weekKey(now)
        val prev = dao.findState(packageName)

        val next = when {
            prev == null -> com.whybuy.app.data.entity.EncounterStateEntity(
                packageName = packageName,
                lastShownAt = now,
                todayCount = 1,
                todayDate = today,
                weekCount = 1,
                weekKey = week,
                consecutiveSkip = 0
            )
            else -> prev.copy(
                lastShownAt = now,
                todayCount = if (prev.todayDate == today) prev.todayCount + 1 else 1,
                todayDate = today,
                weekCount = if (prev.weekKey == week) prev.weekCount + 1 else 1,
                weekKey = week
            )
        }

        dao.upsertState(next)
        Log.d(TAG, "카운터 갱신 · today=${next.todayCount} week=${next.weekCount}")
    }

    companion object {
        private const val TAG = "WhyBuy"
    }
}