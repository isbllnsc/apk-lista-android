package com.listalocal.real

import com.listalocal.core.ig.Evidence
import com.listalocal.real.Perfis.APARELHO
import org.junit.Test

/**
 * Deteccao da conta aberta sobre as telas REAIS da conta do dono ("minhaconta").
 * Historico: o app parou em seguranca numa situacao valida porque leu a conta
 * errada (0.1.2). Aqui: toda leitura de conta, em toda tela real, ou acha
 * "minhaconta" ou nao acha nada. Qualquer outro texto viraria "o Instagram
 * mudou para a conta @X" / "a conferencia de @X nao passou".
 */
class ContaRealTest {

    private fun conta(nome: String, key: String) = Evidence.conta(Telas.raiz(nome), APARELHO, key)

    @Test fun `perfil proprio - o titulo do perfil e a conta aberta`() =
        emTodas(Telas.PERFIL, "profile_title no perfil proprio") { n, _ ->
            soMinhaConta(conta(n, "profile_title"))
        }

    @Test fun `caixa de entrada - o titulo e a conta aberta`() =
        emTodas(Telas.CAIXA + Telas.PEDIDOS, "inbox_title na caixa de entrada") { n, _ ->
            soMinhaConta(conta(n, "inbox_title"))
        }

    @Test fun `lista de seguidores - o titulo da barra e a conta (FonteSeguidores le a conta ali)`() =
        emTodas(Telas.SEGUIDORES + Telas.SO_SUGESTOES + Telas.SEGUINDO + Telas.SINALIZADAS, "profile_title na lista") { n, _ ->
            soMinhaConta(conta(n, "profile_title"))
        }

    /**
     * DmFlow.conferir (a conferencia e o inicio de TODA operacao DM: contaAberta) le
     * inbox_title logo depois de tocar em Direct, na tela que estiver na frente; o
     * Conta.esperarOutra le profile_title e depois inbox_title. Em nenhuma tela da
     * conta do dono pode sair outra conta.
     */
    @Test fun `nenhuma tela real da conta do dono devolve outra conta`() =
        emTodas(Telas.PRINCIPAIS, "Evidence.conta so pode dar 'minhaconta' ou null") { n, _ ->
            listOf("inbox_title", "profile_title").mapNotNull { k ->
                conta(n, k)?.takeIf { it != "minhaconta" }?.let { "$k leu @$it" }
            }.takeIf { it.isNotEmpty() }?.joinToString()
        }

    @Test fun `titulo da caixa por busca direta de id (contaAgora) e a conta`() =
        emTodas(Telas.CAIXA, "textosPorId(igds_action_bar_title)") { _, r ->
            soMinhaConta(Evidence.contaDe(r.findByViewIdSuffix("igds_action_bar_title").mapNotNull { it.text ?: it.contentDescription }))
        }
}
