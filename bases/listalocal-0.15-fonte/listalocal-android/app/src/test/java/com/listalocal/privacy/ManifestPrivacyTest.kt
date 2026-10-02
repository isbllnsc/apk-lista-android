package com.listalocal.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Falha se o manifesto de origem declarar qualquer permissao de rede. Roda em
 * JVM (sem build) — a rede executa ainda o gate Gradle assertNoInternetPermission
 * sobre o manifesto MESCLADO. Duas travas independentes.
 */
class ManifestPrivacyTest {

    private val manifest: String by lazy {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        candidates.first { it.exists() }.readText()
    }

    // Ignora comentarios XML (que citam INTERNET so para explicar o gate).
    private val manifestNoComments: String by lazy {
        manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    private fun declaresPermission(perm: String): Boolean =
        manifestNoComments.contains(Regex("""uses-permission[^>]*android:name="$perm""""))

    @Test fun `sem permissao INTERNET`() {
        assertFalse(declaresPermission("android.permission.INTERNET"))
    }

    @Test fun `sem permissoes de estado de rede`() {
        assertFalse(declaresPermission("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(declaresPermission("android.permission.ACCESS_WIFI_STATE"))
    }

    /**
     * O servico so pode observar os apps alvo — e nenhum outro. A lista e
     * fechada: se alguem acrescentar um pacote (ou remover o atributo, o que
     * faria o servico observar o aparelho inteiro), este teste quebra.
     */
    @Test fun `servico de acessibilidade restrito aos apps alvo`() {
        val config = File("src/main/res/xml/accessibility_service_config.xml")
            .takeIf { it.exists() }
            ?: File("app/src/main/res/xml/accessibility_service_config.xml")
        val text = config.readText()
        val declarados = Regex("""android:packageNames="([^"]+)"""")
            .find(text)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        assertEquals(
            "o servico deve observar exatamente os pacotes de TargetApp",
            setOf("com.whatsapp", "com.whatsapp.w4b"),
            declarados,
        )
    }

    /** O manifesto precisa poder enxergar os dois pacotes (Android 11+). */
    @Test fun `queries declara os dois apps alvo`() {
        assertTrue(manifestNoComments.contains("""<package android:name="com.whatsapp" />"""))
        assertTrue(manifestNoComments.contains("""<package android:name="com.whatsapp.w4b" />"""))
    }

    @Test fun `backup desabilitado para dados sensiveis`() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }
}
