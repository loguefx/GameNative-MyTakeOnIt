package app.gamenative.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_achievement", primaryKeys = ["app_id", "steam_id"])
data class CachedAchievement(
    @ColumnInfo(name = "app_id")
    val appId: Int,
    @ColumnInfo(name = "steam_id")
    val steamId: Long,
    @ColumnInfo(name = "schema_json")
    val schemaJson: String?,
    @ColumnInfo(name = "player_json")
    val playerJson: String?,
    @ColumnInfo(name = "schema_fetched_at")
    val schemaFetchedAt: Long,
    @ColumnInfo(name = "player_fetched_at")
    val playerFetchedAt: Long,
)
