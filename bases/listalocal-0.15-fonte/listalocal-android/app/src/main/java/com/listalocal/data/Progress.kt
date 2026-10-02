package com.listalocal.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * Progresso local para retomada incremental. Guardamos SOMENTE hash do numero
 * (nunca o E.164 em claro) + id local do contato + desfecho. Retomar = pular o
 * que ja tem desfecho neste lote.
 */
@Entity(tableName = "batch_progress")
data class BatchProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchIndex: Int,
    val numberHash: String,      // SHA-256 do E.164 (via Redaction/keystore)
    val contactLocalId: Long,
    val outcome: String,         // selected | not_found | ambiguous | already_selected
    val updatedAt: Long,
)

@Dao
interface BatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: BatchProgress)

    @Query("SELECT numberHash FROM batch_progress WHERE batchIndex = :batchIndex")
    suspend fun processedHashes(batchIndex: Int): List<String>

    @Query("DELETE FROM batch_progress WHERE batchIndex = :batchIndex")
    suspend fun clearBatch(batchIndex: Int)
}

@Database(entities = [BatchProgress::class], version = 1, exportSchema = false)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun progressDao(): BatchProgressDao
}
