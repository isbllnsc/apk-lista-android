package com.listalocal.core.selectors

/**
 * Perfil de seletores para uma faixa de versoes do WhatsApp. Ordem de confianca:
 * viewId > classe/estado > content-desc > texto localizado. Versao sem perfil =>
 * o servico entra em WHATSAPP_VERSION_UNSUPPORTED (nao clica).
 *
 * IDs abaixo foram VALIDADOS em aparelho real no Spike A: Galaxy A14 5G,
 * WhatsApp 2.26.33.74, pt-BR (2026-08-28) — e RECONFIRMADOS por dump de
 * acessibilidade no WhatsApp 2.26.34.81 (2026-09-04): mesmo caminho
 * (menu -> Listas de transmissao -> create_new_broadcast_button), mesmos ids
 * de linha/busca/selecao; next_btn aparece so com 2+ selecionados; contas
 * comerciais nao sao adicionaveis (status "can't add this business").
 *
 * SOBRE O WHATSAPP BUSINESS: o perfil casa pela VERSAO, e o Business usa a
 * mesma numeracao. Os sufixos de id tendem a ser os mesmos (mesmo codigo-base,
 * so muda o prefixo do pacote), mas isso NAO foi verificado em aparelho — por
 * isso [validatedOn] lista apenas os apps onde o perfil foi realmente
 * comprovado. Para os demais, o servico exige uma verificacao de
 * compatibilidade na tela real antes de operar.
 */
data class SelectorProfile(
    val name: String,
    val versionPrefixes: List<String>,
    val ids: Map<String, String>,
    val descriptions: Map<String, List<String>> = emptyMap(),
    /** Apps em que este perfil foi confirmado em aparelho real. */
    val validatedOn: Set<TargetApp> = setOf(TargetApp.WHATSAPP),
) {
    fun idFor(key: String): String? = ids[key]

    fun descriptionsFor(key: String): List<String> = descriptions[key] ?: emptyList()

    fun isValidatedFor(app: TargetApp): Boolean = app in validatedOn

    /** Chaves de id que precisam existir na tela para o fluxo funcionar. */
    fun requiredIdKeys(): List<String> = REQUIRED_ID_KEYS.filter { ids.containsKey(it) }

    companion object {
        /**
         * O que a verificacao de compatibilidade procura. Sao os ids sem os
         * quais o fluxo nao anda: abrir o menu, achar a busca, ler as linhas
         * de contato e chegar ao botao de criar.
         */
        val REQUIRED_ID_KEYS = listOf(
            "home_fab",
            "menu_overflow",
            "picker_search",
            "picker_search_input",
            "row_name",
            // "create_button" fica de fora DE PROPOSITO: no 2.26.34.81 o next_btn
            // so existe com 2+ selecionados, e a verificacao nao seleciona ninguem.
            // A guarda real esta em createList(): sem o botao, parada segura.
        )

        val WA_2_26 = SelectorProfile(
            name = "wa-2.26-ptBR-en",
            versionPrefixes = listOf("2.26."),
            ids = mapOf(
                "menu_overflow" to "menuitem_overflow",
                "menu_title" to "title",                       // itens do menu (usar + texto)
                "broadcast_new_button" to "create_new_broadcast_button",
                "picker_search" to "menuitem_search",
                "picker_search_input" to "search_src_text",
                "row_container" to "row_container",
                "row_name" to "chat_able_contacts_row_name",
                "row_status" to "chat_able_contacts_row_status",
                "selection_check" to "selection_check",
                "selected_list" to "selected_list",
                "selected_chip_name" to "contact_name",
                "create_button" to "next_btn",                 // desc "Criar", so >=2 selecionados
                "home_fab" to "fab",
                // RecyclerView do seletor (2.26.34.81): rolagem por acao de acessibilidade.
                "picker_list" to "contacts_wds_list",
                // Seletor sem correspondencia para a busca (2.26.34.81).
                "no_results" to "search_no_matches",
                // Tela "Listas de transmissao" (2.26.34.81): cota mensal de envios.
                "quota_sent" to "delivered_amount",
                "quota_remaining" to "remaining_amount",
                "quota_period" to "time_interval",
            ),
            descriptions = mapOf(
                // O nome do recurso mudou ao longo das versoes. No WhatsApp
                // comum e "Listas de transmissao"; no Business, a ajuda oficial
                // hoje descreve "Transmissao comercial" (business broadcast).
                // Sao rotulos COMPLETOS de proposito: "Listas" sozinho e outro
                // recurso (filtros de conversa) e cair nele seria erro grave.
                "menu_broadcast_lists" to listOf(
                    "Listas de transmissão", "Broadcast lists",
                    "Transmissão comercial", "Transmissões comerciais",
                    "Business broadcast", "Business broadcasts",
                ),
                // Layout mais novo: a transmissao vira uma linha dentro do
                // seletor de nova conversa, em vez de item do menu.
                "new_broadcast_row" to listOf(
                    "Nova transmissão", "New broadcast",
                    "Transmissão comercial", "Business broadcast",
                ),
                "broadcast_new_button" to listOf("Nova transmissão", "New broadcast"),
                // Terceiro ponto de entrada documentado pelo WhatsApp Business.
                // Ele e reconhecido para diagnostico; o fluxo comercial continua
                // bloqueado para automacao porque pode envolver revisao e pagamento.
                "tools_tab" to listOf("Ferramentas", "Tools"),
                "chats_tab" to listOf("Conversas", "Chats"),
                "tools_business_broadcasts" to listOf(
                    "Transmissões comerciais", "Transmissão comercial",
                    "Business broadcasts", "Business broadcast",
                ),
                "create_button" to listOf("Criar", "Create", "Avançar", "Next"),
                "menu_overflow" to listOf("Mais opções", "More options"),
                "picker_search" to listOf("Pesquisar", "Search"),
            ),
            validatedOn = setOf(TargetApp.WHATSAPP),
        )

        private val ALL = listOf(WA_2_26)

        fun forVersion(versionName: String): SelectorProfile? =
            ALL.firstOrNull { p -> p.versionPrefixes.any { versionName.startsWith(it) } }

        /**
         * Perfil candidato para um app + versao. Casar aqui NAO significa que o
         * perfil vale para aquele app: quem responde isso e [isValidatedFor].
         */
        fun forApp(app: TargetApp, versionName: String): SelectorProfile? =
            forVersion(versionName)
    }
}
