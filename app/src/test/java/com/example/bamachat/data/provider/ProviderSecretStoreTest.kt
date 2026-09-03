package com.example.bamachat.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSecretStoreTest {
    @Test
    fun storesLoadsChecksAndDeletesSeparatedCustomSecrets() {
        val backend = FakeSecretBackend()
        val store = ProviderSecretStore(backend)
        val first = ProviderId.newCustom()
        val second = ProviderId.newCustom()
        val firstValue = generatedValue('a')
        val secondValue = generatedValue('b')

        store.put(first, firstValue)
        store.put(second, secondValue)

        assertTrue(store.contains(first))
        assertEquals(firstValue, store.get(first))
        assertEquals(secondValue, store.get(second))
        assertNotEquals(store.aliasForTesting(first), store.aliasForTesting(second))
        assertFalse(backend.values.keys.any { it.contains(first.value) || it.contains(second.value) })

        store.remove(first)
        assertFalse(store.contains(first))
        assertNull(store.get(first))
        assertEquals(secondValue, store.get(second))
    }

    @Test
    fun clearRemovesOnlyCustomAliasesAndBuiltInsAreNeverTouched() {
        val backend = FakeSecretBackend(mutableMapOf("existing_builtin_alias" to generatedValue('x')))
        val store = ProviderSecretStore(backend)
        store.put(ProviderId.newCustom(), generatedValue('c'))

        store.clearCustomSecrets()

        assertEquals(setOf("existing_builtin_alias"), backend.values.keys)
        assertThrows(ProviderSecretStoreException::class.java) {
            store.put(ProviderId(ProviderId.OPENROUTER), generatedValue('d'))
        }
    }

    @Test
    fun secretNeverAppearsInStoreStringOrSafeException() {
        val backend = FakeSecretBackend(failWrites = true)
        val store = ProviderSecretStore(backend)
        val value = generatedValue('q')
        val exception = assertThrows(ProviderSecretStoreException::class.java) {
            store.put(ProviderId.newCustom(), value)
        }
        assertFalse(store.toString().contains(value))
        assertFalse(exception.message.orEmpty().contains(value))
    }

    private fun generatedValue(character: Char): String = CharArray(24) { character }.concatToString()
}

private class FakeSecretBackend(
    val values: MutableMap<String, String> = linkedMapOf(),
    private val failWrites: Boolean = false
) : ProviderSecretBackend {
    override fun put(alias: String, value: String) {
        if (failWrites) error("synthetic")
        values[alias] = value
    }
    override fun get(alias: String): String? = values[alias]
    override fun contains(alias: String): Boolean = values.containsKey(alias)
    override fun remove(alias: String) {
        values.remove(alias)
    }
    override fun aliases(): Set<String> = values.keys.toSet()
}
