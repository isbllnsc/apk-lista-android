package com.listalocal.e2e

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.listalocal.core.followers.Follower
import com.listalocal.core.ig.Evidence
import com.listalocal.core.ig.Evidence.Conversa
import com.listalocal.core.ig.Listas
import com.listalocal.core.privacy.Redaction
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.service.DmFlow
import com.listalocal.service.FonteConversas
import com.listalocal.service.FonteSeguidores
import com.listalocal.service.sairDaConversa
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CONFERENCIA NO CELULAR REAL, SEM DIGITAR NADA. O mesmo motor do servico
 * (NodeOps + DmFlow.abrirEProvar + FonteSeguidores + FonteConversas) sobre o
 * UiAutomation ([TelaReal]):
 *  1. cada @ de -e provar: abre pelo link e prova o @ (Conversa.PROVADA);
 *  2. cada @ de -e ausentes: "nao encontrado" (a conversa nao abre em 5 s);
 *  3. Perfil > Seguidores: 3 telas lidas (rolando so a lista);
 *  4. a caixa de entrada (Direct), onde o celular termina.
 * Tempos no logcat, tag E2E. Nenhum @, nome ou texto no log: cada caso e c1, c2...
 * com Redaction.mask ao lado.
 *
 * Nao roda sozinho: so com -e real 1 (am instrument). Um connectedAndroidTest comum pula.
 */
@RunWith(AndroidJUnit4::class)
class ConferenciaRealTest {
    private val prof = SelectorProfile.IG_448
    private fun log(s: String) = Log.i(TelaReal.TAG, s)

    @Test
    fun confereSemDigitar() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("so com -e real 1", args.getString("real") == "1")
        val provar = lista(args.getString("provar"))
        val ausentes = lista(args.getString("ausentes"))
        val t = TelaReal()
        val falhas = mutableListOf<String>()
        val inicio = t.agora()
        log("=== conferencia inicio sdk=${Build.VERSION.SDK_INT} ${Build.MODEL}")
        try {
            val flow = DmFlow(t, prof, SemRegistro)
            (provar.map { it to true } + ausentes.map { it to false }).forEachIndexed { i, (u, existe) ->
                val c = "c${i + 1}"
                val a = t.agora()
                val p = flow.abrirEProvar(Follower(u, ""))
                val ms = t.agora() - a
                val ok = if (existe) p.erro == null && p.conversa == Conversa.PROVADA else p.naoEncontrado
                log("[$c] ${Redaction.mask(u)} esperado=${if (existe) "PROVADA" else "NAO_ENCONTRADO"} " +
                    "conversa=${p.conversa} naoEncontrado=${p.naoEncontrado} erro=${p.erro} ms=$ms ${if (ok) "OK" else "FALHOU"}")
                if (!ok) falhas += c
                val s = t.agora()
                val saiu = t.sairDaConversa(prof)
                log("[$c] saiu=$saiu ms=${t.agora() - s}")
                if (!saiu) falhas += "$c-sair"
            }

            // Seguidores: 3 telas. Cada tela lida e marcada como feita; a proxima rola a lista.
            val fs = FonteSeguidores(t, prof)
            var a = t.agora()
            val conta = runCatching { fs.abrir() }.onFailure { log("seguidores abrir erro=${it.message}") }.getOrNull()
            log("seguidores abrir conta=${conta?.let(Redaction::mask)} ms=${t.agora() - a}")
            if (conta == null) falhas += "seguidores-abrir"
            else {
                val feitos = HashSet<String>()
                var telas = 0
                for (n in 1..3) {
                    a = t.agora()
                    val lote = runCatching { fs.proximas { it in feitos } }
                        .onFailure { log("seguidores tela=$n erro=${it.message}") }
                    val linhas = lote.getOrNull()
                    log("seguidores tela=$n linhas=${linhas?.size ?: "fim"} ms=${t.agora() - a}")
                    if (lote.isFailure) { falhas += "seguidores-tela$n"; break }
                    if (linhas == null) break // a lista acabou antes (poucos seguidores)
                    telas++
                    feitos += linhas.mapNotNull { it.username }
                }
                log("seguidores telas=$telas pessoas=${feitos.size}")
                if (telas == 0) falhas += "seguidores-vazio"
            }

            // Caixa de entrada: so a leitura das linhas (abrir uma conversa a marcaria como lida).
            a = t.agora()
            val fc = FonteConversas(t, prof)
            val contaCaixa = runCatching { fc.abrir() }.onFailure { log("caixa erro=${it.message}") }.getOrNull()
            val r = t.ler()
            val linhas = r?.let { Listas.conversas(it, prof) }.orEmpty()
            log("caixa conta=${contaCaixa?.let(Redaction::mask)} linhas=${linhas.size} " +
                "temConversa=${r?.let { Evidence.temConversa(it, prof) }} ms=${t.agora() - a}")
            if (contaCaixa == null || linhas.isEmpty()) falhas += "caixa"
        } finally {
            t.fechar()
            log("=== conferencia fim ms=${t.agora() - inicio} falhas=$falhas ${if (falhas.isEmpty()) "OK" else "FALHOU"}")
        }
        assertTrue("falhas: $falhas (detalhes no logcat, tag E2E)", falhas.isEmpty())
    }
}

internal fun lista(s: String?): List<String> =
    s.orEmpty().split(',').map { it.trim().removePrefix("@").lowercase() }.filter { it.isNotEmpty() }

/** A conferencia nao escreve nada: nada a gravar. */
internal object SemRegistro : com.listalocal.service.Registro {
    override fun marcarCommit(username: String) = false
    override fun gravar(username: String, desfecho: com.listalocal.service.Desfecho, motivo: String, enviadas: List<Int>) = Unit
}
