package com.listalocal.core.selectors

import com.listalocal.core.tree.UiNode

/**
 * Perfil de seletores do Instagram. Ordem de confianca: viewId > classe/estado
 * > content-desc > texto localizado. Texto EXATO para escolher onde tocar;
 * "contem" so para reconhecer avisos. Nunca procurar "send" por pedaco: dentro
 * de mensagens existem `floating_send_container`, `send_button_pill_container`
 * e `send_label`, que NAO sao o Enviar.
 *
 * Fonte: calibracao da rodada 1 no celular do dono (INSTAGRAM-APP-REAL.md e
 * TESTE-ENVIO-REAL.md): Galaxy A14, Android 15, pt-BR, Instagram 448.0.0.52.84,
 * 25-26/09/2026. Ids e textos pt-BR confirmados na tela real. Chave em
 * [calibrar] = ainda nao lida PELA ACESSIBILIDADE (o uiautomator nao enxerga a
 * ModalActivity: conversa, Nova mensagem, Amigos Proximos) ou sem medicao
 * (textos em ingles, avisos que nao apareceram): CALIBRAR / PRECISA VERIFICAR
 * NO INSTAGRAM REAL. O app nao confia nela as cegas: cada fluxo confere a
 * chave na tela antes de agir e para se faltar.
 *
 * A versao do Instagram muda toda semana, entao o perfil nao casa por prefixo
 * de versao: quem autoriza um modo naquela versao e a conferencia na tela real
 * deste aparelho (CompatCheck, "Conferir o Instagram").
 */
data class SelectorProfile(
    val name: String,
    val ids: Map<String, String>,
    val descriptions: Map<String, List<String>> = emptyMap(),
    /** Onde e quando o perfil foi medido. */
    val medidoEm: String = "",
    /** Chaves ainda nao lidas pela acessibilidade ou sem medicao. */
    val calibrar: Set<String> = emptySet(),
    /**
     * Ids que a AUTO-CALIBRACAO aprendeu neste aparelho (papel -> id de verdade).
     * Na tela, so valem quando o id fixo de [ids] nao esta nela ([nos]). Vazio no perfil fixo.
     */
    val aprendidos: Map<String, String> = emptyMap(),
    /** Rotulos aprendidos por papel (o texto de verdade daquele aparelho/idioma). */
    val aprendidosDesc: Map<String, List<String>> = emptyMap(),
    /** Idioma detectado quando o perfil foi aprendido (ex.: "pt", "en", "es"). */
    val idioma: String = "",
    /**
     * Resolver papel por ASSINATURA ao vivo quando nem o id aprendido nem o fixo
     * batem na tela. O [IG_448] CRU mantem o comportamento de sempre (id conhecido
     * ou nada): e usado onde nao se pode chutar (ex.: NodeOps). O perfil que a
     * operacao/conferencia usa ([paraAparelho]) SEMPRE liga isto: com um perfil
     * aprendido por cima, ou — sem nada aprendido ainda — como BOOTSTRAP, para a
     * primeira conferencia navegar por forma num aparelho de ids diferentes e so
     * entao aprender. Ligar cedo e seguro: um id conhecido que bate sempre vence a
     * assinatura (SelectorProfile.nos), e a conferencia so libera com prova.
     */
    val assinaturaAoVivo: Boolean = false,
) {
    /** Id do papel: o aprendido (se houver), senao o fixo. */
    fun idFor(key: String): String? = aprendidos[key] ?: ids[key]

    /** Rotulos do papel: os aprendidos primeiro (idioma do aparelho), depois os fixos. */
    fun descriptionsFor(key: String): List<String> =
        (aprendidosDesc[key].orEmpty() + (descriptions[key] ?: emptyList())).distinct()

    /**
     * Os nos deste papel na arvore, na ordem de resolucao: id fixo -> id
     * aprendido -> assinatura ao vivo (so se [assinaturaAoVivo]). Um id conhecido que
     * bate vence sempre a assinatura (mais seguro); a assinatura so entra quando
     * NENHUM id conhecido aparece na tela. Vazio = papel sumiu: o chamador pausa
     * e pede reconferir, sem chutar.
     */
    fun nos(root: UiNode, key: String): List<UiNode> {
        // O id fixo (medido) na tela vence sempre: um id aprendido errado ficava gravado no aparelho, passava na
        // frente do fixo e valia ate no outro modo (a foto de um seguidor como aba Perfil, 26/09).
        ids[key]?.let(root::findByViewIdSuffix)?.takeIf { it.isNotEmpty() }?.let { return it }
        // Conta aberta (o @ do titulo): so por id exato. Nunca pela assinatura ao vivo: "texto parecido com @"
        // casa com o @ de um SEGUIDOR na lista (teste real 26/09 17:04: leu o @ de um seguidor como a
        // conta aberta, e travou a operacao).
        if (key in CONTA_KEYS) return aprendidos[key]?.let(root::findByViewIdSuffix).orEmpty()
        val aprendido = aprendidos[key]?.let(root::findByViewIdSuffix).orEmpty()
        // Abas: um id aprendido so vale DENTRO da barra de abas. Um aprendido errado (o botao "Enviar mensagem
        // para <seguidor>" como direct_tab) fazia a operacao tocar nele, abrindo a conversa de um terceiro.
        val valido = if (key in SignatureLibrary.ABAS) {
            val naBarra = root.walk().filter(SignatureLibrary.barraDeAbas).flatMap { it.children.asSequence() }.toSet()
            aprendido.filter { it in naBarra }
        } else {
            aprendido
        }
        if (valido.isNotEmpty()) return valido
        if (key in SO_POR_ID) return emptyList()
        if (assinaturaAoVivo) SignatureLibrary.para(key)?.let { sig ->
            RoleMatcher.todos(root, sig).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    /** O melhor no do papel por assinatura ao vivo (rotulo por regex), ou null. */
    fun noPorAssinatura(root: UiNode, key: String): UiNode? =
        if (assinaturaAoVivo) SignatureLibrary.para(key)?.let { RoleMatcher.achar(root, it) } else null

    /** Este perfil fixo com o que a calibracao aprendeu por cima (a operacao usa este). */
    fun comAprendido(p: PerfilAprendido): SelectorProfile = copy(
        name = "aprendido-${p.versao}-${p.idioma}-${p.modo}",
        aprendidos = p.ids, aprendidosDesc = p.descricoes, idioma = p.idioma, assinaturaAoVivo = true,
    )

    /**
     * O perfil que a operacao e a conferencia usam neste aparelho: o fixo com o
     * [aprendido] por cima, ou — quando nada foi aprendido ainda — o fixo com a
     * assinatura ao vivo ligada como BOOTSTRAP da calibracao. Nos dois casos a
     * assinatura ao vivo fica ligada (ver [assinaturaAoVivo]); o [IG_448] cru, nao.
     */
    fun paraAparelho(aprendido: PerfilAprendido?): SelectorProfile =
        aprendido?.let(::comAprendido) ?: copy(assinaturaAoVivo = true)

    /** Ids que so existem com uma conversa aberta: e a janela a ler (a Modal, nao a principal). */
    val conversaIds: List<String> get() = listOfNotNull(idFor("thread_header"), idFor("composer"))

    /** Ids da barra de abas: so a atividade principal os tem. */
    val abasIds: List<String> get() = listOfNotNull(idFor("direct_tab"), idFor("profile_tab"))

    companion object {
        val IG_448 = SelectorProfile(
            name = "ig-448-ptBR-en",
            medidoEm = "Instagram 448.0.0.52.84, pt-BR, Galaxy A14 (Android 15), 25/09/2026",
            ids = mapOf(
                // Atividade principal (uiautomator funciona): abas e titulos.
                "direct_tab" to "direct_tab",               // desc "Mensagem"
                "profile_tab" to "profile_tab",             // desc "Perfil"
                "inbox_title" to "igds_action_bar_title",   // o @ da conta aberta
                "profile_title" to "action_bar_title",      // o @ no perfil proprio (so na aba Perfil)
                // Destinatarios ao vivo (atividade principal; lidos pelo uiautomator na rodada 1).
                // Perfil > numero de seguidores (desc "388seguidores") > lista de Seguidores.
                "profile_followers" to "profile_header_followers_stacked_familiar",
                "follow_tab" to "title",                    // abas "388 seguidores" (marcada), "413 seguindo"...
                "follow_list" to "list",                    // android:id/list, o ListView que rola (NAO o ViewPager: troca de aba)
                "follow_row" to "follow_list_container",
                "follow_username" to "follow_list_username", // o @
                "follow_name" to "follow_list_subtitle",     // o nome
                // Fim da lista: comecam as "Sugestoes para voce" ("Seguir", "Ignorar": NUNCA tocar).
                "follow_end" to "recommended_user_row_content_identifier",
                "follow_search" to "row_search_edit_text", // busca da lista (dica "Pesquisar")
                // Caixa de entrada: linhas em Compose, sem id; a descricao e "<nome>, <estado>, <tempo>".
                "inbox_list" to "inbox_refreshable_thread_list_recyclerview",
                "inbox_notes" to "cf_hub_recycler_view",     // notas no topo: nao sao conversas
                "inbox_search" to "search_row",              // "Pesquisar" (Compose): tem o mesmo formato de uma linha
                "inbox_filters" to "global_filter_pill",     // chip "Filtros" da barra Filtros/Principal/Pedidos/Geral
                // Nova mensagem (ModalActivity): so para a conferencia ver a janela.
                "new_chat_to" to "direct_new_chat_to_field", // "Para:"
                // Conversa (ModalActivity).
                "thread_header" to "direct_thread_header",
                // O bloco clicavel do nome (abre "detalhes"): fica na arvore do servico mesmo quando o
                // direct_thread_header (layout sem clique) some; a descricao "nome, @" mora nele.
                "header_container" to "header_title_subtitle_container",
                "header_title" to "header_title",           // nome; o @ na "Conversa comercial"
                "header_subtitle" to "header_subtitle",     // o @, alternando com "Online agora" (IgView)
                "message_list" to "message_list",
                "composer_bar" to "message_composer_bar",
                "composer" to "row_thread_composer_edittext",
                // So aparece com texto no campo (substitui microfone, galeria, figurinha, +).
                "send" to "row_thread_composer_send_button_container",
                // Conversa nova: a bandeja "Diga 'ola' enviando uma figurinha" na barra do campo (t07h), fora das bolhas.
                "new_thread_tray" to "new_friend_bump_sticker_banner_container",
                // Amigos Proximos (ModalActivity).
                "cf_search" to "search_edit_text",
                "cf_row" to "row_user_container",
                "cf_username" to "row_user_username",       // o @; a caixa de marcar nao tem id
                "cf_done" to "done_button",                 // "Concluir"
                "cf_clear" to "row_header_action",          // "Limpar tudo": NUNCA tocar
                // Cabecalhos da lista inicial ("162 pessoas", "Sugestoes"): a busca filtrada nao tem (t03a, t03b).
                "cf_header" to "row_header_textview",
                // "Nao enviamos notificacoes quando voce edita sua lista Amigos Proximos": so nesta tela (nem na Nova
                // mensagem nem na folha de compartilhar, que tambem tem search_edit_text: t05d, t12d).
                "cf_disclaimer" to "audience_picker_disclaimer_text",
            ),
            descriptions = mapOf(
                "new_message" to listOf("Nova mensagem", "New message", "Nuevo mensaje"),
                "options" to listOf("Opções", "Options", "Opciones"),
                // Titulo da tela aberta por Opcoes (Bloks, sem id, sem rolagem pela acessibilidade: t01a-t01d).
                "settings" to listOf("Configurações e atividade", "Settings and activity", "Configuración y actividad"),
                // Configuracoes e atividade: texto "Amigos Próximos", desc "Amigos Próximos, 162".
                "cf_entry" to listOf("Amigos Próximos", "Close friends", "Close Friends", "Amigos cercanos", "Mejores amigos"),
                "cf_done" to listOf("Concluir", "Done", "Listo", "Hecho"),
                "cf_clear" to listOf("Limpar tudo", "Clear all", "Borrar todo", "Limpiar todo"),
                "send" to listOf("Enviar", "Send"),
                "composer_hint" to listOf("Mensagem...", "Mensagem…", "Message...", "Message…", "Mensaje...", "Mensaje…"),
                // Dica do campo com mensagens temporarias ligadas: a mensagem some depois de vista.
                // es e en ("Disappearing message") nao medidos: um rotulo a mais so recusa mais (nunca envia errado).
                "temporary" to listOf(
                    "Mensagem temporária", "Mensagem tempo", "Temporary message", "Disappearing message",
                    "Mensaje temporal", "Mensajes temporales",
                ),
                // O subtitulo do cabecalho no lugar do @ (comeco do texto). So com um destes (ou
                // sem subtitulo legivel) o nome do cabecalho vale como prova (DmFlow).
                "header_status" to listOf(
                    "Online", "Ativo", "Ativa", "Visto", "Conversa comercial", "Digitando",
                    "Active", "Seen", "Business chat", "Typing",
                ),
                // Subtitulo do cabecalho numa conversa com conta comercial (o @ vai no titulo).
                "business_chat" to listOf("Conversa comercial", "Business chat"),
                // Aba marcada da lista de seguidores ("388 seguidores"): so a de seguidores serve.
                "followers_tab" to listOf("seguidores", "seguidor", "followers"),
                // A dica da busca da lista de seguidores: o campo sem nada digitado (a acessibilidade devolve a dica).
                "follow_search_hint" to listOf("Pesquisar", "Search", "Buscar"),
                // So para a mensagem de recusa: a lista so e aceita com a aba de SEGUIDORES marcada (Listas).
                "following_tab" to listOf("seguindo", "following", "seguidos", "siguiendo", "a seguir"),
                // Conversa sem mensagens (bandeja de figurinhas: tocar numa ENVIA): nao e conversa existente.
                "new_thread" to listOf("enviando uma figurinha", "sending a sticker"),
                // Filtro marcado da caixa de entrada: "Pedidos" nao e conversa com quem segue.
                "requests" to listOf("Pedidos", "Requests"),
                // O chip de filtros da caixa sem filtro ligado (com um ligado, ex. "Nao lidos", a caixa mostra so um pedaco).
                "inbox_filters" to listOf("Filtros", "Filters", "Filtri", "Filter", "Filtres"),
                // A linha "Pesquisar" do topo da caixa: tem o formato de uma conversa e nunca e uma.
                "inbox_search" to listOf("Pesquisar", "Search", "Buscar", "Suchen", "Cerca"),
                // Cartao do topo da conversa (Compose): foto, nome, @, "Ver perfil".
                "profile_card" to listOf("Ver perfil", "View profile"),
                // Cartao do topo quando a pessoa nao segue a conta: nao e seguidor.
                "not_following" to listOf("Vocês não se seguem no Instagram", "You don't follow each other on Instagram"),
                // Avisos: reconhecidos por "contem", nunca usados para escolher toque.
                "not_sent" to listOf(
                    "Não enviada", "Não foi possível enviar", "Toque para tentar novamente",
                    "Not sent", "Couldn't send", "Failed to send", "Tap to retry",
                    "No enviado", "No se pudo enviar", "Toca para reintentar",
                ),
                "restriction" to listOf(
                    "Tente novamente mais tarde", "Try again later",
                    "Restringimos determinadas atividades", "We restrict certain activity",
                    "Ação bloqueada", "Action blocked",
                    "Sua conta foi restringida", "Your account has been restricted",
                    "Inténtalo de nuevo más tarde", "Acción bloqueada", "Tu cuenta ha sido restringida",
                ),
                "unavailable" to listOf(
                    "não pode receber mensagens", "Não é possível enviar mensagem",
                    "Esta conta não está disponível", "Usuário do Instagram",
                    "can't receive messages", "You can't message", "This account isn't available",
                    "Instagram User",
                    "no puede recibir mensajes", "No puedes enviar mensajes",
                    "Esta cuenta no está disponible", "Usuario de Instagram",
                ),
            ),
            calibrar = setOf(
                // Lidos na tela (arvore de Views), falta a leitura pela acessibilidade.
                "new_chat_to", "thread_header", "header_container", "header_title", "header_subtitle", "message_list",
                "composer_bar", "composer", "send", "new_thread_tray", "cf_search", "cf_row", "cf_username", "cf_done",
                "composer_hint", "temporary", "profile_card", "not_following", "header_status", "business_chat",
                // Vistos na tela pelo uiautomator, sem leitura pelo app ainda.
                "profile_followers", "follow_tab", "follow_list", "follow_row", "follow_username", "follow_name",
                "follow_search", "follow_search_hint",
                "follow_end", "inbox_list", "inbox_notes", "inbox_search", "inbox_filters", "followers_tab", "requests", "new_thread",
                // Nunca vistos (ingles, rotulo do Enviar, avisos que nao apareceram).
                "not_sent", "restriction", "unavailable",
            ),
        )

        /**
         * Chaves que "Conferir o Instagram" exige por modo antes de liga-lo.
         * "new_chat_to" prova que o servico enxerga a ModalActivity (a mesma
         * janela da conversa), abrindo Nova mensagem sem escrever nada.
         */
        /** Papeis que dizem QUAL conta esta aberta: nunca resolvidos por assinatura (ver nos()). */
        val CONTA_KEYS = setOf("inbox_title", "profile_title")

        /**
         * Linha, @ e fim da lista de seguidores: so pelo id de verdade (aprendido ou fixo). Pela forma
         * ("clicavel, numa lista, com uma palavra"), o rotulo da aba "Sinalizadas" virava um seguidor e o
         * perfil, a caixa de entrada e Configuracoes viravam a lista; e o fim achado pela forma, com as
         * linhas ilegiveis, dava "Fim da lista" sem ninguem. Sem o id na tela, "Seus seguidores" para com aviso.
         */
        val SO_POR_ID = setOf("follow_row", "follow_username", "follow_name", "follow_end", "inbox_list")
        // inbox_list: a forma (lista vertical que rola) tambem e a lista de seguidores, e dali Listas.conversas lia
        // "Seguir de volta" como conversas. A conferencia DM o aprende pela forma na caixa provada; a operacao, pelo id.

        val DM_KEYS = listOf("direct_tab", "inbox_title", "new_message", "new_chat_to")
        // cf_clear no gate: o modo só libera depois de o app reconhecer "Limpar tudo"
        // (o botão que NUNCA pode ser tocado) neste aparelho/idioma. Ver CloseFriendsFlow.naoLimpar.
        // cf_row/cf_username no gate: sao o que a operacao usa para achar a linha da pessoa e ler
        // o @/marca (CloseFriendsFlow.marcar, Evidence.linhasAmigos). Sem prova de que o app os
        // reconhece neste aparelho, o modo NAO libera (senao toda marcacao viria NAO_ENCONTRADO calado).
        // profile_title no gate: a operacao so marca na conta conferida (Fila.amigos), e sem a conta lida
        // a conferencia passava com conta = null.
        val CF_KEYS = listOf(
            "profile_tab", "profile_title", "options", "settings", "cf_entry", "cf_search", "cf_done", "cf_clear",
            "cf_row", "cf_username",
        )

        /**
         * Perfil candidato para uma versao. Casar aqui NAO autoriza operar:
         * quem autoriza e a conferencia na tela real (CompatCheck).
         */
        fun forVersion(versionName: String): SelectorProfile? =
            IG_448.takeIf { versionName.isNotBlank() }
    }
}
