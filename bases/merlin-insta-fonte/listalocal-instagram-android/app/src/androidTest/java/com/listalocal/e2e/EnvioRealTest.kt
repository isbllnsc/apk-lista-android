package com.listalocal.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.listalocal.core.followers.Follower
import com.listalocal.core.privacy.Redaction
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.service.Desfecho
import com.listalocal.service.DmFlow
import com.listalocal.service.Registro
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UM ENVIO REAL, so para a conta do dono. O motor inteiro do servico
 * (DmFlow.enviar sobre o NodeOps, via [TelaReal]): prova do @ antes de
 * escrever, texto inteiro de uma vez, "vou enviar" antes do toque, um toque,
 * evidencia depois. A conversa do dono tem mensagens temporarias: a opcao
 * explicita aceitarTemporaria vai ligada SO aqui.
 *
 * Sem evidencia = falha, e nada e reenviado: o teste chama enviar uma vez.
 * Nao roda sozinho: so com -e enviar <@ do dono> -e texto <texto>.
 */
@RunWith(AndroidJUnit4::class)
class EnvioRealTest {
    private fun log(s: String) = Log.i(TelaReal.TAG, s)

    @Test
    fun enviaUmaVez() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val u = lista(args.getString("enviar")).singleOrNull()
        assumeTrue("so com -e enviar <@>", u != null)
        // Um @ trocado nunca recebe: so a conta do dono.
        assertEquals("o envio real e so para a conta do dono", DONO, u)
        val texto = args.getString("texto").orEmpty()
        assumeTrue("so com -e texto <texto>", texto.isNotBlank())

        val t = TelaReal()
        val registro = object : Registro {
            override fun marcarCommit(username: String): Boolean {
                log("[envio] vou enviar ${Redaction.mask(username)} t=${t.agora()}")
                return true
            }
            override fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int>) {
                log("[envio] desfecho=$desfecho motivo=$motivo")
            }
        }
        val a = t.agora()
        log("=== envio inicio ${Redaction.mask(DONO)} texto=${texto.length}c${Redaction.mask(texto)}")
        val r = try {
            DmFlow(t, SelectorProfile.IG_448, registro).enviar(Follower(DONO, ""), texto, aceitarTemporaria = true)
        } finally {
            t.fechar()
        }
        val ms = t.agora() - a
        log("=== envio fim desfecho=${r.desfecho} trilha=${r.trilha} tocou=${r.tocouEm?.let { it - a }} ms=$ms " +
            if (r.desfecho == Desfecho.ENVIADO) "OK" else "FALHOU (nao reenviar)")
        assertEquals(r.motivo, Desfecho.ENVIADO, r.desfecho)
    }

    companion object {
        /** A conta do proprio dono (E2E-REAL.md, passo 5). */
        const val DONO = "jvsgirao"
    }
}
