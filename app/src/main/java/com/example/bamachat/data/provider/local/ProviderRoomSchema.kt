package com.example.bamachat.data.provider.local

import androidx.sqlite.db.SupportSQLiteDatabase

object ProviderRoomSchema {
    fun createTables(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `providers` (" +
                "`providerId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `connectionType` TEXT NOT NULL, " +
                "`baseUrl` TEXT NOT NULL, `authenticationType` TEXT NOT NULL, `defaultModelId` TEXT, " +
                "`streaming` INTEGER NOT NULL, `modelDiscovery` INTEGER NOT NULL, `tools` INTEGER NOT NULL, " +
                "`vision` INTEGER NOT NULL, `timeoutMs` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                "`builtIn` INTEGER NOT NULL, `localHttpConfirmed` INTEGER NOT NULL, `hasSecret` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`providerId`))"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `provider_models` (" +
                "`providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`providerId`, `modelId`), " +
                "FOREIGN KEY(`providerId`) REFERENCES `providers`(`providerId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_provider_models_providerId` ON `provider_models` (`providerId`)"
        )
    }
}
