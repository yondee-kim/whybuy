package com.whybuy.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.whybuy.app.data.entity.AppRuleEntity
import com.whybuy.app.data.entity.EncounterLogEntity
import com.whybuy.app.data.entity.EncounterStateEntity
import com.whybuy.app.data.entity.TargetAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetAppDao {

    @Upsert
    suspend fun upsert(app: TargetAppEntity)

    @Query("SELECT * FROM target_app WHERE enabled = 1")
    suspend fun getEnabled(): List<TargetAppEntity>

    @Query("SELECT * FROM target_app WHERE enabled = 1")
    fun observeEnabled(): Flow<List<TargetAppEntity>>

    @Query("SELECT * FROM target_app WHERE packageName = :pkg")
    suspend fun findByPackage(pkg: String): TargetAppEntity?

    @Query("DELETE FROM target_app WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface AppRuleDao {

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Query("SELECT * FROM app_rule WHERE packageName = :pkg")
    suspend fun findByPackage(pkg: String): AppRuleEntity?

    @Query("SELECT * FROM app_rule")
    fun observeAll(): Flow<List<AppRuleEntity>>
}

@Dao
interface EncounterDao {

    @Upsert
    suspend fun upsertState(state: EncounterStateEntity)

    @Query("SELECT * FROM encounter_state WHERE packageName = :pkg")
    suspend fun findState(pkg: String): EncounterStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: EncounterLogEntity): Long

    @Query("SELECT * FROM encounter_log ORDER BY shownAt DESC")
    fun observeLogs(): Flow<List<EncounterLogEntity>>

    @Query("SELECT COUNT(*) FROM encounter_log WHERE serviceDate = :date")
    suspend fun countByDate(date: String): Int

    @Query("DELETE FROM encounter_log WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM encounter_log")
    suspend fun deleteAllLogs()
}