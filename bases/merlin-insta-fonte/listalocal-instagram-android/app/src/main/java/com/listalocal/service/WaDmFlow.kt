package com.listalocal.service

import android.util.Log
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.UiNode

/**
 * Fluxo de DM específico para o WhatsApp.
 *
 * Conferência (etapa 2):
 *   1. Abre o WhatsApp (via tela.abrirInstagram() redirecionado para WA)
 *   2. Espera a tela de conversas (checa FAB "Nova conversa")
 *   3. Toca no FAB → tela de nova conversa com campo de busca
 *   4. Verifica o campo de busca de contato
 *   5. Volta sem escrever nada
 *
 * IDs reais do WhatsApp usados (viewIdResourceName sem prefixo "com.whatsapp:id/"):
 *   fab                       → botão flutuante "Nova conversa"
 *   entry                     → campo de texto da mensagem em uma conversa
 *   send                      → botão Enviar
 *   search_contact_textview   → campo "Pesquisar" na tela de Nova conversa
 *   conversation_contact_name → nome no cabeçalho da conversa aberta
 */
class WaDmFlow(
    private val tela: Tela,
    private val prof: SelectorProfile,
    private val registro: Registro,
) {

    // ── Localização de nós por viewIdResourceName ─────────────────────────────

    private fun UiNode.idFim(suffix: String): Boolean =
        viewIdResourceName?.substringAfter(":id/", "") == suffix

    private fun UiNode.labelContains(vararg palavras: String) =
        listOfNotNull(text, contentDescription).any { t ->
            palavras.any { p -> t.contains(p, ignoreCase = true) }
        }

    // ── Elementos do WhatsApp ─────────────────────────────────────────────────

    /** Botão "Nova conversa" na lista de conversas. */
    private fun fabNovaConversa(root: UiNode): UiNode? =
        root.walk().firstOrNull { n ->
            n.idFim("fab") ||
                n.labelContains("nova conversa", "new chat", "compose")
        }

    /** Campo de pesquisa na tela de Nova conversa — IDs variam entre versões do WhatsApp. */
    private fun campoPesquisa(root: UiNode): UiNode? =
        root.walk().firstOrNull { n ->
            n.idFim("search_contact_textview") ||
                n.idFim("search_src_text") ||
                n.idFim("search_input") ||
                n.idFim("search_edit_text") ||
                n.idFim("query") ||
                n.idFim("searchTextView") ||
                (n.isClickable && n.labelContains(
                    "pesquise um contato", "nome ou número", "search",
                    "pesquisar", "find", "to:", "para:"
                ))
        }

    /** Campo de mensagem em uma conversa aberta. */
    fun campoMensagem(root: UiNode): UiNode? =
        root.walk().firstOrNull { n ->
            n.idFim("entry")
        } ?: root.walk().firstOrNull { n ->
            n.isClickable && n.labelContains("mensagem", "message", "digite", "type a message")
        }

    /** Botão Enviar habilitado. */
    fun botaoEnviar(root: UiNode): UiNode? =
        root.walk().firstOrNull { n ->
            n.isEnabled && (
                n.idFim("send") ||
                    n.idFim("send_btn") ||
                    (n.isClickable && listOfNotNull(n.contentDescription).any {
                        it.equals("Enviar", ignoreCase = true) || it.equals("Send", ignoreCase = true)
                    })
                )
        }

    /** Nome no cabeçalho da conversa aberta. */
    fun tituloDaConversa(root: UiNode): String? =
        root.walk().firstOrNull { n ->
            n.idFim("conversation_contact_name") && !n.text.isNullOrBlank()
        }?.text?.trim()
            ?: root.walk().firstOrNull { n ->
                n.idFim("action_bar_title") && !n.text.isNullOrBlank()
            }?.text?.trim()

    // ── Resultado do envio WA ─────────────────────────────────────────────────

    data class WaResult(
        val desfecho: Desfecho,
        val motivo: String,
        val tocouEm: Long? = null,
        val enviadas: List<Int> = emptyList(),
    )

    // ── Envio (Modo C WhatsApp) ───────────────────────────────────────────────

    /**
     * Envia [texto] para [username] (número E.164) pelo WhatsApp.
     *
     * 1. Abre a conversa via wa.me/<número>
     * 2. Aguarda o campo de mensagem aparecer na tela (até 8 s)
     * 3. Aplica o intervalo ([vez])
     * 4. Escreve o texto e toca em Enviar
     * 5. Verifica o envio (campo fica vazio em até 5 s)
     */
    suspend fun enviarWa(
        username: String,
        nome: String,
        texto: String,
        vez: suspend () -> Boolean = { false },
        parado: () -> Boolean = { false },
    ): WaResult {
        // 1. Abre a conversa
        if (!tela.abrirConversa(username)) {
            return WaResult(Desfecho.FALHA, "não foi possível abrir a conversa")
        }

        // 2. Espera o campo de mensagem aparecer (conversa aberta)
        val rootConversa = esperarAte(8_000) { r -> campoMensagem(r) != null }
        if (rootConversa == null) {
            return WaResult(Desfecho.NAO_ENCONTRADO, "a conversa com $username não abriu em 8 s")
        }

        // 3. Aplica intervalo (pode lançar Adiado se Pausar/Parar for tocado)
        val leuDeNovo = vez()
        val rootAtual = if (leuDeNovo) tela.ler() ?: rootConversa else rootConversa

        // 4. Localiza campo e escreve
        val campo = campoMensagem(rootAtual) ?: campoMensagem(rootConversa)
            ?: return WaResult(Desfecho.FALHA, "campo de mensagem sumiu após intervalo")
        tela.escrever(campo, texto)
        tela.esperar(400)

        // 5. Localiza botão Enviar habilitado
        val rootComTexto = tela.ler()
            ?: return WaResult(Desfecho.FALHA, "perdeu a tela após escrever")
        val botao = botaoEnviar(rootComTexto)
            ?: return WaResult(Desfecho.FALHA, "botão Enviar não encontrado ou desabilitado")

        // 6. Commit: grava "vou enviar" antes do toque
        registro.gravar(username, Desfecho.INCERTO, "vou enviar", emptyList())
        tela.tocarEnviar(botao)
        val enviouEm = tela.agora()

        // 7. Verifica: campo vazio = enviado com confirmação
        val confirmado = esperarAte(5_000) { r ->
            val c = campoMensagem(r)
            c == null || c.text.isNullOrEmpty()
        }
        return if (confirmado != null) {
            registro.gravar(username, Desfecho.ENVIADO, "enviado", listOf(1))
            WaResult(Desfecho.ENVIADO, "enviado", tocouEm = enviouEm, enviadas = listOf(1))
        } else {
            registro.gravar(username, Desfecho.INCERTO, "enviado; sem confirmação visual", listOf(1))
            WaResult(Desfecho.INCERTO, "enviado; sem confirmação visual", tocouEm = enviouEm, enviadas = listOf(1))
        }
    }

    // ── Conferência ───────────────────────────────────────────────────────────

    /**
     * Navega no WhatsApp sem escrever nem enviar nada.
     * Retorna (mapa de achados → conta lida, sempre null no WA).
     */
    suspend fun conferir(
        aprender: ((UiNode, Set<String>) -> Unit)? = null,
    ): Pair<Map<String, Boolean>, String?> {
        val achou = LinkedHashMap<String, Boolean>()

        // 1. Abrir o WhatsApp
        if (!tela.abrirInstagram()) return achou to null

        // Aguarda o WhatsApp chegar ao primeiro plano antes de começar a ler.
        // Aparelhos lentos (Android 7, SM-A510M) podem levar 2-3 s para transicionar.
        tela.esperar(2_500)

        // 2. Esperar a tela de conversas (FAB ou lista de conversas)
        val telaInicial = esperarAte(12_000) { root ->
            fabNovaConversa(root) != null ||
                root.walk().any { it.idFim("conversations_rv") } ||
                root.walk().any { it.idFim("list") } ||
                // IDs reais encontrados no WA 2.26.37.73 (dump via uiautomator):
                root.walk().any { it.idFim("conversation_list_view_host") } ||
                root.walk().any { it.idFim("conversations_coordinator_layout") } ||
                root.walk().any { it.idFim("conversations_swipe_to_reveal_filter_recycler_view") }
        }

        val fab = telaInicial?.let { fabNovaConversa(it) }
        achou["wa_inbox"] = telaInicial != null
        achou["wa_fab"] = fab != null

        if (telaInicial == null) {
            val dump = tela.ler()?.walk()?.take(15)
                ?.map { it.viewIdResourceName }?.toList()
            Log.w(TAG, "conferir: tela de conversas não encontrada. IDs visíveis: $dump")
            return achou to null
        }

        // FAB é opcional: algumas versões do WA usam ícone no action bar em vez de FAB flutuante.
        if (fab == null) {
            Log.i(TAG, "conferir: inbox encontrado mas FAB ausente (versão sem FAB flutuante). Prosseguindo.")
            achou["wa_fab"] = true  // considera OK se inbox foi encontrado
        }
        Log.d(TAG, "conferir: inbox=${achou["wa_inbox"]} fab=${achou["wa_fab"]}")

        if (fab != null) {
            // 3. Tocar no FAB → tela de nova conversa
            tela.tocar(fab)
            val telaNova = esperarAte(5_000) { root -> campoPesquisa(root) != null }
            achou["wa_search_field"] = telaNova != null
            Log.d(TAG, "conferir: search_field=${achou["wa_search_field"]}")

            if (telaNova == null) {
                val dump2 = tela.ler()?.walk()?.take(10)?.map { it.viewIdResourceName }?.toList()
                Log.w(TAG, "conferir: campo pesquisa não encontrado. IDs visíveis: $dump2")
            }
        }

        // 4. Voltar sem escrever
        tela.esperar(300)
        tela.voltar()
        tela.esperar(500)
        // fechar teclado se abriu
        if (tela.ler()?.walk()?.none {
                it.idFim("conversations_rv") || it.idFim("list") ||
                    it.idFim("conversation_list_view_host") ||
                    it.idFim("conversations_coordinator_layout") ||
                    it.idFim("conversations_swipe_to_reveal_filter_recycler_view")
            } == true) {
            tela.voltar()
        }

        return achou to null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun esperarAte(ms: Long, condicao: (UiNode) -> Boolean): UiNode? {
        val fim = tela.agora() + ms
        while (tela.agora() < fim) {
            val root = tela.ler()
            if (root != null && condicao(root)) return root
            tela.esperarEvento(300)
        }
        return null
    }

    companion object {
        private const val TAG = "WaDmFlow"

        /**
         * Chaves que devem estar presentes para o modo DM ser "Liberado".
         */
        /** wa_inbox é suficiente; wa_fab e wa_search_field são diagnóstico extra. */
        val CHAVES_CONFERENCIA = setOf("wa_inbox")
    }
}
