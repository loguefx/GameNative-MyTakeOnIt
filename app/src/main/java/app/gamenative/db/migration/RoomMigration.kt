package app.gamenative.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val DROP_TABLE = "DROP TABLE IF EXISTS " // Trailing Space

internal val ROOM_MIGRATION_V7_to_V8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        // Dec 5, 2025: Friends and Chat features removed
        connection.execSQL(DROP_TABLE + "chat_message")
        connection.execSQL(DROP_TABLE + "emoticon")
        connection.execSQL(DROP_TABLE + "steam_friend")
    }
}

private const val CREATE_CHAT_MESSAGE = """
    CREATE TABLE IF NOT EXISTS chat_message (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        steam_id_friend INTEGER NOT NULL,
        from_local INTEGER NOT NULL,
        message TEXT NOT NULL,
        timestamp INTEGER NOT NULL
    )
"""

internal val ROOM_MIGRATION_V13_to_V14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(CREATE_CHAT_MESSAGE)
    }
}
