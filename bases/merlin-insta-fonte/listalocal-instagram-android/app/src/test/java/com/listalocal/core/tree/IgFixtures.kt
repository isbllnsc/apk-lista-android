package com.listalocal.core.tree

/**
 * Arvores falsas do Instagram 448 (pt-BR), montadas a partir da calibracao da
 * rodada 1 (ids das arvores de acessibilidade e de Views). Nomes e @ sao
 * ficticios: os dumps reais tem terceiros e nao entram no projeto.
 */
const val IG_ID = "com.instagram.android:id/"

fun ig(
    id: String? = null,
    text: String? = null,
    desc: String? = null,
    cls: String? = null,
    clickable: Boolean = false,
    checked: Boolean = false,
    scrollable: Boolean = false,
    selected: Boolean = false,
    visible: Boolean = true,
    children: List<UiNode> = emptyList(),
    bounds: Bounds? = null,
) = FakeNode(
    viewIdResourceName = id?.let { if (":id/" in it) it else IG_ID + it },
    className = cls,
    contentDescription = desc,
    text = text,
    isClickable = clickable,
    isChecked = checked,
    isScrollable = scrollable,
    isSelected = selected,
    isVisibleToUser = visible,
    children = children,
    bounds = bounds,
)

/**
 * Caixa de entrada do Direct (atividade principal; t04.xml): titulo com o @ da
 * conta, "Nova mensagem", e a lista com a busca (Compose, search_row), as
 * notas (cf_hub_recycler_view), os filtros e as conversas. Cada conversa e
 * uma linha em Compose SEM id: filho com a descricao "<nome>, <estado>", o
 * avatar clicavel (abre story: nunca tocar) e o nome em texto. Sem o @.
 */
fun inboxFixture(
    conta: String,
    conversas: List<Pair<String, String>> = listOf(
        "Ana Souza" to "Enviado",
        "Usuário do Instagram" to "Toque para conversar ·, 3 sem",
        "Bia Lima" to "não lidas, 2 novas mensagens ·, 1 h",
    ),
    pedidos: Boolean = false,
    /** Linhas com a posicao na tela (bounds), como no aparelho: a linha e uma View de Compose num lugar. */
    posicoes: Boolean = false,
    /** A linha "Pesquisar" (a 1a da lista) aparece: a caixa esta no topo. */
    noTopo: Boolean = true,
) = ig(
    children = listOf(
        ig(id = "igds_action_bar_title", text = conta, cls = "android.widget.Button", clickable = true),
        ig(desc = "Nova mensagem", cls = "android.widget.ImageView", clickable = true),
        ig(id = "inbox_refreshable_thread_list_recyclerview", scrollable = true, children = listOfNotNull(
            ig(id = "search_row", children = listOf(ig(clickable = true, children = listOf(
                ig(desc = "Pesquisar"), ig(id = "ig_text", text = "Pesquisar", cls = "android.widget.TextView"),
            )))).takeIf { noTopo },
            ig(id = "cf_hub_recycler_view", scrollable = true, children = listOf(
                ig(id = "pog_root_view", desc = "Adicionar nota", clickable = true, children = listOf(
                    ig(children = listOf(ig(desc = "Sua nota"))), ig(id = "pog_name", text = "Sua nota"),
                )),
                ig(id = "pog_root_view", desc = "Bia Lima", clickable = true, children = listOf(
                    ig(desc = "Bia Lima, nota"), ig(id = "pog_name", text = "Bia Lima"),
                )),
            )),
            ig(scrollable = true, children = listOf(
                ig(id = "global_filter_pill", text = "Filtros", clickable = true),
                ig(text = "Principal 6", desc = "Principal 6", clickable = true, selected = !pedidos),
                ig(text = "Pedidos", clickable = true, selected = pedidos),
                ig(text = "Geral", clickable = true),
            )),
        ) + conversas.mapIndexed { i, (nome, estado) -> linhaDaCaixa(nome, estado, topo = if (posicoes) LINHA_TOPO + i * LINHA_ALTURA else null) }),
        abasDeBaixo(marcada = "direct"),
    ),
)

/**
 * A barra de abas de baixo da atividade principal, como na tela real (perfil_proprio.xml):
 * 5 abas lado a lado no rodape. [direct]/[perfil] = rotulo e id das abas Direct e Perfil.
 */
fun abasDeBaixo(
    marcada: String? = null,
    direct: String = "Mensagem",
    perfil: String = "Perfil",
    idDirect: String = "direct_tab",
    idPerfil: String = "profile_tab",
) = ig(id = "tab_bar", children = listOf(
    ig(id = "feed_tab", clickable = true, bounds = Bounds(0, 2138, 216, 2273)),
    ig(id = "clips_tab", clickable = true, bounds = Bounds(216, 2138, 432, 2273)),
    ig(id = idDirect, desc = direct, clickable = true, selected = marcada == "direct", bounds = Bounds(432, 2138, 648, 2273)),
    ig(id = "search_tab", clickable = true, bounds = Bounds(648, 2138, 864, 2273)),
    ig(id = idPerfil, desc = perfil, clickable = true, selected = marcada == "perfil", bounds = Bounds(864, 2138, 1080, 2273)),
))

/** Onde a primeira linha da caixa comeca e a altura de cada uma (caixa_principal.xml: 957, 214 px). */
const val LINHA_TOPO = 957
const val LINHA_ALTURA = 214

/** Uma conversa na caixa de entrada, no formato Compose medido (t04.xml). [topo]: a posicao na tela. */
fun linhaDaCaixa(nome: String, estado: String, topo: Int? = null) = ig(children = listOf(ig(children = listOf(
    ig(clickable = true, bounds = topo?.let { Bounds(0, it, 1080, it + LINHA_ALTURA) }, children = listOf(
        ig(desc = "$nome, $estado"),
        ig(clickable = true, children = listOf(ig(desc = "Foto do perfil de $nome"))),
        ig(children = listOf(ig(text = nome, cls = "android.widget.TextView"))),
        ig(text = estado, cls = "android.widget.TextView"),
        ig(cls = "android.widget.Button"),
    )),
))))

/** Perfil proprio: o @ no titulo e o numero de seguidores (desc "388seguidores"). */
fun perfilFixture(conta: String, seguidores: Int = 388) = ig(children = listOf(
    ig(id = "action_bar_title", text = conta, desc = conta),
    ig(id = "profile_header_followers_stacked_familiar", desc = "${seguidores}seguidores", clickable = true),
    ig(desc = "Opções", clickable = true),
    abasDeBaixo(marcada = "perfil"),
))

/**
 * Perfil > Seguidores (t10a.xml, t10f.xml): abas ("388 seguidores" marcada),
 * a busca e as linhas visiveis dentro de android:id/list, no ViewPager (que
 * tambem rola: rolar ele troca de aba). [linhas] = (@, nome) na tela;
 * [sugestoes] = linhas de "Sugestoes para voce" visiveis (o fim).
 * [seguindoAoLado] = a pagina "seguindo" na arvore, fora da tela.
 */
fun seguidoresFixture(
    conta: String,
    linhas: List<Pair<String, String>>,
    sugestoes: Int = 0,
    aba: String = "seguidores",
    seguindoAoLado: List<String> = emptyList(),
    /** A barra de abas ainda nao chegou (renderiza depois das linhas). */
    semAbas: Boolean = false,
) = ig(children = listOfNotNull(
    ig(id = "action_bar_title", text = conta, desc = conta, clickable = true),
    if (semAbas) null else ig(id = "unified_follow_list_tab_layout", scrollable = true, children = listOf(
        ig(id = "title", text = "388 seguidores", clickable = true, selected = aba == "seguidores"),
        ig(id = "title", text = "413 seguindo", clickable = true, selected = aba == "seguindo"),
        ig(id = "title", text = "Sinalizadas", clickable = true),
    )),
    ig(id = "unified_follow_list_view_pager", scrollable = true, children = listOf(
        ig(scrollable = true, children = listOf(
            ig(id = "layout_listview_parent_container", children = listOf(
                ig(id = "android:id/list", scrollable = true, children = listOf(
                    ig(id = "row_search_edit_text", text = "Pesquisar", cls = "android.widget.EditText", clickable = true),
                ) + linhas.map { (u, n) -> linhaDeSeguidor(u, n) } + (1..sugestoes).map(::sugestao)),
            )),
            ig(id = "layout_listview_parent_container", visible = false, children = listOf(
                ig(id = "android:id/list", scrollable = true, visible = false,
                    children = seguindoAoLado.map { linhaDeSeguidor(it, it).invisivel() }),
            )),
        )),
    )),
))

fun linhaDeSeguidor(u: String, nome: String) = ig(clickable = true, children = listOf(
    ig(id = "follow_list_container", clickable = true, children = listOf(
        ig(id = "follow_list_user_imageview", desc = "Foto do perfil", clickable = true),
        ig(id = "follow_list_content_container", children = listOf(
            ig(id = "follow_list_user_info_container", children = listOf(ig(id = "follow_list_username", text = u))),
            ig(id = "follow_list_subtitle", text = nome),
        )),
        ig(id = "follow_list_row_large_follow_button", text = "Mensagem", desc = "Enviar mensagem para $nome", clickable = true),
        ig(desc = "Ignorar", clickable = true),
    )),
))

/** A subarvore inteira fora da tela (como a pagina ao lado de um ViewPager). */
fun FakeNode.invisivel(): FakeNode = copy(isVisibleToUser = false, children = children.map { (it as FakeNode).invisivel() })

private fun sugestao(i: Int) = ig(id = "recommended_user_row_content_identifier", clickable = true, children = listOf(
    ig(id = "row_recommended_user_username", text = "Sugerida $i"),
    ig(id = "row_recommended_user_follow_button", text = "Seguir", desc = "Seguir Sugerida $i", clickable = true),
    ig(id = "row_recommended_hide_icon_button", desc = "Ignorar", clickable = true),
))

/**
 * Conversa 1:1 aberta pelo link ig.me (INSTAGRAM-APP-REAL.md, 1.f).
 * [username] = o que o subtitulo do cabecalho mostra (o @, ou "Online agora";
 * null = nao exposto). [cartao] = o @ no cartao do topo (null = fora da tela,
 * como em conversa longa). [campo] null = so a dica. O Enviar so existe com
 * texto no campo, como no Instagram (em conversa nova ele aparece desabilitado).
 * [novaConversa] = sem historico: Enviar ja visivel e desabilitado e a bandeja
 * "Diga 'ola' enviando uma figurinha" (tocar numa figurinha ENVIA). [iscas] =
 * uma mensagem recebida com floating_send_container/send_label "Enviar", que
 * NAO sao o Enviar. [subtituloTexto] = false: o subtitulo (IgView) sem texto.
 * [avisos] = faixa do Instagram FORA da lista de mensagens (no lugar em que o
 * Instagram avisa; dentro da lista o texto e de bolha).
 */
fun threadFixture(
    username: String?,
    nome: String = "Pessoa Teste",
    mensagens: List<String> = emptyList(),
    campo: String? = null,
    comEnviar: Boolean = campo != null,
    enviarHabilitado: Boolean = true,
    avisos: List<String> = emptyList(),
    subtituloNaDescricao: Boolean = false,
    cartao: String? = username,
    cartaoExtra: List<String> = emptyList(),
    dica: String = "Mensagem...",
    novaConversa: Boolean = false,
    iscas: Boolean = false,
    subtituloTexto: Boolean = true,
    /** false = a acessibilidade nao traz a lista de mensagens (message_list). */
    comLista: Boolean = true,
    /** Blocos a mais na lista de mensagens (ex.: um perfil compartilhado, com "Ver perfil"). */
    blocos: List<FakeNode> = emptyList(),
): FakeNode {
    val titulo = if (subtituloNaDescricao) {
        ig(id = "header_title_subtitle_container", desc = "$nome, ${username.orEmpty()}", clickable = true)
    } else {
        ig(
            id = "header_title_subtitle_container", clickable = true,
            children = listOfNotNull(
                ig(id = "header_title", text = nome, cls = "com.instagram.common.ui.base.IgTextView"),
                username?.let {
                    ig(id = "header_subtitle", text = it.takeIf { subtituloTexto }, cls = "com.instagram.common.ui.base.IgView")
                },
            ),
        )
    }
    val enviar = FakeNode(
        viewIdResourceName = IG_ID + "row_thread_composer_send_button_container",
        isClickable = true, isEnabled = enviarHabilitado && (campo != null || !novaConversa),
        children = listOf(ig(id = "row_thread_composer_send_button_background"), ig(id = "row_thread_composer_send_button_icon")),
    )
    val botoes = if (comEnviar || novaConversa) {
        listOf(ig(id = "row_thread_composer_button_sticker_shortcut", clickable = true), enviar)
    } else {
        listOf(
            ig(id = "row_thread_composer_button_camera", clickable = true),
            ig(id = "row_thread_composer_voice", clickable = true),
            ig(id = "row_thread_composer_button_gallery", clickable = true),
            ig(id = "row_thread_composer_button_sticker", clickable = true),
            ig(id = "row_thread_composer_button_overflow", clickable = true),
        )
    }
    val topo = cartao?.let {
        listOf(ig(children = listOf(ig(text = nome), ig(text = it)) + cartaoExtra.map { t -> ig(text = t) } +
            ig(text = "Ver perfil", clickable = true)))
    }.orEmpty()
    val isca = if (iscas) {
        listOf(ig(children = listOf(
            ig(text = "comentou: olha isso"),
            ig(id = "floating_send_container", clickable = true, children = listOf(
                ig(id = "send_button_pill_container", children = listOf(ig(id = "send_label", text = "Enviar"))),
            )),
        )))
    } else emptyList()
    val bandeja = if (novaConversa) listOf(ig(text = "Diga 'olá' enviando uma figurinha"), ig(desc = "Figurinha", clickable = true)) else emptyList()
    val visto = if (mensagens.isNotEmpty()) listOf(ig(id = "seen_state_text", text = "Visto", cls = "TightTextView")) else emptyList()
    return ig(
        id = "thread_fragment_container",
        children = listOf(
            ig(
                id = "direct_thread_header",
                children = listOf(
                    ig(id = "header_left_button", clickable = true),
                    ig(id = "header_avatar", clickable = true),
                    titulo,
                    ig(id = "header_right_buttons", children = listOf(
                        ig(id = "0x15", clickable = true), ig(id = "0x16", clickable = true), ig(id = "0x17", clickable = true),
                    )),
                ),
            ),
            ig(
                id = "message_list", scrollable = true, cls = "CustomFadingEdgeRecyclerView",
                children = topo + isca + blocos + mensagens.map { ig(text = it) },
            ).takeIf { comLista },
        ).filterNotNull() + visto + avisos.map { ig(text = it) } + listOf(
            ig(
                id = "message_composer_bar",
                children = listOf(
                    ig(
                        id = "row_thread_composer_container",
                        children = listOf(
                            ig(id = "row_thread_composer_edittext", text = campo ?: dica,
                                cls = "android.widget.EditText", clickable = true),
                        ) + botoes,
                    ),
                ) + bandeja, // na barra do campo, fora das bolhas (t07h)
            ),
        ),
    )
}

/**
 * Amigos Proximos como na tela real (INSTAGRAM-APP-REAL.md, 3; t02a, t03a,
 * t03b): aviso, busca em search_box, cabecalho "N pessoas" com "Limpar tudo"
 * (row_header_action, NUNCA), membros marcados, "Sugestoes" desmarcadas,
 * caixa de marcar sem id e "Concluir" fixo embaixo. Buscando, so as linhas
 * que casam (um membro buscado aparece sozinho e marcado).
 */
/** [busca]: o que a lista mostra (a resposta); [campo]: o que esta escrito (a resposta do servidor chega depois). */
fun amigosFixture(linhas: List<Pair<String, Boolean>>, busca: String = "", campo: String = busca) = ig(
    id = "main_container",
    children = listOf(
        ig(id = "audience_picker_disclaimer_text",
            text = "Não enviamos notificações quando você edita sua lista Amigos Próximos. Como funciona."),
        ig(id = "search_box", children = listOf(
            ig(id = "search_edit_text", text = campo.ifEmpty { "Pesquisar" }, cls = "android.widget.EditText", clickable = true),
        ) + if (campo.isNotEmpty()) listOf(ig(id = "search_exit_button", text = "Cancelar", clickable = true)) else emptyList()),
        ig(
            id = "recycler_view", scrollable = true,
            children = if (busca.isNotEmpty()) linhas.map(::linhaAmigo) else {
                val (membros, sugestoes) = linhas.partition { it.second }
                listOf(ig(id = "row_header_container", children = listOf(
                    ig(id = "row_header_textview", text = "${membros.size} pessoas"),
                    ig(id = "row_header_action", text = "Limpar tudo", clickable = true),
                ))) + membros.map(::linhaAmigo) +
                    ig(id = "row_header_container", children = listOf(ig(id = "row_header_textview", text = "Sugestões"))) +
                    sugestoes.map(::linhaAmigo)
            },
        ),
        ig(id = "done_button", text = "Concluir", cls = "com.instagram.igds.components.button.IgdsButton", clickable = true),
    ),
)

private fun linhaAmigo(l: Pair<String, Boolean>) = ig(
    id = "row_user_container", clickable = true,
    children = listOf(
        ig(id = "row_user_info_layout", children = listOf(
            ig(id = "row_user_username", text = l.first),
            ig(id = "row_user_info", text = "Nome de ${l.first}"),
        )),
        ig(cls = "com.instagram.igds.components.checkbox.IgdsCheckBox", checked = l.second),
    ),
)
