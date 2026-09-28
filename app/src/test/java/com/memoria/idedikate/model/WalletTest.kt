package com.memoria.idedikate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletTest {

    @Test
    fun `migrates a legacy wallet that has both old fields`() {
        val update = Wallet.legacyMigration(mapOf("tokens" to 3L, "fruit" to 4L, "food" to 2L))!!

        assertEquals(4L, update["flowers"])
        assertEquals(2L, update["candles"])
        assertEquals(setOf("flowers", "candles", "fruit", "food"), update.keys)
    }

    @Test
    fun `leaves up-to-date wallets alone`() {
        assertNull(Wallet.legacyMigration(mapOf("tokens" to 3L, "flowers" to 4L, "candles" to 2L)))
    }

    @Test
    fun `skips wallets the rules wouldn't let it migrate`() {
        // Only one old field, or old and new fields together: the rules reject these renames
        assertNull(Wallet.legacyMigration(mapOf("fruit" to 4L)))
        assertNull(Wallet.legacyMigration(mapOf("fruit" to 4L, "food" to 2L, "flowers" to 1L)))
    }
}
