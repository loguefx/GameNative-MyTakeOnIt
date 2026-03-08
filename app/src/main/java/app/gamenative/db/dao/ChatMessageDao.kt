package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.gamenative.data.FriendMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_message WHERE steam_id_friend = :steamIdFriend ORDER BY timestamp ASC")
    fun getByFriend(steamIdFriend: Long): Flow<List<FriendMessage>>

    @Insert
    suspend fun insert(message: FriendMessage)
}
