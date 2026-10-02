package com.listalocal.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Falha se o manifesto de origem declarar permissao de rede ou de contatos, ou
 * se o servico puder observar outro app. Roda em JVM (sem build); o gate Gradle
 * assertNoInternetPermission confere ainda o manifesto MESCLADO.
 */
class ManifestPrivacyTest {

    private fun arquivo(caminho: String) = listOf(File(caminho), File("app/$caminho")).first { it.exists() }

    private val manifest: String by lazy { arquivo("src/main/AndroidManifest.xml").readText() }

    // Ignora comentarios XML (que citam as permissoes so para explicar o gate).
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

    @Test fun `sem contatos nem armazenamento`() {
        assertFalse(declaresPermission("android.permission.READ_CONTACTS"))
        assertFalse(declaresPermission("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(manifestNoComments.contains("uses-permission"))
    }

    /**
     * O servico so pode observar o Instagram — e nenhum outro. A lista e
     * fechada: se alguem acrescentar um pacote (ou remover o atributo, o que
     * faria o servico observar o aparelho inteiro), este teste quebra.
     */
    @Test fun `servico de acessibilidade restrito ao Instagram`() {
        val text = arquivo("src/main/res/xml/accessibility_service_config.xml").readText()
        val declarados = Regex("""android:packageNames="([^"]+)"""")
            .find(text)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        assertEquals(setOf("com.instagram.android"), declarados)
        // A conversa abre numa janela que a "janela ativa" nao mostra: precisa das janelas.
        assertTrue(text.contains("flagRetrieveInteractiveWindows"))
    }

    @Test fun `queries declara so o Instagram`() {
        val pacotes = Regex("""<package android:name="([^"]+)"""").findAll(manifestNoComments)
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("com.instagram.android"), pacotes)
    }

    /** O app do Codex usa com.listalocal.instagram: com o mesmo pacote, um apagaria o outro. */
    @Test fun `pacote e nome proprios para conviver com o app do Codex`() {
        assertTrue(arquivo("../app/build.gradle.kts").readText().contains("applicationId = \"com.listalocal.instagram.claude\""))
        assertTrue(arquivo("src/main/res/values/strings.xml").readText().contains(">Lista Local Instagram (Claude)<"))
    }

    @Test fun `backup desabilitado para dados sensiveis`() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }
}
