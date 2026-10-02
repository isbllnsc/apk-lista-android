package com.listalocal.service

import android.content.Context
import android.util.Log
import com.listalocal.core.contacts.Contato
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.UiNode
import com.listalocal.data.ContactsRepository
import kotlinx.coroutines.delay

/**
 * Orquestra o modo LISTAS_TRANSMISSAO no WhatsApp:
 *
 *  Fase 1 — Cria as listas de transmissão:
 *    1. Lê a agenda do celular (READ_CONTACTS)
 *    2. Normaliza os números para E.164 (+55...)
 *    3. Divide em lotes de até 256 contatos
 *    4. Para cada lote: abre o seletor de Nova transmissão, seleciona os
 *       contatos encontrados e toca em Criar
 *    5. Termina com [RunPhase.LISTS_CREATED] — o app volta ao primeiro plano
 *       para o usuário digitar a mensagem.
 *
 *  Fase 2 — Envia a mensagem pelas listas criadas:
 *    Para cada lista criada com sucesso: abre a lista no WhatsApp, digita a
 *    mensagem e toca em Enviar.
 *
 * Criado e chamado pelo [WhatsAppAccessibilityService].
 */
class WaBroadcastRunner(
    private val ctx: Context,
    private val waOps: WaBroadcastNodeOps,
    private val tela: Tela,
    private val prof: SelectorProfile,
    private val checkExpiry: () -> Unit,
    private val confirmarNoApp: suspend (String) -> Boolean,
    /** Quando true, inclui apenas contatos com número iniciando em +5521 (DDD 21). */
    val soPor5521: Boolean = false,
) {

    // ── Fase 1: criar e escanear listas ───────────────────────────────────────

    /**
     * Executa a Fase 1: cria listas de transmissão a partir da agenda (se houver) e
     * escaneia todas as listas existentes no WhatsApp, rolando a tela para garantir que
     * todas as listas apareçam (novas e já existentes).
     *
     * Ao terminar, sinaliza [RunPhase.LISTS_CREATED] com todas as listas encontradas
     * para que o usuário possa selecioná-las na Etapa 4 antes do envio.
     */
    suspend fun runPhase1() {
        // Modo só-scan: pula criação de listas, vai direto ao escaneamento das existentes.
        if (AutomationController.pendingBroadcastWa == AutomationController.ONLY_SCAN) {
            AutomationController.setPhase(RunPhase.WORKING, "Abrindo WhatsApp para ler listas…")
            tela.abrirInstagram()
            tela.esperar(2_500)
            AutomationController.setPhase(RunPhase.WORKING, "Lendo listas de transmissão do WhatsApp…")
            val todasAsListas = scanAllBroadcastLists(emptyList())
            AutomationController.update { st -> st.copy(broadcastListas = todasAsListas) }
            Log.i(TAG, "Scan-only: ${todasAsListas.size} lista(s) encontrada(s)")
            AutomationController.finish(RunPhase.LISTS_CREATED, "Listas atualizadas!")
            return
        }

        AutomationController.setPhase(RunPhase.READING_LIST, "Lendo agenda de contatos…")
        val contatos = carregarContatos()
        if (contatos.isEmpty()) {
            val temPermissao = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!temPermissao) {
                AutomationController.finish(
                    RunPhase.FAILED_SAFE,
                    "Permissão de acesso aos contatos não foi concedida. Ative o acesso aos contatos em Ajustes › Apps › Permissões."
                )
                return
            }
        }

        val lotes = if (contatos.isNotEmpty()) BroadcastLote.deContatos(contatos) else emptyList()
        Log.i(TAG, "Fase 1: ${contatos.size} contatos → ${lotes.size} lote(s) a criar")

        if (lotes.isNotEmpty()) {
            AutomationController.update { st ->
                st.copy(
                    broadcastFase = 1,
                    broadcastListas = lotes.map {
                        BroadcastListaProgress(it.index, it.label, total = it.contatos.size)
                    }
                )
            }
        }

        AutomationController.setPhase(RunPhase.OPENING_APP, "Abrindo o WhatsApp…")
        tela.abrirInstagram()
        tela.esperar(2_500)

        val resultadosCriacao = if (lotes.isNotEmpty()) {
            val engine = BroadcastEngine(waOps, prof, checkExpiry)
            engine.runPlan(
                lotes = lotes,
                autoConfirm = AutomationController.autoConfirm,
                onProgress = { fase, msg -> AutomationController.setPhase(fase, msg) },
                awaitConfirmation = { msg -> confirmarNoApp(msg) },
            )
        } else {
            emptyList()
        }

        for (r in resultadosCriacao) {
            AutomationController.updateBroadcastLista(r.index) {
                it.copy(selecionados = r.selected, criada = r.created, motivo = r.motivo)
            }
        }

        // Fase 1.5: Escaneia todas as listas de transmissão (inclusive pré-existentes),
        // rolando a tela para garantir que todas sejam descobertas.
        AutomationController.setPhase(RunPhase.WORKING, "Escanear listas de transmissão no WhatsApp…")
        val todasAsListas = scanAllBroadcastLists(resultadosCriacao)

        AutomationController.update { st ->
            st.copy(broadcastListas = todasAsListas)
        }

        val criadas = todasAsListas.filter { it.criada }
        Log.i(TAG, "Fase 1 concluída: ${criadas.size} lista(s) de transmissão prontas no WhatsApp")

        if (criadas.isEmpty()) {
            AutomationController.finish(
                RunPhase.FAILED_SAFE,
                "Nenhuma lista de transmissão foi encontrada ou criada no WhatsApp."
            )
            return
        }

        // Fase 1 concluída: volta ao app para o usuário escolher quais listas enviar e digitar a mensagem
        AutomationController.finish(
            RunPhase.LISTS_CREATED,
            "${criadas.size} lista(s) pronta(s). Escolha as listas para envio no app."
        )
    }

    // ── Fase 1.5: Escaneamento e rolagem de listas ────────────────────────────

    /** Constrói um mapa de nó -> pai para navegação ascendente na árvore de UiNodes. */
    private fun construirMapaDePais(raiz: UiNode): Map<UiNode, UiNode> {
        val mapa = mutableMapOf<UiNode, UiNode>()
        fun visitar(no: UiNode) {
            for (filho in no.children) {
                mapa[filho] = no
                visitar(filho)
            }
        }
        visitar(raiz)
        return mapa
    }

    /**
     * Escaneia a tela principal de conversas do WhatsApp procurando por listas de transmissão.
     * As listas de transmissão aparecem como conversas cujo subtítulo contém
     * "lista de transmissão" ou "broadcast list".
     * Rola a lista para garantir que todas sejam encontradas.
     */
    private suspend fun scanBroadcastsFromMainChatList(
        resultadosCriacao: List<BroadcastEngine.LoteResultado>
    ): List<BroadcastListaProgress> {
        // Abre o WhatsApp
        tela.abrirInstagram()
        tela.esperar(2_000)

        // Garante que estamos na aba "Conversas" (e não Comunidades, Atualizações, etc.)
        val rootAba = tela.ler()
        if (rootAba != null) {
            // Procura a aba "Conversas" na barra inferior — por texto, contentDescription ou ID
            val abaConversas = rootAba.walk().firstOrNull { n ->
                val txt = n.text?.toString() ?: ""
                val cd = n.contentDescription?.toString() ?: ""
                val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: ""
                txt.equals("Conversas", ignoreCase = true) ||
                    txt.equals("Chats", ignoreCase = true) ||
                    cd.equals("Conversas", ignoreCase = true) ||
                    cd.equals("Chats", ignoreCase = true) ||
                    id == "conversations_tab" || id == "chats_tab"
            }
            if (abaConversas != null) {
                Log.d(TAG, "Clicando na aba Conversas: txt='${abaConversas.text}' cd='${abaConversas.contentDescription}'")
                tela.tocar(abaConversas)
                tela.esperar(1_500)
            } else {
                Log.w(TAG, "Aba Conversas não encontrada na barra inferior.")
            }
        }

        data class ItemEscaneado(val titulo: String, val subtitulo: String)

        val itens = mutableListOf<ItemEscaneado>()
        val assinaturas = mutableSetOf<String>()
        var rodadasSemNovos = 0

        repeat(25) { _ ->
            checkExpiry()
            if (AutomationController.cancelRequested) return@repeat

            val root = tela.ler() ?: return@repeat
            val parentMap = construirMapaDePais(root)

            // Log diagnóstico: registra todos os IDs visíveis na primeira rolagem
            if (rodadasSemNovos == 0 && itens.isEmpty()) {
                val idsVistos = root.walk()
                    .mapNotNull { n -> n.viewIdResourceName?.substringAfter(":id/", "")?.takeIf { it.isNotBlank() } }
                    .distinct()
                    .take(30)
                Log.d(TAG, "IDs na tela: $idsVistos")
            }

            // Padrões para identificar listas de transmissão
            val broadcastTexts = listOf(
                "lista de transmissão", "broadcast list",
                "você criou uma lista", "you created a broadcast",
            )

            // Abordagem ampla: encontra contêineres de linha que contenham
            // qualquer texto com padrão de broadcast EM QUALQUER NÓ FILHO
            val todosNos = root.walk().toList()

            // Mapeia cada nó para seu avô (2 níveis acima = linha da conversa)
            var adicionou = false
            val containersCandidatos = mutableSetOf<UiNode>()

            for (no in todosNos) {
                val txt = no.text?.toString() ?: ""
                val cd = no.contentDescription?.toString() ?: ""
                val textoCompleto = "$txt $cd"
                val ehBroadcast = broadcastTexts.any { p ->
                    textoCompleto.contains(p, ignoreCase = true)
                }
                if (ehBroadcast) {
                    // Sobe até 4 níveis para encontrar o container da linha
                    var container: UiNode? = no
                    repeat(4) { container = parentMap[container] }
                    container?.let { containersCandidatos.add(it) }
                }
            }

            for (container in containersCandidatos) {
                // Dentro do container, pega o primeiro texto que não seja broadcast
                // (esse é o título da lista — nome dos contatos)
                val textosNoContainer = container.walk()
                    .mapNotNull { it.text?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val subtitulo = textosNoContainer.firstOrNull { t ->
                    broadcastTexts.any { p -> t.contains(p, ignoreCase = true) }
                } ?: ""

                val titulo = textosNoContainer.firstOrNull { t ->
                    broadcastTexts.none { p -> t.contains(p, ignoreCase = true) } && t.length > 2
                } ?: ""

                val chave = "${titulo.take(60)}||${subtitulo.take(80)}"
                if (chave.length > 4 && assinaturas.add(chave)) {
                    itens.add(ItemEscaneado(titulo.ifBlank { "Lista de transmissão" }, subtitulo))
                    adicionou = true
                    Log.i(TAG, "✅ Lista encontrada: titulo='$titulo' subtitulo='$subtitulo'")
                }
            }

            if (adicionou) {
                rodadasSemNovos = 0
            } else {
                rodadasSemNovos++
                if (rodadasSemNovos >= 2) return@repeat
            }

            val rolevel = root.walk().firstOrNull { it.isScrollable }
                ?: root.walk().firstOrNull { n ->
                    val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: ""
                    id == "list" || id == "recycler_view" || id == "conversations_list" ||
                        n.className?.contains("RecyclerView") == true
                }
            if (rolevel == null || !tela.rolar(rolevel)) return@repeat
            tela.esperar(600)
        }

        Log.i(TAG, "scanBroadcastsFromMainChatList: ${itens.size} lista(s) encontrada(s)")

        val lotesCriados = resultadosCriacao.filter { it.created }
        return itens.mapIndexed { idx, item ->
            val ehRecemCriada = idx < lotesCriados.size
            val loteCorrespondente = if (ehRecemCriada) lotesCriados[idx] else null
            val contatosCount = BroadcastParser.parseContactCount(item.subtitulo, item.titulo)
                .takeIf { it > 0 }
                ?: loteCorrespondente?.selected ?: 0
            val label = when {
                item.titulo.isNotBlank() -> item.titulo
                loteCorrespondente != null -> loteCorrespondente.label
                else -> "Lista #${idx + 1}"
            }
            BroadcastListaProgress(
                index = idx + 1,
                label = label,
                selecionados = contatosCount,
                total = contatosCount,
                criada = true,
                jaExistia = !ehRecemCriada,
                subtitulo = item.subtitulo.ifBlank { if (contatosCount > 0) "$contatosCount destinatários" else "" },
                selecionadaParaEnvio = true,
            )
        }
    }

    /**
     * Navega até a tela "Listas de transmissão" no WhatsApp.
     */
    private suspend fun navegarAteTelaListas(): Boolean {
        // 1. Confere se já está na tela de listas de transmissão
        val rootAtual = tela.ler()
        if (rootAtual != null) {
            val estaNaTela = rootAtual.walk().any { n ->
                val txt = n.text?.toString() ?: ""
                txt.contains("Listas de transmissão", ignoreCase = true) ||
                    txt.contains("Broadcast lists", ignoreCase = true)
            }
            if (estaNaTela) return true
        }

        // 2. Se estiver dentro de uma conversa (ex: recém-criou lista), volta uma vez
        val temCompositor = rootAtual?.walk()?.any { n ->
            n.viewIdResourceName?.substringAfter(":id/", "") == "entry" ||
                n.viewIdResourceName?.substringAfter(":id/", "") == "send"
        } == true
        if (temCompositor) {
            tela.voltar()
            tela.esperar(1_000)
        }

        // 3. Tenta abrir via menu overflow ⋮ -> "Listas de transmissão"
        val root = tela.ler() ?: return false
        val overflow = root.walk().firstOrNull { n ->
            val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: ""
            id == "overflow_menu_button" || id == "menuitem_overflow" ||
                n.contentDescription?.toString()?.contains("mais opções", ignoreCase = true) == true ||
                n.contentDescription?.toString()?.contains("more options", ignoreCase = true) == true
        }
        if (overflow != null) {
            tela.tocar(overflow)
            tela.esperar(600)

            val menuRoot = tela.ler()
            val itemListas = menuRoot?.walk()?.firstOrNull { n ->
                listOf("listas de transmissão", "broadcast lists", "broadcast list")
                    .any { t -> n.text?.toString()?.contains(t, ignoreCase = true) == true }
            }
            if (itemListas != null) {
                tela.tocar(itemListas)
                tela.esperar(1_200)
                return true
            } else {
                tela.voltar()
                tela.esperar(400)
            }
        }
        return false
    }

    /**
     * Escaneia a tela de listas de transmissão do WhatsApp, rolando a tela
     * para coletar todas as listas disponíveis (novas e já existentes).
     */
    private suspend fun scanAllBroadcastLists(
        resultadosCriacao: List<BroadcastEngine.LoteResultado>
    ): List<BroadcastListaProgress> {
        val navOk = navegarAteTelaListas()
        if (!navOk) {
            Log.w(TAG, "Tela de listas não disponível; escaneando tela principal de conversas.")
            // Fallback: listas de transmissão aparecem na tela principal de conversas
            // com subtítulo "Você criou uma lista de transmissão com X destinatários"
            return scanBroadcastsFromMainChatList(resultadosCriacao)
        }

        data class ItemEscaneado(
            val titulo: String,
            val subtitulo: String,
            val contatosCount: Int,
        )

        val itensColetados = mutableListOf<ItemEscaneado>()
        val assinaturasVistas = mutableSetOf<String>()
        var rodadasSemNovos = 0
        val maxRolagens = 25

        for (rolagem in 0 until maxRolagens) {
            checkExpiry()
            if (AutomationController.cancelRequested) break

            val listRoot = tela.ler() ?: break
            val parentMap = construirMapaDePais(listRoot)

            val nosDeTitulo = listRoot.walk().filter { n ->
                val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: ""
                (id == "conversations_row_contact_name" || id == "conversation_contact_name" || id == "name") &&
                    !n.text.isNullOrBlank()
            }.toList()

            var adicionouAlgum = false
            for (tituloNode in nosDeTitulo) {
                val titulo = tituloNode.text?.toString()?.trim() ?: ""

                // Tenta achar o subtítulo dentro do container da linha
                val container = parentMap[tituloNode]?.let { parentMap[it] } ?: parentMap[tituloNode]
                val subtituloNode = container?.walk()?.firstOrNull { child ->
                    val cid = child.viewIdResourceName?.substringAfter(":id/", "") ?: ""
                    (cid == "conversations_row_snippet" || cid == "conversations_row_subtitle" ||
                        cid == "subtitle" || cid == "snippet" || cid == "single_msg_tv") &&
                        child != tituloNode && !child.text.isNullOrBlank()
                }
                val subtitulo = subtituloNode?.text?.toString()?.trim() ?: ""

                val chave = "$titulo||$subtitulo"
                if (chave.isNotBlank() && assinaturasVistas.add(chave)) {
                    val contatosCount = BroadcastParser.parseContactCount(subtitulo, titulo)
                    itensColetados.add(
                        ItemEscaneado(
                            titulo = titulo,
                            subtitulo = subtitulo,
                            contatosCount = contatosCount,
                        )
                    )
                    adicionouAlgum = true
                }
            }

            if (adicionouAlgum) {
                rodadasSemNovos = 0
            } else {
                rodadasSemNovos++
                if (rodadasSemNovos >= 2) {
                    Log.d(TAG, "Duas rolagens sem novas listas: fim da tela.")
                    break
                }
            }

            // Tenta rolar para carregar mais listas
            val rolevel = listRoot.walk().firstOrNull { it.isScrollable }
                ?: listRoot.walk().firstOrNull { n ->
                    val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: ""
                    id == "list" || id == "recycler_view" || id == "conversations_list" ||
                        n.className?.contains("RecyclerView") == true || n.className?.contains("ListView") == true
                }

            if (rolevel == null) {
                Log.d(TAG, "Nenhum container rolável na tela.")
                break
            }

            val rolou = tela.rolar(rolevel)
            if (!rolou) {
                Log.d(TAG, "rolar() retornou false: final da lista alcançado.")
                break
            }
            tela.esperar(800)
        }

        Log.i(TAG, "Listas escaneadas no WhatsApp: ${itensColetados.size}")

        val lotesCriados = resultadosCriacao.filter { it.created }
        val resultadoFinal = mutableListOf<BroadcastListaProgress>()

        for ((idx, item) in itensColetados.withIndex()) {
            val index = idx + 1
            val ehRecemCriada = idx < lotesCriados.size
            val loteCorrespondente = if (ehRecemCriada) lotesCriados[idx] else null

            val contatosCount = when {
                item.contatosCount > 0 -> item.contatosCount
                loteCorrespondente != null && loteCorrespondente.selected > 0 -> loteCorrespondente.selected
                else -> 0
            }

            val label = when {
                item.titulo.isNotBlank() && !item.titulo.equals("Lista de transmissão", ignoreCase = true) -> item.titulo
                loteCorrespondente != null -> loteCorrespondente.label
                else -> "Lista #$index"
            }

            val subtitulo = when {
                item.subtitulo.isNotBlank() -> item.subtitulo
                contatosCount > 0 -> "$contatosCount destinatários"
                else -> ""
            }

            resultadoFinal.add(
                BroadcastListaProgress(
                    index = index,
                    label = label,
                    selecionados = contatosCount,
                    total = contatosCount,
                    criada = true,
                    jaExistia = !ehRecemCriada,
                    subtitulo = subtitulo,
                    selecionadaParaEnvio = true,
                )
            )
        }

        if (resultadoFinal.isEmpty() && lotesCriados.isNotEmpty()) {
            for (lote in lotesCriados) {
                resultadoFinal.add(
                    BroadcastListaProgress(
                        index = lote.index,
                        label = lote.label,
                        selecionados = lote.selected,
                        total = lote.selected,
                        criada = true,
                        jaExistia = false,
                        subtitulo = "${lote.selected} contatos",
                        selecionadaParaEnvio = true,
                    )
                )
            }
        }

        return resultadoFinal
    }

    // ── Fase 2: envio via listas selecionadas ─────────────────────────────────

    /**
     * Executa a Fase 2 enviando mensagem apenas para as listas selecionadas.
     * Se [listasIndices] estiver vazio, envia para todas as listas marcadas como criadas.
     */
    suspend fun runPhase2(mensagem: String, listasIndices: Set<Int> = emptySet()) {
        checkExpiry()
        val todas = AutomationController.state.value.broadcastListas.filter { it.criada }
        val alvos = if (listasIndices.isNotEmpty()) {
            todas.filter { it.index in listasIndices }
        } else {
            todas
        }

        if (alvos.isEmpty()) {
            AutomationController.finish(RunPhase.FAILED_SAFE, "Nenhuma lista selecionada para envio.")
            return
        }

        AutomationController.update { it.copy(broadcastFase = 2) }
        AutomationController.setPhase(RunPhase.SENDING_PHASE, "Iniciando envio por ${alvos.size} lista(s)…")
        sendViaLists(alvos, mensagem)
    }

    /** Compatibilidade com assinatura anterior de runPhase2. */
    suspend fun runPhase2(mensagem: String) {
        runPhase2(mensagem, emptySet())
    }

    /** Compat: executa Fase 1 e Fase 2 de forma contínua. */
    suspend fun run(mensagem: String) {
        runPhase1()
        if (AutomationController.state.value.phase == RunPhase.LISTS_CREATED) {
            AutomationController.update { it.copy(running = true) }
            runPhase2(mensagem)
        }
    }

    /**
     * Abre cada lista selecionada e envia a mensagem, rolando a tela
     * se a lista estiver mais abaixo.
     */
    private suspend fun sendViaLists(alvos: List<BroadcastListaProgress>, mensagem: String) {
        checkExpiry()

        // Abre WhatsApp
        tela.abrirInstagram()
        tela.esperar(2_000)

        val navOk = navegarAteTelaListas()
        if (!navOk) {
            Log.w(TAG, "Fase 2: não conseguiu navegar até a tela de listas.")
            AutomationController.finish(
                RunPhase.COMPLETED,
                "Listas prontas! Abra manualmente no WhatsApp e envie a mensagem."
            )
            return
        }

        var enviadas = 0
        for ((idx, alvo) in alvos.withIndex()) {
            checkExpiry()
            if (AutomationController.cancelRequested) break
            while (AutomationController.pauseRequested) { checkExpiry(); delay(400) }

            AutomationController.setPhase(
                RunPhase.WORKING,
                "Enviando pela lista '${alvo.label}' (${idx + 1}/${alvos.size})…"
            )

            var linhaDaLista: UiNode? = null
            for (scrollAttempt in 0..10) {
                val listRoot = tela.ler() ?: break
                val parentMap = construirMapaDePais(listRoot)

                val candidatos = listRoot.walk().filter { n ->
                    val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: ""
                    (id == "conversations_row_contact_name" || id == "conversation_contact_name" || id == "name") &&
                        !n.text.isNullOrBlank()
                }.toList()

                // 1. Tenta achar pelo texto do título
                linhaDaLista = candidatos.firstOrNull { c ->
                    val txt = c.text?.toString()?.trim() ?: ""
                    txt.equals(alvo.label, ignoreCase = true) ||
                        (alvo.label.isNotBlank() && txt.contains(alvo.label, ignoreCase = true))
                }

                // 2. Se não achou pelo título, tenta pelo subtítulo
                if (linhaDaLista == null && alvo.subtitulo.isNotBlank()) {
                    linhaDaLista = candidatos.firstOrNull { c ->
                        val container = parentMap[c]?.let { parentMap[it] } ?: parentMap[c]
                        container?.walk()?.any { sub ->
                            sub.text?.toString()?.contains(alvo.subtitulo, ignoreCase = true) == true
                        } == true
                    }
                }

                // 3. Fallback por índice se estiver na primeira tela
                if (linhaDaLista == null && scrollAttempt == 0 && alvo.index <= candidatos.size) {
                    linhaDaLista = candidatos[alvo.index - 1]
                }

                if (linhaDaLista != null) break

                // Rola para a frente para tentar encontrar a lista
                val rolevel = listRoot.walk().firstOrNull { it.isScrollable }
                    ?: listRoot.walk().firstOrNull { n ->
                        val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: ""
                        id == "list" || id == "recycler_view" || id == "conversations_list"
                    }
                if (rolevel == null || !tela.rolar(rolevel)) break
                tela.esperar(800)
            }

            if (linhaDaLista == null) {
                Log.w(TAG, "Fase 2: lista '${alvo.label}' não encontrada na tela.")
                continue
            }

            tela.tocar(linhaDaLista)
            tela.esperar(1_500)

            // Dentro da lista: digita e envia a mensagem
            val convRoot = tela.ler()
            if (convRoot == null) {
                tela.voltar()
                tela.esperar(600)
                continue
            }

            val waDm = WaDmFlow(tela, prof, SemRegistro)
            val campo = waDm.campoMensagem(convRoot)
            if (campo == null) {
                Log.w(TAG, "Fase 2: campo de mensagem não encontrado na lista '${alvo.label}'")
                tela.voltar()
                tela.esperar(600)
                continue
            }

            tela.escrever(campo, mensagem)
            tela.esperar(400)

            val rootEnviar = tela.ler() ?: continue
            val botao = waDm.botaoEnviar(rootEnviar)
            if (botao == null) {
                Log.w(TAG, "Fase 2: botão Enviar não encontrado na lista '${alvo.label}'")
                tela.voltar()
                tela.esperar(600)
                continue
            }

            tela.tocarEnviar(botao)
            tela.esperar(800)
            enviadas++

            tela.voltar()
            tela.esperar(700)
        }

        val msg = if (enviadas == alvos.size) {
            "Envio concluído: mensagem enviada por $enviadas lista(s) de transmissão."
        } else {
            "Fase 2 parcial: $enviadas/${alvos.size} listas enviadas. Verifique o WhatsApp."
        }
        AutomationController.finish(RunPhase.COMPLETED, msg)
    }

    // ── Leitura de contatos ───────────────────────────────────────────────────

    /**
     * Lê a agenda do celular via [ContactsRepository] e normaliza os números para E.164.
     * Cada contato com ao menos um número válido vira um [Contato].
     */
    private fun carregarContatos(): List<Contato> {
        val raw = try {
            ContactsRepository.carregar(ctx)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão negada ao ler contatos: ${e.message}")
            emptyList()
        }
        val contatos = mutableListOf<Contato>()
        for (entry in raw) {
            val e164 = entry.phones.firstNotNullOfOrNull { normalizar(it) } ?: continue
            if (soPor5521 && !e164.startsWith("+5521")) continue   // filtro DDD 21
            contatos += Contato(e164 = e164, nome = entry.name, contactId = entry.id)
        }
        return contatos
    }

    /**
     * Normaliza um número de telefone para E.164 com DDD brasileiro (+55).
     * Retorna null se o número não tiver dígitos suficientes.
     */
    private fun normalizar(raw: String): String? {
        val digitos = raw.filter { it.isDigit() }
        return when {
            digitos.length >= 13 -> "+$digitos"           // já completo: ex. 55119...
            digitos.length == 11 -> "+55$digitos"         // DDD + 9 + 8 dígitos
            digitos.length == 10 -> "+55$digitos"         // DDD + 8 dígitos (fixo)
            digitos.length >= 8  -> "+55${digitos}"       // sem DDD: best-effort
            else -> null
        }
    }

    private object SemRegistro : Registro {
        override fun marcarCommit(username: String) = false
        override fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int>) = Unit
    }

    companion object {
        private const val TAG = "WaBroadcastRunner"
    }
}
