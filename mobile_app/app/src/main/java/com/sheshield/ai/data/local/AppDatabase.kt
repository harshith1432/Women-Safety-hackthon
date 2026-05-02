package com.sheshield.ai.data.local

import android.content.Context
import androidx.room.*
import com.sheshield.ai.data.*

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts")
    suspend fun getAll(): List<EmergencyContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContact)

    @Delete
    suspend fun delete(contact: EmergencyContact)
}

@Dao
interface SafetyLogDao {
    @Query("SELECT * FROM safety_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<SafetyLog>

    @Query("SELECT * FROM safety_logs WHERE isSynced = 0")
    suspend fun getUnsynced(): List<SafetyLog>

    @Insert
    suspend fun insert(log: SafetyLog)

    @Update
    suspend fun update(log: SafetyLog)
}

@Dao
interface CommunityZoneDao {
    @Query("SELECT * FROM community_zones")
    suspend fun getAll(): List<CommunityZone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: CommunityZone)

    @Query("DELETE FROM community_zones WHERE zoneId = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [EmergencyContact::class, SafetyLog::class, CommunityZone::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): EmergencyContactDao
    abstract fun logDao(): SafetyLogDao
    abstract fun communityZoneDao(): CommunityZoneDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sheshield_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
