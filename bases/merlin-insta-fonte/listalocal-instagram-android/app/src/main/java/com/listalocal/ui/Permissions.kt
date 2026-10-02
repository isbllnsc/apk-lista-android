package com.listalocal.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.listalocal.service.InstagramAccessibilityService

/** Utilitarios de permissao: servico de acessibilidade. */
object Permissions {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, InstagramAccessibilityService::class.java)
        if (Settings.Secure.getInt(
                context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0,
            ) != 1
        ) return false
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').asSequence()
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }
}
