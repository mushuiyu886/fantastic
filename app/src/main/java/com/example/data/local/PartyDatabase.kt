package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedResourcePackEntity::class,
        SessionSnapshotEntity::class,
        PartyMatchRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PartyDatabase : RoomDatabase() {
    abstract fun resourcePackDao(): ResourcePackDao
    abstract fun sessionSnapshotDao(): SessionSnapshotDao
    abstract fun matchRecordDao(): MatchRecordDao

    companion object {
        @Volatile
        private var INSTANCE: PartyDatabase? = null

        fun getInstance(context: Context): PartyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PartyDatabase::class.java,
                    "bump_party_offline.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
