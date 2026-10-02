package com.listalocal.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetWindowPolicyTest {
    @Test
    fun acceptsOnlyTheInstagramPackage() {
        assertTrue(TargetWindowPolicy.allows("com.instagram.android", "com.instagram.android"))
        assertFalse(TargetWindowPolicy.allows("com.instagram.android", "com.instagram.lite"))
        assertFalse(TargetWindowPolicy.allows("com.instagram.android", "com.instagram.android.extra"))
        assertFalse(TargetWindowPolicy.allows("com.instagram.android", "com.whatsapp"))
        assertFalse(TargetWindowPolicy.allows("com.instagram.android", "com.listalocal.instagram"))
        assertFalse(TargetWindowPolicy.allows("com.instagram.android", null))
    }

    @Test
    fun refusesAnyUnlistedTargetEvenWhenTheWindowMatches() {
        assertFalse(TargetWindowPolicy.allows("com.whatsapp", "com.whatsapp"))
        assertFalse(TargetWindowPolicy.allows("com.example.other", "com.example.other"))
    }
}
