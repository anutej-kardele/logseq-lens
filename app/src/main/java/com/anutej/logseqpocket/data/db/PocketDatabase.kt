package com.anutej.logseqpocket.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DatasetMetaEntity::class, PageEntity::class, BlockEntity::class, BlockFts::class, TagEntity::class,
        BlockTagEntity::class, PageTagEntity::class, BlockRefEntity::class, CardEntity::class,
        CardCategoryEntity::class, DeckEntity::class, ReviewProgressEntity::class, ReviewSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun importDao(): ImportDao
    abstract fun notesDao(): NotesDao
    abstract fun searchDao(): SearchDao
    abstract fun studyDao(): StudyDao

    companion object {
        const val NAME = "logseq-pocket.db"

        fun create(context: Context): PocketDatabase =
            Room.databaseBuilder(context.applicationContext, PocketDatabase::class.java, NAME)
                // Content can always be re-imported, but review progress cannot: never use destructive
                // migration. Add explicit Migrations when the schema version increases.
                .build()
    }
}
