package com.listalocal.real

import com.listalocal.core.ig.Evidence
import com.listalocal.core.ig.Evidence.Conversa
import com.listalocal.core.tree.ArvoreReal
import com.listalocal.core.tree.UiGroup
import com.listalocal.core.tree.UiNode
import com.listalocal.core.tree.ig
import com.listalocal.real.Perfis.APARELHO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O envio (modo DM) sobre as conversas REAIS da rodada 1 (dump de Views, anonimizado): o que o app decide antes de
 * escrever e depois do toque em Enviar. Os textos de bolha sao inventados (o dump nao traz texto); a estrutura, os ids
 * e as posicoes sao os da tela real.
 */
class ConversaRealTest {

    /** A conversa real com mensagens temporarias: duas bolhas de texto (direct_text_message_text_view) e reels. */
    private fun comBolha(texto: String): UiNode =
        ArvoreReal.comRotuloNoId(Telas.raiz("v_conversa_temporaria"), "direct_text_message_text_view", texto = texto)

    // ---------------- avisos do Instagram x texto das bolhas ----------------

    @Test fun `bolha de seguidor com as palavras de um aviso nao e aviso do Instagram`() {
        assertTrue(Evidence.temMensagens(comBolha("x"), APARELHO))
        assertFalse("restricao", Evidence.restricao(comBolha("o site caiu, tente novamente mais tarde"), APARELHO))
        assertFalse("nao enviada", Evidence.falhaDeEnvio(comBolha("Sua encomenda foi Não enviada ainda?"), APARELHO))
        assertNotEquals(Conversa.INDISPONIVEL, Evidence.conversa(comBolha("sou usuário do Instagram faz tempo"), APARELHO, "pessoa01"))
        assertFalse(Evidence.indisponivel(comBolha("ele não pode receber mensagens agora"), APARELHO))
    }

    @Test fun `nossa propria mensagem com as palavras de um aviso nao vira restricao nem falha`() {
        val nosso = "Se o link não abrir, tente novamente mais tarde. Mensagem não enviada? Responda aqui."
        val r = comBolha(nosso)
        assertFalse(Evidence.restricao(r, APARELHO))
        assertFalse(Evidence.falhaDeEnvio(r, APARELHO, listOf(nosso)))
    }

    @Test fun `aviso de verdade continua valendo - marcador embaixo da bolha, faixa e dialogo`() {
        // O marcador inteiro num no da lista (o status embaixo da bolha).
        val marcador = ArvoreReal.comRotuloNoId(Telas.raiz("v_conversa"), "seen_state_text", texto = "Não enviada. Toque para tentar novamente")
        assertTrue(Evidence.falhaDeEnvio(marcador, APARELHO))
        // A nossa bolha com o marcador junto (Compose pode juntar): tirado o nosso texto, sobra so o marcador.
        val junto = ArvoreReal.comRotuloNoId(Telas.raiz("v_conversa"), "seen_state_text", texto = "Oi, tudo bem? · Não enviada")
        assertTrue(Evidence.falhaDeEnvio(junto, APARELHO, listOf("Oi, tudo bem?")))
        // Dialogo do Instagram numa janela por cima da conversa.
        val dialogo = UiGroup(listOf(Telas.raiz("v_conversa"), ig(id = "dialog_container", children = listOf(ig(text = "Tente novamente mais tarde")))))
        assertTrue(Evidence.restricao(dialogo, APARELHO))
        // A conta que nao recebe: o aviso fora das bolhas (no lugar do campo) ou o titulo "Usuario do Instagram".
        val titulo = ArvoreReal.comRotuloNoId(Telas.raiz("v_conversa"), "header_title", texto = "Usuário do Instagram")
        assertEquals(Conversa.INDISPONIVEL, Evidence.conversa(titulo, APARELHO, "pessoa01"))
    }

    // ---------------- campo e Enviar com ids diferentes (outro aparelho ou versao) ----------------

    /** A conversa com o campo digitado e os ids trocados; [descEnviar] = o rotulo que o botao Enviar exporia. */
    private fun digitada(tela: String, texto: String, descEnviar: String? = null): UiNode {
        var r = ArvoreReal.comRotuloNoId(Telas.raiz(tela), "row_thread_composer_edittext", texto = texto)
        if (descEnviar != null) r = ArvoreReal.comRotuloNoId(r, "row_thread_composer_send_button_container", descricao = descEnviar)
        return ArvoreReal.comIdsTrocados(r)
    }

    @Test fun `ids diferentes - com enviar no texto digitado o campo nunca vira o botao Enviar`() {
        // Teclado aberto (campo no meio da tela, t06e) e teclado fechado (campo no rodape, t10h).
        for (tela in listOf("v_conversa_com_texto", "v_conversa_temporaria")) {
            val r = digitada(tela, "Vou te enviar o catálogo")
            val enviar = Evidence.botaoEnviar(r, APARELHO)
            assertTrue("$tela: ${enviar?.descr()}", enviar == null || enviar.id() != "row_thread_composer_edittext_v2")
            val servico = Evidence.botaoEnviar(ArvoreReal.semNaoImportantes(r), APARELHO)
            assertTrue("$tela (servico): ${servico?.descr()}", servico == null || servico.id() != "row_thread_composer_edittext_v2")
        }
    }

    @Test fun `ids diferentes - campo digitado com teclado aberto e o Enviar rotulado sao achados`() {
        // t06e: a unica tela real com o Enviar (so aparece com texto no campo).
        val r = digitada("v_conversa_com_texto", "Vou te enviar o catálogo", descEnviar = "Enviar")
        for ((leitura, t) in listOf("views" to r, "servico" to ArvoreReal.semNaoImportantes(r))) {
            assertEquals(leitura, "row_thread_composer_send_button_container_v2", Evidence.botaoEnviar(t, APARELHO)?.id())
            assertEquals(leitura, "Vou te enviar o catálogo", Evidence.textoDoCampo(t, APARELHO))
        }
    }

    // ---------------- conversa nova (sem historico) ----------------

    @Test fun `conversa nova real - bandeja de figurinhas fora da lista de mensagens e reconhecida`() {
        // t07h: a bandeja "Diga 'ola' enviando uma figurinha" fica em message_composer_bar, nao em message_list.
        val nova = Telas.raiz("v_conversa_nova")
        val comTexto = ArvoreReal.comRotuloNoId(nova, "centered_text_view", texto = "Diga 'olá' enviando uma figurinha")
        assertTrue("pelo texto", Evidence.conversaNova(comTexto, APARELHO))
        assertTrue("sem texto lido (Enviar desabilitado, bandeja)", Evidence.conversaNova(nova, APARELHO))
        assertTrue("ids trocados, pelo texto", Evidence.conversaNova(ArvoreReal.comIdsTrocados(comTexto), APARELHO))
        assertTrue("como o servico le", Evidence.conversaNova(ArvoreReal.semNaoImportantes(comTexto), APARELHO))
        for (t in listOf("v_conversa", "v_conversa_com_texto", "v_conversa_temporaria")) {
            assertFalse(t, Evidence.conversaNova(Telas.raiz(t), APARELHO))
        }
        // Uma bolha de seguidor com as mesmas palavras nao faz da conversa uma conversa nova.
        assertFalse(Evidence.conversaNova(comBolha("tô enviando uma figurinha pra você"), APARELHO))
    }

    @Test fun `reel compartilhado de conta desativada nao torna a conversa indisponivel`() {
        // O autor de um item compartilhado ("Usuario do Instagram") mora dentro da lista de mensagens.
        val r = ArvoreReal.comRotuloNoId(Telas.raiz("v_conversa_temporaria"), "reel_share_item_view", descricao = "Usuário do Instagram")
        assertNotEquals(Conversa.INDISPONIVEL, Evidence.conversa(r, APARELHO, "pessoa01"))
    }
}
