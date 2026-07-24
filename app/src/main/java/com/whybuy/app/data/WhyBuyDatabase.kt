package com.whybuy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.whybuy.app.data.dao.AppRuleDao
import com.whybuy.app.data.dao.EncounterDao
import com.whybuy.app.data.dao.TargetAppDao
import com.whybuy.app.data.entity.AppRuleEntity
import com.whybuy.app.data.entity.EncounterLogEntity
import com.whybuy.app.data.entity.EncounterStateEntity
import com.whybuy.app.data.entity.TargetAppEntity

@Database(
    entities = [
        TargetAppEntity::class,
        AppRuleEntity::class,
        EncounterStateEntity::class,
        EncounterLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class WhyBuyDatabase : RoomDatabase() {

    abstract fun targetAppDao(): TargetAppDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun encounterDao(): EncounterDao

    companion object {
        @Volatile
        private var instance: WhyBuyDatabase? = null

        fun get(context: Context): WhyBuyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WhyBuyDatabase::class.java,
                    "whybuy.db"
                ).build().also { instance = it }
            }
        }
    }
}