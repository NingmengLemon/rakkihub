package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.rerere.rikkahub.data.db.entity.FileDerivationEntity

@Dao
interface FileDerivationDAO {
    @Query(
        "SELECT * FROM file_derivations " +
            "WHERE file_id = :fileId AND kind = :kind AND producer = :producer"
    )
    suspend fun get(fileId: Long, kind: String, producer: String): FileDerivationEntity?

    @Query(
        "SELECT * FROM file_derivations " +
            "WHERE file_id IN (:fileIds) AND kind = :kind AND producer = :producer"
    )
    suspend fun listForFiles(fileIds: List<Long>, kind: String, producer: String): List<FileDerivationEntity>

    @Query(
        "SELECT * FROM file_derivations " +
            "WHERE file_id = :fileId AND kind = :kind " +
            "ORDER BY edited DESC, updated_at DESC LIMIT 1"
    )
    suspend fun getAny(fileId: Long, kind: String): FileDerivationEntity?

    @Query(
        "SELECT * FROM file_derivations " +
            "WHERE file_id IN (:fileIds) AND kind = :kind " +
            "ORDER BY edited DESC, updated_at DESC"
    )
    suspend fun listAny(fileIds: List<Long>, kind: String): List<FileDerivationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(derivation: FileDerivationEntity): Long

    @Update
    suspend fun update(derivation: FileDerivationEntity)

    @Query(
        "DELETE FROM file_derivations " +
            "WHERE file_id = :fileId AND kind = :kind AND producer = :producer"
    )
    suspend fun delete(fileId: Long, kind: String, producer: String): Int

    @Query("DELETE FROM file_derivations WHERE edited = 0")
    suspend fun clearGenerated(): Int

    @Query("DELETE FROM file_derivations")
    suspend fun clear(): Int
}
