package com.listalocal.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetWindowPolicyTest {
    @Test
    fun acceptsOnlyTheSelectedWhatsAppPackage() {
        assertTrue(TargetWindowPolicy.allows("com.whatsapp", "com.whatsapp"))
        assertFalse(TargetWindowPolicy.allows("com.whatsapp", "com.whatsapp.w4b"))
        assertFalse(TargetWindowPolicy.allows("com.whatsapp", "com.whatsapp.extra"))
        assertFalse(TargetWindowPolicy.allows("com.whatsapp", null))
    }

    @Test
    fun acceptsBusinessOnlyWhenBusinessIsSelected() {
        assertTrue(TargetWindowPolicy.allows("com.whatsapp.w4b", "com.whatsapp.w4b"))
        assertFalse(TargetWindowPolicy.allows("com.whatsapp.w4b", "com.whatsapp"))
        assertFalse(TargetWindowPolicy.allows("com.whatsapp.w4b", "com.whatsapp.w4b.extra"))
    }

    @Test
    fun refusesAnyUnlistedTargetEvenWhenTheWindowMatches() {
        assertFalse(TargetWindowPolicy.allows("com.example.other", "com.example.other"))
    }
}
