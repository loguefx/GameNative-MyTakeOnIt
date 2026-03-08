package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gamenative.db.entity.CachedAchievement
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedAchievementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: CachedAchievement)

    @Query("SELECT * FROM cached_achievement WHERE app_id = :appId AND steam_id = :steamId LIMIT 1")
    fun get(appId: Int, steamId: Long): Flow<CachedAchievement?>

    @Query("SELECT * FROM cached_achievement WHERE app_id = :appId AND steam_id = :steamId LIMIT 1")
    suspend fun getOnce(appId: Int, steamId: Long): CachedAchievement?
}
