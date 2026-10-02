package com.listalocal.e2e

import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.listalocal.core.followers.Follower
import com.listalocal.core.privacy.Redaction
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.service.Desfecho
import com.listalocal.service.DmFlow
import com.listalocal.service.Registro
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Envio pedido pelo dono a uma lista de @ (os seguidores da conta aberta), pelo MESMO motor do
 * servico: prova do @ antes de escrever, texto inteiro de uma vez (com acento), um toque, evidencia
 * depois. Sem evidencia = falha e nunca reenvia. Retoma pelo arquivo de feitos (quem ja teve desfecho
 * nunca e aberto de novo). Para sozinho em sinal de restricao ou 3 falhas seguidas.
 *
 * So roda com: -e alvos a,b,c -e texto64 <base64 UTF-8> -e intervalo <s>.
 */
@RunWith(AndroidJUnit4::class)
class EnvioListaTest {
    private fun log(s: String) = Log.i(TAG, s)

    @Test
    fun enviaParaALista(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        // alvos64: base64 UTF-8 de linhas "@<TAB>nome" (o nome prova quando o cabecalho troca o @ por "Online agora").
        val pares: List<Pair<String, String>> = args.getString("alvos64")?.let { b ->
            String(Base64.decode(b, Base64.DEFAULT), Charsets.UTF_8).lines().filter { it.isNotBlank() }
                .map { l -> l.substringBefore('\t').trim().lowercase() to l.substringAfter('\t', "").trim() }
        } ?: lista(args.getString("alvos")).map { it to "" }
        val alvos = pares.map { it.first }
        val nomes = pares.toMap()
        val texto = args.getString("texto64")?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.orEmpty()
        val intervaloMs = (args.getString("intervalo")?.toLongOrNull() ?: 30L) * 1000
        assumeTrue("so com -e alvos e -e texto64", alvos.isNotEmpty() && texto.isNotBlank())

        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val feitosArq = File(dir, "envio-lista-feitos.txt")
        // Nunca de novo: toque gravado (COMMIT), recebeu, pode ter recebido ou pediu para parar.
        // FALHA/NAO_ENCONTRADO antes do toque podem ser tentadas de novo (nada saiu).
        val finais = setOf("COMMIT", "ENVIADO", "INCERTO", "PEDIU_PARA_PARAR", "INDISPONIVEL")
        val feitos = if (feitosArq.exists()) feitosArq.readLines().filter { it.split('\t').getOrNull(1) in finais }
            .map { it.substringBefore('\t') }.toMutableSet() else mutableSetOf()
        val pendentes = alvos.filterNot { it in feitos }
        log("=== inicio total=${alvos.size} ja_feitos=${feitos.size} pendentes=${pendentes.size} intervalo=${intervaloMs / 1000}s texto=${texto.length}c")

        val t = TelaReal()
        var falhasSeguidas = 0
        var n = 0
        try {
            for (u in pendentes) {
                if (n > 0) delay(intervaloMs)
                n++
                val registro = object : Registro {
                    override fun marcarCommit(username: String): Boolean {
                        // Grava ANTES do toque: se o processo morrer, esta pessoa nunca recebe de novo.
                        feitosArq.appendText("$username\tCOMMIT\n")
                        return true
                    }
                    override fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int>) {}
                }
                val r = DmFlow(t, SelectorProfile.IG_448, registro).enviar(Follower(u, nomes[u].orEmpty()), texto)
                feitosArq.appendText("$u\t${r.desfecho}\t${r.motivo.replace('\t', ' ').replace('\n', ' ')}\n")
                log("[$n/${pendentes.size}] ${Redaction.mask(u)} ${r.desfecho} ${r.motivo}")
                if (r.desfecho == Desfecho.ENVIADO || r.desfecho == Desfecho.PULADO) falhasSeguidas = 0
                else if (r.desfecho.falha) falhasSeguidas++
                val restricao = listOf("restri", "tente novamente", "try again", "bloque", "limit").any { r.motivo.contains(it, ignoreCase = true) }
                if (restricao) { log("=== PAROU: sinal de restricao do Instagram: ${r.motivo}"); break }
                if (falhasSeguidas >= 3) { log("=== PAROU: 3 falhas seguidas"); break }
            }
        } finally {
            t.fechar()
        }
        log("=== fim processados=$n")
    }

    companion object {
        const val TAG = "ENVIOLISTA"
    }
}
