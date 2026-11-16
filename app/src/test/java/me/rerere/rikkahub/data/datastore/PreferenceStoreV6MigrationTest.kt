package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.migration.CURRENT_SETTINGS_VERSION
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV6Migration
import me.rerere.rikkahub.data.datastore.migration.SETTINGS_VERSION_KEY
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.datastore.migration.migrateNetworkSettingAutoRetry
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreV6MigrationTest {
    @Test
    fun `legacy auto retry is turned off without losing other fields`() {
        val migrated = migrateNetworkSettingAutoRetry(
            """{"enableAutoRetry":true,"maxRetries":7,"proxyUrl":"http://proxy"}"""
        )
        val decoded = JsonInstant.decodeFromString<NetworkSetting>(migrated)

        assertFalse(decoded.enableAutoRetry)
        assertEquals(7, decoded.maxRetries)
        assertEquals("http://proxy", decoded.proxyUrl)
    }

    @Test
    fun `malformed network json is left untouched`() {
        assertEquals("not json", migrateNetworkSettingAutoRetry("not json"))
    }

    @Test
    fun `data migration is gated by version and writes version six`() = runBlocking {
        val migration = PreferenceStoreV6Migration()
        val legacy = preferencesOf(
            SettingsStore.NETWORK_SETTING to """{"enableAutoRetry":true,"maxRetries":7}"""
        )

        assertTrue(migration.shouldMigrate(legacy))
        val migrated = migration.migrate(legacy)

        assertEquals(6, migrated[SettingsStore.VERSION])
        assertFalse(
            JsonInstant.decodeFromString<NetworkSetting>(migrated[SettingsStore.NETWORK_SETTING]!!).enableAutoRetry
        )

        val alreadyMigrated = preferencesOf(SettingsStore.VERSION to 6)
        assertFalse(migration.shouldMigrate(alreadyMigrated))
    }

    @Test
    fun `legacy backup without a settings marker disables auto retry`() {
        val migrated = JsonInstant.parseToJsonElement(
            SettingsJsonMigrator.migrate("""{"networkSetting":{"enableAutoRetry":true,"maxRetries":7}}""")
        ).jsonObject

        assertEquals(CURRENT_SETTINGS_VERSION, migrated[SETTINGS_VERSION_KEY]?.jsonPrimitive?.intOrNull)
        assertFalse(
            JsonInstant.decodeFromString<NetworkSetting>(migrated["networkSetting"].toString()).enableAutoRetry
        )
    }

    @Test
    fun `new backup with an explicit opt-in keeps auto retry enabled`() {
        val migrated = JsonInstant.parseToJsonElement(
            SettingsJsonMigrator.migrate(
                """{"settingsVersion":6,"networkSetting":{"enableAutoRetry":true,"maxRetries":7}}"""
            )
        ).jsonObject

        assertTrue(
            JsonInstant.decodeFromString<NetworkSetting>(migrated["networkSetting"].toString()).enableAutoRetry
        )
    }
}
