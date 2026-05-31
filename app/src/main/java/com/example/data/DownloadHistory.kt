package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "download_records")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val instagramId: String,
    val username: String,
    val type: String, // "Post" or "Story"
    val mediaType: String, // "image" or "video"
    val localUri: String, // Android Content URI or File path
    val originalUrl: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_records ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(record: DownloadRecord)

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteDownloadById(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM download_records WHERE instagramId = :instagramId)")
    suspend fun isAlreadyDownloaded(instagramId: String): Boolean
}

@Database(entities = [DownloadRecord::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getDatabase(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "download_history_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
