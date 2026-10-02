package com.listalocal.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotGateTest {
    @Test fun locksOnlyAfterEveryPlannedListWasCreated() {
        assertTrue(OneShotGate.shouldConsume(completed = true, plannedLists = 3, createdLists = 3))
        assertFalse(OneShotGate.shouldConsume(completed = true, plannedLists = 3, createdLists = 2))
        assertFalse(OneShotGate.shouldConsume(completed = false, plannedLists = 3, createdLists = 3))
        assertFalse(OneShotGate.shouldConsume(completed = true, plannedLists = 0, createdLists = 0))
    }
}
