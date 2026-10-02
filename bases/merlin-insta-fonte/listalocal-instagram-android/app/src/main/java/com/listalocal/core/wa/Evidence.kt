package com.listalocal.core.wa

import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.UiNode

/**
 * Provas sobre a árvore de acessibilidade do WhatsApp.
 * API espelhada com core/ig/Evidence para que DmFlow reutilize a mesma lógica.
 *
 * // A CALIBRAR (etapa E8): todos os ids marcados precisam ser medidos no
 * aparelho real com uiautomatorviewer ou adb shell uiautomator dump.
 */
object Evidence {

    fun norm(texto: String?): String = texto?.trim().orEmpty()

    // ── Cabeçalho ─────────────────────────────────────────────────────────────

    /** Título do cabeçalho da conversa. // A CALIBRAR: id "conversation_contact_name" */
    fun tituloDaConversa(root: UiNode, prof: SelectorProfile): String? =
        prof.nos(root, "conversation_contact_name").firstOrNull()
            ?.let { norm(it.text ?: it.contentDescription).takeIf { s -> s.isNotEmpty() } }

    /** Nome do cabeçalho bate com o nome esperado? */
    fun mesmoNome(titulo: String?, nome: String): Boolean {
        if (titulo.isNullOrBlank() || nome.isBlank()) return false
        return norm(titulo).equals(norm(nome), ignoreCase = true) ||
            norm(titulo).contains(norm(nome), ignoreCase = true)
    }

    // ── Campo e botão Enviar ───────────────────────────────────────────────────

    /** Campo de mensagem. // A CALIBRAR: id "message_input_text" */
    fun campo(root: UiNode, prof: SelectorProfile): UiNode? =
        prof.nos(root, "message_input_text").firstOrNull()

    /** Texto do campo (vazio quando não há botão Enviar habilitado). */
    fun textoDoCampo(root: UiNode, prof: SelectorProfile): String {
        if (botaoEnviar(root, prof) == null) return ""
        val t = norm(campo(root, prof)?.text)
        val dicas = prof.descriptionsFor("composer_hint")
        return if (dicas.any { t.equals(it, ignoreCase = true) }) "" else t
    }

    /** Botão Enviar habilitado. // A CALIBRAR: id "send" */
    fun botaoEnviar(root: UiNode, prof: SelectorProfile): UiNode? =
        prof.nos(root, "send").firstOrNull { it.isEnabled }

    // ── Estado da conversa ─────────────────────────────────────────────────────

    /** Lista de mensagens visível. // A CALIBRAR: id "message_list" */
    fun temMensagens(root: UiNode, prof: SelectorProfile): Boolean =
        prof.nos(root, "message_list").isNotEmpty()

    /** Nó por rótulo de contentDescription ou text. */
    fun porRotulo(root: UiNode, prof: SelectorProfile, papel: String): UiNode? {
        val rotulos = prof.descriptionsFor(papel).map { it.lowercase() }
        if (rotulos.isEmpty()) return null
        return root.walk().firstOrNull { n ->
            listOfNotNull(n.text, n.contentDescription)
                .map { it.trim().lowercase() }
                .any { l -> rotulos.any { r -> l == r || l.contains(r) } }
        }
    }

    /** Conta lida no título ou na aba de perfil. */
    fun conta(root: UiNode, prof: SelectorProfile, papel: String = "profile_title"): String? =
        prof.nos(root, papel).firstOrNull()?.let { norm(it.text).takeIf { s -> s.isNotEmpty() } }

    // ── Filtros de recusa ──────────────────────────────────────────────────────

    /** Aviso de restrição. // A CALIBRAR: id "restriction_notice" */
    fun restricao(root: UiNode, prof: SelectorProfile): Boolean =
        prof.nos(root, "restriction_notice").isNotEmpty() ||
            prof.nos(root, "blocked_screen").isNotEmpty()

    /** Número não tem WhatsApp ou bloqueou. // A CALIBRAR: id "unavailable_notice" */
    fun naoSegue(root: UiNode, prof: SelectorProfile): Boolean =
        prof.nos(root, "unavailable_notice").isNotEmpty() ||
            prof.nos(root, "invalid_number_notice").isNotEmpty()

    /** Conta não recebe. // A CALIBRAR: id "cannot_send_message" */
    fun indisponivel(root: UiNode, prof: SelectorProfile): Boolean =
        prof.nos(root, "cannot_send_message").isNotEmpty()

    /** Mensagens temporárias ligadas. // A CALIBRAR: id "disappearing_messages_label" */
    fun temporaria(root: UiNode, prof: SelectorProfile): Boolean =
        prof.nos(root, "disappearing_messages_label").isNotEmpty()

    /** Conversa nova (sem histórico). */
    fun conversaNova(root: UiNode, prof: SelectorProfile): Boolean =
        !temMensagens(root, prof) && campo(root, prof) != null

    /** Subtítulo indica grupo. */
    fun subtituloDeGrupo(root: UiNode, prof: SelectorProfile): Boolean {
        val sub = prof.nos(root, "conversation_subtitle").firstOrNull()
            ?.let { norm(it.text) } ?: return false
        return sub.contains(" e mais ") || sub.count { it == ',' } >= 2
    }

    /**
     * Alguém pediu para parar?
     * Lê bolhas exceto os próprios textos e o cabeçalho.
     * // A CALIBRAR: id "message_text"
     */
    fun pediuParaParar(
        root: UiNode,
        prof: SelectorProfile,
        nossoTexto: String,
        username: String,
        nossos: Collection<String> = emptyList(),
    ): Boolean {
        val ignorar = (nossos + nossoTexto + username).map { norm(it).lowercase() }.toSet()
        val cab = tituloDaConversa(root, prof)?.lowercase().orEmpty()
        return prof.nos(root, "message_text").any { n ->
            val t = norm(n.text).lowercase()
            t.isNotEmpty() && t != cab && t !in ignorar &&
                OPT_OUT_RE.any { t.contains(it) }
        }
    }

    /** Falha de envio (ícone de reenvio). // A CALIBRAR: id "msg_status_retry" */
    fun falhaDeEnvio(root: UiNode, prof: SelectorProfile, nossos: Collection<String> = emptyList()): Boolean =
        prof.nos(root, "msg_status_retry").isNotEmpty() ||
            prof.nos(root, "msg_failed_icon").isNotEmpty()

    /** Conta de nós com este texto (confirmação de envio). // A CALIBRAR: id "message_text" */
    fun contarMensagem(root: UiNode, prof: SelectorProfile, texto: String): Int {
        val alvo = norm(texto)
        return prof.nos(root, "message_text").count { n ->
            val t = norm(n.text)
            t == alvo || (alvo.length >= 12 && t.contains(alvo))
        }
    }

    private val OPT_OUT_RE = listOf(
        "pare", "para", "stop", "sair", "cancelar",
        "não quero", "nao quero", "me remova", "me tire",
    )
}
