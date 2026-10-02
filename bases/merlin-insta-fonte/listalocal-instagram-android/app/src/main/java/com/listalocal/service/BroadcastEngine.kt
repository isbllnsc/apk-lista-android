package com.listalocal.service

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.listalocal.core.contacts.Contato
import com.listalocal.core.privacy.Redaction
import com.listalocal.core.selectors.SelectorProfile
import kotlinx.coroutines.delay

/**
 * Motor de criação de listas de transmissão no WhatsApp.
 *
 * Portado do BroadcastAccessibilityService (ListaLocal-v16), adaptado como
 * classe pura (sem ser AccessibilityService) para ser chamado dentro do
 * [WhatsAppAccessibilityService] do merlin como Fase 1, antes do envio de mensagens.
 *
 * Segurança: nunca envia mensagem, nunca abre conversa, nunca toca no compositor;
 * seleciona correspondência EXATA e ÚNICA; para em UI desconhecida.
 */
class BroadcastEngine(
    private val ops: BroadcastNodeOps,
    private val prof: SelectorProfile,
    private val checkExpiry: () -> Unit,
) {

    /** Resultado da criação de um lote. */
    data class LoteResultado(
        val index: Int,
        val label: String,
        val created: Boolean,
        val selected: Int = 0,
        val motivo: String = "",
    )

    /** Campo de busca do seletor, reaproveitado entre contatos. */
    private var searchBox: AccessibilityNodeInfo? = null
    private var ultimaConsulta: String? = null
    private var ultimoResultadoVazio: Boolean = false

    /**
     * Cria as listas de transmissão para cada lote de contatos.
     * Se um lote falhar, registra e continua para o próximo.
     *
     * @param lotes lista de lotes (até 256 contatos cada)
     * @param autoConfirm se true, não espera confirmação do usuário antes de criar
     * @param onProgress callback para atualizar a UI a cada mudança de fase
     * @param awaitConfirmation callback para pedir confirmação ao usuário
     * @return lista com resultado de cada lote
     */
    suspend fun runPlan(
        lotes: List<BroadcastLote>,
        autoConfirm: Boolean,
        onProgress: (RunPhase, String) -> Unit,
        awaitConfirmation: suspend (String) -> Boolean,
    ): List<LoteResultado> {
        val resultados = mutableListOf<LoteResultado>()

        for (lote in lotes) {
            checkExpiry()
            if (AutomationController.cancelRequested) break

            onProgress(RunPhase.OPENING_SCREEN, "Abrindo seletor para ${lote.label}…")

            // Reseta o cache do campo de busca a cada nova lista
            searchBox = null
            ultimaConsulta = null
            ultimoResultadoVazio = false

            // Abre o seletor de nova transmissão
            val caminho = try {
                abrirSeletorDaTransmissao()
            } catch (e: Exception) {
                Log.w(TAG, "falha ao abrir seletor para ${lote.label}: ${e.message}")
                resultados += LoteResultado(lote.index, lote.label, created = false, motivo = e.message ?: "seletor não abriu")
                continue
            }

            if (caminho == null) {
                resultados += LoteResultado(lote.index, lote.label, created = false, motivo = "Nova transmissão não encontrado no WhatsApp")
                continue
            }

            // Aguarda o seletor de contatos carregar (usa prof ou fallbacks WA hardcoded)
            val pickerSearch = prof.idFor("picker_search") ?: "menuitem_search"
            val pickerSearchInput = prof.idFor("picker_search_input") ?: "search_src_text"
            if (!waitViewId(pickerSearch, 8000) && !waitViewId(pickerSearchInput, 2000)) {
                ops.performGlobalBack()
                resultados += LoteResultado(lote.index, lote.label, created = false, motivo = "seletor de contatos não abriu")
                continue
            }
            // Aguarda linha de contato aparecer (fallback WA: contactpicker_row_name)
            val rowNameId = prof.idFor("row_name") ?: "contactpicker_row_name"
            waitViewId(rowNameId, 10000)

            // Seleciona cada contato do lote
            onProgress(RunPhase.WORKING, "Selecionando contatos — ${lote.label}…")
            var selecionados = 0
            for (contato in lote.contatos) {
                checkExpiry()
                if (AutomationController.cancelRequested) break
                while (AutomationController.pauseRequested) { checkExpiry(); delay(400) }
                if (selectOne(contato)) selecionados++
                onProgress(RunPhase.WORKING, "${lote.label}: $selecionados selecionados…")
            }

            if (selecionados < MIN_RECIPIENTS) {
                ops.performGlobalBack()
                resultados += LoteResultado(lote.index, lote.label, created = false, selected = selecionados,
                    motivo = "menos de $MIN_RECIPIENTS contatos encontrados no WhatsApp")
                continue
            }

            // Confirmação antes de criar (se não for autoConfirm)
            if (!autoConfirm) {
                onProgress(RunPhase.WAITING_CONFIRMATION, "$selecionados selecionados. Criar ${lote.label}?")
                val ok = awaitConfirmation("$selecionados selecionados. Criar ${lote.label}?")
                if (!ok) {
                    ops.performGlobalBack()
                    resultados += LoteResultado(lote.index, lote.label, created = false, selected = selecionados, motivo = "cancelado pelo usuário")
                    continue
                }
            }

            // Toca em Criar
            onProgress(RunPhase.SAVING, "Criando ${lote.label}…")
            val criou = try {
                createList()
                true
            } catch (e: Exception) {
                Log.w(TAG, "falha ao criar ${lote.label}: ${e.message}")
                false
            }

            resultados += LoteResultado(lote.index, lote.label, created = criou, selected = selecionados,
                motivo = if (criou) "" else "botão Criar não respondeu")
            Log.i(TAG, "${lote.label}: created=$criou selected=$selecionados")
        }

        return resultados
    }

    // ── Seleção de contatos ──────────────────────────────────────────────────

    /**
     * Seleciona um contato na tela do seletor de transmissão.
     * @return true se o contato foi selecionado com evidência de marcação.
     */
    private suspend fun selectOne(contato: Contato): Boolean {
        val candidatos = consultasPara(contato.e164, contato.nome)
        var rows: List<AccessibilityNodeInfo> = emptyList()

        for ((i, consulta) in candidatos.withIndex()) {
            checkExpiry()
            if (ultimoResultadoVazio || consulta.equals(ultimaConsulta, ignoreCase = true)) {
                prepararBusca()
            }
            val antes = fotografarLinhas()
            if (!digitarBusca(consulta)) {
                Log.w(TAG, "não consegui digitar busca para ${Redaction.mask(contato.nome)}")
                return false
            }
            rows = waitExactNameRows(contato.nome, BUSCA_TIMEOUT_MS, antes)
            ultimaConsulta = consulta
            ultimoResultadoVazio = rows.isEmpty()
            if (rows.isNotEmpty()) break
            Log.d(TAG, "consulta ${i + 1}/${candidatos.size} sem resultado para ${Redaction.mask(contato.nome)}")
        }

        return when {
            rows.size == 1 -> {
                if (ops.anySelected(rows)) {
                    Log.d(TAG, "${Redaction.mask(contato.nome)}: já selecionado")
                    true
                } else {
                    val chipsAntes = contarChips()
                    ops.click(rows[0])
                    val oraculo = esperarMarcacao(contato.nome, chipsAntes)
                    if (oraculo != null) {
                        Log.d(TAG, "${Redaction.mask(contato.nome)}: selecionado ($oraculo)")
                        true
                    } else {
                        if (dismissBlockedDialogIfPresent()) {
                            Log.d(TAG, "${Redaction.mask(contato.nome)}: bloqueado — pulado")
                        } else {
                            Log.w(TAG, "${Redaction.mask(contato.nome)}: toque sem confirmação")
                        }
                        false
                    }
                }
            }
            rows.size > 1 -> { Log.d(TAG, "${Redaction.mask(contato.nome)}: ambíguo (${rows.size} resultados)"); false }
            else -> { Log.d(TAG, "${Redaction.mask(contato.nome)}: não encontrado"); false }
        }
    }

    /** Consultas a tentar no seletor — nome primeiro, depois número. */
    private fun consultasPara(e164: String, nome: String): List<String> {
        val n = nome.trim()
        val nomeEhNumero = n.isEmpty() || NUMERICO.matches(n)
        if (!nomeEhNumero) return listOf(n)
        val nacional = if (e164.startsWith("+55")) e164.removePrefix("+55") else e164.removePrefix("+")
        return listOf(nacional, e164).distinct()
    }

    private fun contarChips(): Int =
        ops.byViewId(prof.idFor("selected_chip_name") ?: "contact_name").size

    private fun fotografarLinhas(): Set<String> {
        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        return ops.byViewId(rowId).mapNotNull { it.text?.toString()?.trim() }.toSet()
    }

    private suspend fun esperarMarcacao(nome: String, chipsAntes: Int): String? {
        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        val checkId = prof.idFor("selection_check") ?: "selection_check"
        val alvo = nome.trim()
        val fim = SystemClock.uptimeMillis() + MARCACAO_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < fim) {
            checkExpiry()
            if (contarChips() > chipsAntes) return "chip"
            val linha = ops.byViewId(rowId).firstOrNull { it.text?.toString()?.trim() == alvo }
            if (linha != null) {
                val c = containerDe(linha)
                if (c != null && (c.isChecked || c.isSelected)) return "container"
                if (c?.contentDescription?.contains("elecionad", ignoreCase = true) == true) return "descricao"
                val check = ops.descendantByViewId(c ?: linha, checkId)
                if (check != null && check.isVisibleToUser) return "check"
            }
            delay(POLL_NORMAL)
        }
        return null
    }

    private fun containerDe(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val sufixo = ":id/${prof.idFor("row_container") ?: "row_container"}"
        var n: AccessibilityNodeInfo? = node
        repeat(5) {
            if (n?.viewIdResourceName?.endsWith(sufixo) == true) return n
            n = n?.parent
        }
        return null
    }

    private suspend fun dismissBlockedDialogIfPresent(): Boolean {
        if (ops.firstTextContaining("bloque") == null) return false
        ops.firstTextContaining("cancelar")?.let { ops.click(it) } ?: ops.performGlobalBack()
        delay(300)
        searchBox = null
        return true
    }

    // ── Campo de busca ───────────────────────────────────────────────────────

    private suspend fun prepararBusca() {
        val box = searchBox ?: return
        try { box.refresh() } catch (_: Exception) { }
        val atual = box.text?.toString() ?: ""
        if (atual.isEmpty()) return
        if (!ops.setText(box, "")) { searchBox = null; return }
        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        val semResultadosId = prof.idFor("no_results")
        var anterior: Set<String>? = null
        val fim = SystemClock.uptimeMillis() + PREPARO_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < fim) {
            checkExpiry()
            val nomes = ops.byViewId(rowId).mapNotNull { it.text?.toString()?.trim() }.toSet()
            val semAviso = semResultadosId == null || !ops.existsViewId(semResultadosId)
            if (nomes.isNotEmpty() && semAviso && nomes == anterior) return
            anterior = if (nomes.isNotEmpty() && semAviso) nomes else null
            delay(POLL_NORMAL)
        }
    }

    private suspend fun digitarBusca(texto: String): Boolean {
        repeat(2) { tentativa ->
            checkExpiry()
            var box = searchBox
            if (box == null || tentativa == 1) {
                box = openSearch() ?: return false
                searchBox = box
            }
            if (ops.setText(box, texto)) {
                try { box.refresh() } catch (_: Exception) { }
                if ((box.text?.toString() ?: "").trim() == texto.trim()) return true
            }
            searchBox = null
        }
        return false
    }

    private suspend fun openSearch(): AccessibilityNodeInfo? {
        val inputId = prof.idFor("picker_search_input") ?: "search_src_text"
        val searchId = prof.idFor("picker_search") ?: "menuitem_search"
        ops.firstByViewId(inputId)?.let { return it }
        ops.firstByViewId(searchId)?.let { ops.click(it) }
        if (waitViewId(inputId, 3000)) {
            return ops.firstByViewId(inputId)
        }
        return null
    }

    // ── Navegação ────────────────────────────────────────────────────────────

    /**
     * Abre o seletor de contatos para nova transmissão.
     * Tenta três caminhos: menu ⋮ → Listas de transmissão → Nova,
     * botão FAB → Nova transmissão, aba Ferramentas (Business).
     * @return identificação do caminho usado, ou null se nenhum existe.
     */
    private suspend fun abrirSeletorDaTransmissao(): String? {
        checkExpiry()

        // A) menu ⋮ → Listas de transmissão
        // Tenta primeiro pelo profile, depois pelos IDs reais do WhatsApp (menuitem_overflow)
        val overflow = prof.idFor("menu_overflow")?.let { ops.firstByViewId(it) }
            ?: ops.firstByViewId("menuitem_overflow")
            ?: prof.descriptionsFor("menu_overflow").firstNotNullOfOrNull { ops.byContentDesc(it) }
            ?: listOf("Mais opções", "More options", "Mais Opções")
                .firstNotNullOfOrNull { ops.byContentDesc(it) }
        if (overflow != null) {
            ops.click(overflow)
            delay(500)
            // Procura item de transmissão no menu pelo profile ou por texto PT/EN
            val item = prof.descriptionsFor("menu_broadcast_lists")
                .firstNotNullOfOrNull { ops.menuItemContaining(it) }
                ?: listOf("transmiss", "broadcast", "Listas de transmissão", "Nova transmissão")
                    .firstNotNullOfOrNull { ops.menuItemContaining(it) }
            if (item != null) {
                ops.click(item)
                delay(700)
                // Dentro da BroadcastListHomeActivity: procura botão para nova lista
                val novo = prof.idFor("broadcast_new_button")?.let { ops.firstByViewId(it) }
                    ?: ops.firstByViewId("broadcast_new_button")
                    ?: ops.firstByViewId("fab")   // FAB na tela home de transmissões
                    ?: prof.descriptionsFor("broadcast_new_button")
                        .firstNotNullOfOrNull { ops.byContentDesc(it) }
                    ?: listOf("Nova lista", "Nova transmissão", "New list", "New broadcast")
                        .firstNotNullOfOrNull { ops.byContentDesc(it) }
                    ?: listOf("Nova lista", "Nova transmissão", "New list")
                        .firstNotNullOfOrNull { ops.menuItemContaining(it) }
                if (novo != null) {
                    ops.click(novo)
                    delay(900)
                    return "menu"
                }
            }
            ops.performGlobalBack()
            delay(400)
        }

        // B) FAB → Nova transmissão (WA hardcoded: id/fab com desc "Nova conversa")
        val fab = prof.idFor("home_fab")?.let { ops.firstByViewId(it) }
            ?: ops.firstByViewId("fab")
        if (fab != null) {
            ops.click(fab)
            delay(900)
            val linha = prof.descriptionsFor("new_broadcast_row")
                .firstNotNullOfOrNull { ops.menuItemContaining(it) ?: ops.byContentDesc(it) }
                ?: listOf(
                    "Nova lista de transmissão", "Nova transmissão",
                    "New broadcast list", "New broadcast"
                ).firstNotNullOfOrNull { ops.menuItemContaining(it) ?: ops.byContentDesc(it) }
            if (linha != null) {
                ops.click(linha)
                delay(900)
                return "seletor"
            }
            ops.performGlobalBack()
            delay(400)
        }

        // C) Aba Ferramentas → Transmissões comerciais (WhatsApp Business)
        val aba = prof.descriptionsFor("tools_tab")
            .firstNotNullOfOrNull { ops.byExactText(it).firstOrNull() }
        if (aba != null) {
            ops.click(aba)
            delay(900)
            val linha = prof.descriptionsFor("tools_business_broadcasts")
                .firstNotNullOfOrNull { ops.byExactText(it).firstOrNull() }
            if (linha != null) {
                ops.click(linha)
                delay(900)
                return "ferramentas"
            }
            val conversas = prof.descriptionsFor("chats_tab")
                .firstNotNullOfOrNull { ops.byExactText(it).firstOrNull() }
            if (conversas != null) ops.click(conversas) else ops.performGlobalBack()
            delay(500)
        }

        return null
    }

    /** Toca no botão "Criar" da lista de transmissão. */
    private suspend fun createList() {
        checkExpiry()
        // Fallbacks WA: next_btn → action_done → done_btn → texto "Criar"/"Create"
        val btn = prof.idFor("create_button")?.let { ops.firstByViewId(it) }
            ?: ops.firstByViewId("next_btn")
            ?: ops.firstByViewId("action_done")
            ?: ops.firstByViewId("done_btn")
            ?: prof.descriptionsFor("create_button").firstNotNullOfOrNull { ops.byContentDesc(it) }
            ?: listOf("Criar", "Create", "Confirmar", "OK")
                .firstNotNullOfOrNull { ops.menuItemContaining(it) }
            ?: throw IllegalStateException("botão de criar não localizado")
        if (!ops.click(btn)) throw IllegalStateException("botão de criar não pode ser acionado")
        delay(600)
    }

    // ── Esperas condicionadas ────────────────────────────────────────────────

    private suspend fun waitViewId(suffix: String?, timeoutMs: Long): Boolean {
        suffix ?: return false
        return waitUntil(timeoutMs) { ops.existsViewId(suffix) }
    }

    private suspend fun waitExactNameRows(
        name: String,
        timeoutMs: Long,
        antes: Set<String> = emptySet(),
    ): List<AccessibilityNodeInfo> {
        // Fallback WA 2.26+: contactpicker_row_name
        val rowId = prof.idFor("row_name") ?: "contactpicker_row_name"
        val semResultadosId = prof.idFor("no_results")
        val alvo = name.trim()
        var anterior: Set<String>? = null
        var estaveis = 0
        var avisos = 0
        var vaziosEstaveis = 0
        val inicio = SystemClock.uptimeMillis()
        var decorrido = 0L
        var poll = POLL_RAPIDO
        while (SystemClock.uptimeMillis() - inicio < timeoutMs) {
            checkExpiry()
            decorrido = SystemClock.uptimeMillis() - inicio
            if (ops.root() == null) { delay(poll); poll = POLL_NORMAL; continue }
            val todas = ops.byViewId(rowId)
            val nomes = todas.mapNotNull { it.text?.toString()?.trim() }.toSet()
            val mudou = (todas.isEmpty() && antes.isNotEmpty()) || (nomes.isNotEmpty() && nomes != antes)
            if (!mudou) { delay(poll); poll = POLL_NORMAL; continue }
            val exatas = todas.filter { it.text?.toString()?.trim() == alvo }
            if (exatas.isNotEmpty()) return exatas
            if (todas.isEmpty()) {
                val avisou = semResultadosId != null && ops.existsViewId(semResultadosId)
                avisos = if (avisou) avisos + 1 else 0
                if (avisos >= 2) return emptyList()
                vaziosEstaveis++
                if (vaziosEstaveis >= CICLOS_VAZIO_ESTAVEL && decorrido >= PISO_VAZIO_MS) return emptyList()
            } else {
                vaziosEstaveis = 0
                estaveis = if (nomes == anterior) estaveis + 1 else 0
                anterior = nomes
                if (estaveis >= CICLOS_ESTAVEIS && decorrido >= PISO_VEREDITO_MS) return emptyList()
            }
            delay(poll); poll = POLL_NORMAL
        }
        return emptyList()
    }

    private suspend inline fun waitUntil(timeoutMs: Long, crossinline cond: () -> Boolean): Boolean {
        val fim = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            checkExpiry()
            if (cond()) return true
            if (SystemClock.uptimeMillis() >= fim) return false
            delay(POLL_NORMAL)
        }
    }

    companion object {
        private const val TAG = "BroadcastEngine"
        const val MIN_RECIPIENTS = 2

        private const val POLL_RAPIDO = 50L
        private const val POLL_NORMAL = 80L
        private const val BUSCA_TIMEOUT_MS = 6000L
        private const val PREPARO_TIMEOUT_MS = 2500L
        private const val CICLOS_ESTAVEIS = 3
        private const val PISO_VEREDITO_MS = 400L
        private const val MARCACAO_TIMEOUT_MS = 800L
        private const val CICLOS_VAZIO_ESTAVEL = 6
        private const val PISO_VAZIO_MS = 1200L
        private val NUMERICO = Regex("^[+\\d\\s()\\-]+$")
    }
}

/**
 * Utilitário para interpretar informações de listas de transmissão lidas do WhatsApp.
 */
object BroadcastParser {
    /**
     * Extrai a quantidade de contatos/destinatários a partir do subtítulo ou título da linha da lista.
     * Exemplos: "256 destinatários" -> 256, "5 recipients" -> 5, "12 contatos" -> 12.
     */
    fun parseContactCount(subtitle: String, title: String): Int {
        val regex = Regex("""(\d+)\s*(?:destinat[áa]rios?|contatos?|recipients?|pessoas?)""", RegexOption.IGNORE_CASE)
        regex.find(subtitle)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        regex.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        subtitle.trim().toIntOrNull()?.let { return it }
        return 0
    }
}
