package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity

@Dao
interface MessageNodeDAO {
    // 只取含本地文件地址的行，附件清理用；整张表读进内存没必要
    @Query("SELECT messages FROM message_node WHERE instr(messages, 'file:') > 0")
    suspend fun getContentsWithFileReferences(): List<String>

    @Query(
        "SELECT * FROM message_node WHERE conversation_id = :conversationId " +
            "ORDER BY node_index ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getNodesOfConversationPaged(
        conversationId: String,
        limit: Int,
        offset: Int
    ): List<MessageNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<MessageNodeEntity>)

    @Query("DELETE FROM message_node WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM message_node WHERE id = :nodeId")
    suspend fun deleteById(nodeId: String)

    // 分支切换只改这一列，不重新编码消息 JSON；返回受影响行数
    @Query(
        "UPDATE message_node SET select_index = :selectIndex " +
            "WHERE id = :nodeId AND conversation_id = :conversationId"
    )
    suspend fun updateSelectIndex(conversationId: String, nodeId: String, selectIndex: Int): Int

    // 消息节点数（重新生成的分支不另算）
    @Query("SELECT COUNT(*) FROM message_node")
    suspend fun countAll(): Int
}
