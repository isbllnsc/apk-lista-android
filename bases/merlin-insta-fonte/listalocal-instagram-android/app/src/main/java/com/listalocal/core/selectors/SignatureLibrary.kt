package com.listalocal.core.selectors

import com.listalocal.core.tree.UiNode

/**
 * As assinaturas de cada papel do Instagram que o app usa. É a "dica/atalho"
 * (o id do perfil fixo entra como bônus) E o motor da auto-calibração: com
 * estas assinaturas, "Conferir o Instagram" descobre os ids/textos de verdade
 * no aparelho e a operação resolve por papel quando o id conhecido não bate.
 *
 * Regex multi-idioma (pt/en/es e mais), nunca lista fechada; forma (className,
 * estado, região, vizinhança) para casar mesmo num idioma não previsto. Cada
 * assinatura vive aqui, em código (constante). Só o que foi APRENDIDO (ids e
 * textos daquele aparelho) é gravado; as assinaturas não.
 *
 * TETO DE IDIOMAS (honestidade): a auto-calibração generaliza bem entre celulares
 * e versões NOVAS no MESMO idioma (id/estrutura), e entre os idiomas cujas regex
 * abaixo cobrem o rótulo. Papéis com [RoleSignature.textoObrigatorio] (newMessage,
 * options, settings, cfEntry, cfDone, cfClear...) EXIGEM o texto como portão: num
 * idioma fora das regex daqui, esses papéis não são aprendidos e o modo fica
 * bloqueado (dizendo qual faltou), igual ao perfil fixo. As abas (direct_tab,
 * profile_tab) também exigem o rótulo INTEIRO e a barra de baixo: por forma só,
 * casavam com botões de pessoas. Só os papéis com apoio estrutural (composer por
 * região+rodapé, new_chat_to por região+texto) alcançam idiomas não listados. As regex cobrem subconjuntos
 * diferentes (ex.: SEGUIR só pt/en/es; CONCLUIR/NOVA_MSG sem italiano): ampliar um
 * idioma é acrescentar a palavra à regex do papel, com cuidado de falso-positivo.
 */
object SignatureLibrary {

    // --- regex multi-idioma; casam por PEDAÇO (containsMatchIn), sem lista fechada ---
    private val MSG = Regex("mensagem|message|mensaje|messaggi|nachricht", RegexOption.IGNORE_CASE)
    private val ENVIAR = Regex("enviar|send|invia|senden", RegexOption.IGNORE_CASE)
    private val BUSCA = Regex("pesquisar|search|buscar|busca|suchen|cerca", RegexOption.IGNORE_CASE)
    private val NOVA_MSG = Regex("nova mensagem|new message|nuevo mensaje|mensaje nuevo|neue nachricht", RegexOption.IGNORE_CASE)
    // O rotulo INTEIRO: por pedaco, o "..." de cada post e linha, "Mais opções para <pessoa>", virava o menu Opcoes.
    private val OPCOES = Regex("^(opções|opcoes|options|opciones|opzioni|optionen)$", RegexOption.IGNORE_CASE)
    private val CONFIG = Regex("configuraç|configuraci|settings|atividade|activity|ajustes|impostazioni|einstellungen", RegexOption.IGNORE_CASE)
    private val AMIGOS = Regex("amigos próximos|amigos proximos|close friends|amigos cercanos|mejores amigos|migliori amici|enge freunde", RegexOption.IGNORE_CASE)
    private val CONCLUIR = Regex("concluir|conclu|done|listo|hecho|fertig|terminar", RegexOption.IGNORE_CASE)
    private val LIMPAR = Regex("limpar tudo|clear all|borrar todo|limpiar todo|cancella tutto|löschen", RegexOption.IGNORE_CASE)
    // Abas da barra de baixo: o rotulo INTEIRO. Por pedaco, o botao "Enviar mensagem para <pessoa>" e a
    // "Foto do perfil" de cada linha de seguidor viravam as abas Direct/Perfil e o app tocava numa pessoa.
    private val TAB_MSG = Regex("^(mensagem|mensagens|messages?|mensaje|mensajes|direct|messaggi|nachrichten)$", RegexOption.IGNORE_CASE)
    private val TAB_PERFIL = Regex("^(perfil|profile|profil|profilo)$", RegexOption.IGNORE_CASE)
    // "Para:"/"To:"/"A:"/"An:"/"für" ANCORADOS no INICIO do rotulo: o campo da Nova
    // mensagem comeca por eles. Ancorar em ^ (nao so \b) impede que uma frase qualquer
    // do topo com "para"/"to" no meio ("Buscar pessoas para adicionar") vire o campo:
    // so a regiao (2) sem o texto casado fica abaixo do minimo (3). "Categoria:",
    // "Conta:", "Data:" tambem nao casam (nao comecam por para/to/a:).
    private val PARA = Regex("^(para\\b|to\\b|a:|an:|für\\b)", RegexOption.IGNORE_CASE)
    private val SEGUIR = Regex("sugest|suggest|seguir|follow|sigue", RegexOption.IGNORE_CASE)
    // O numero, abreviado ou nao, e a palavra: "388seguidores", "1.234 seguidores", "12,3 mil seguidores",
    // "1,2 mi seguidores", "12.3K followers" (espaco comum ou sem quebra). Sem "mil"/"K", 10 mil+ nao casava.
    private const val NUMERO = "\\d[\\d\\s\\u00A0\\u202F.,]*(?:mil|mi|mln|mio|tsd|k|m|b)?\\.?[\\s\\u00A0\\u202F]*"
    private val SEGUIDORES = Regex(NUMERO + "(seguidor|follower)", RegexOption.IGNORE_CASE)
    private val ABA_DA_LISTA = Regex(NUMERO + "(seguidor|follower|seguindo|following|seguidos|siguiendo|a seguir)", RegexOption.IGNORE_CASE)

    // --- vizinhança: predicados sobre um ancestral/descendente ---
    /** Um ancestral que rola: dentro de uma lista. */
    private val emLista: (UiNode) -> Boolean = { it.isScrollable }
    /** Um descendente cujo texto INTEIRO parece um @ (a linha tem um usuário). */
    private val temArroba: (UiNode) -> Boolean = { n ->
        listOfNotNull(n.text, n.contentDescription).any { RoleMatcher.ARROBA.matches(it.trim()) && " " !in it.trim() }
    }

    /**
     * A barra de abas de baixo (Pagina inicial, Reels, Mensagem, Pesquisar, Perfil): 4 ou mais filhos
     * clicaveis lado a lado, na mesma faixa. A linha de um seguidor (foto, "Mensagem", "Ignorar") nao e:
     * sao 3 e em alturas diferentes. Sem medida (bounds) nao ha como saber: nao e.
     */
    val barraDeAbas: (UiNode) -> Boolean = { n ->
        val abas = n.children.filter { it.isClickable }.mapNotNull { it.bounds }
        abas.size >= 4 && abas.all { kotlin.math.abs(it.centroY - abas[0].centroY) <= abas[0].altura / 4 }
    }

    /** Papeis que so existem na barra de abas de baixo (nunca um botao de pessoa com o mesmo rotulo). */
    val ABAS: Set<String> = setOf("direct_tab", "profile_tab")

    // --- abas / títulos da atividade principal ---
    val directTab = RoleSignature(
        "direct_tab", "aba Mensagem (Direct)", clicavel = true, texto = TAB_MSG, textoObrigatorio = true,
        dentroDe = barraDeAbas, idConhecido = "direct_tab", regiao = Regiao.RODAPE, minimo = 3,
    )
    val profileTab = RoleSignature(
        "profile_tab", "aba Perfil", clicavel = true, texto = TAB_PERFIL, textoObrigatorio = true,
        dentroDe = barraDeAbas, idConhecido = "profile_tab", regiao = Regiao.RODAPE, minimo = 3,
    )
    // Titulos de CONTA: o @ inteiro na barra de titulo, e mais nada. So a conferencia os procura pela forma, numa
    // tela ja provada (DmFlow/CloseFriendsFlow); a operacao le a conta so pelo id (SelectorProfile.CONTA_KEYS).
    // Com a regiao TOPO, "15posts" (o numero do perfil, logo abaixo da barra e antes dela na arvore) empatava com o
    // titulo e virava "a conta"; na caixa, sem o @, "Selecionar varias mensagens" bastava (clicavel + topo).
    val inboxTitle = RoleSignature(
        "inbox_title", "@ da conta na caixa de entrada", clicavel = true, texto = RoleMatcher.ARROBA, textoObrigatorio = true,
        idConhecido = "igds_action_bar_title", regiao = Regiao.CABECALHO, minimo = 5,
    )
    val profileTitle = RoleSignature(
        "profile_title", "@ da conta no perfil", texto = RoleMatcher.ARROBA, textoObrigatorio = true,
        idConhecido = "action_bar_title", regiao = Regiao.CABECALHO, minimo = 4,
    )

    // --- Nova mensagem / caixa de entrada ---
    val newMessage = RoleSignature(
        "new_message", "\"Nova mensagem\"", clicavel = true, texto = NOVA_MSG, textoObrigatorio = true, minimo = 3,
    )
    val newChatTo = RoleSignature(
        // O rotulo "Para:"/"To:" fica no topo da Nova mensagem (INSTAGRAM-APP-REAL.md 1.b).
        // A regiao TOPO e bonus/portao: com ela o papel aprende pela forma mesmo com id
        // diferente do IG_448 (texto 2 + regiao 2 = 4 >= 3); o id continua bonus.
        "new_chat_to", "campo \"Para:\" da Nova mensagem", texto = PARA, regiao = Regiao.TOPO,
        idConhecido = "direct_new_chat_to_field", minimo = 3,
    )
    // As listas: rola e empilha as linhas. So "rolavel" (1 ponto) nunca chegava ao minimo sem o id: com o id
    // renomeado, a conferencia liberava o modo e a operacao nao achava a lista (Conversas do Direct nao abria).
    val inboxList = RoleSignature(
        "inbox_list", "lista de conversas", rolavel = true, listaVertical = true,
        idConhecido = "inbox_refreshable_thread_list_recyclerview", minimo = 2,
    )

    // --- conversa (resolvidos ao vivo na operação; não abrimos conversa na conferência) ---
    val composer = RoleSignature(
        // minimo = 3: editavel+clicavel (os portoes, +1 cada) NAO bastam sozinhos, como
        // em send/cfRow. Exige um sinal a mais (texto MSG, rodape medido, foco ou id),
        // senao um 2o EditText clicavel (busca, comentario) viraria o campo de mensagem.
        // O foco: com o teclado aberto o campo sobe para o meio da tela (t06e, y 1236-1377 de 2408) e, digitado,
        // perde a dica "Mensagem...": sem ele, o campo sumia no meio do envio.
        "composer", "campo de mensagem", editavel = true, clicavel = true, texto = MSG, focado = true,
        regiao = Regiao.RODAPE, idConhecido = "row_thread_composer_edittext", minimo = 3,
    )
    val send = RoleSignature(
        // Ao lado do campo (mesma faixa de altura), nunca o proprio campo e nunca dentro de uma lista (as bolhas
        // com floating_send_container/send_label "Enviar" moram na lista de mensagens).
        "send", "botão Enviar", clicavel = true, texto = ENVIAR, aoLadoDoCampo = true, fora = emLista,
        regiao = Regiao.RODAPE, idConhecido = "row_thread_composer_send_button_container", exigeTextoOuId = true, minimo = 3,
    )
    val messageList = RoleSignature(
        "message_list", "lista de mensagens", rolavel = true, listaVertical = true, idConhecido = "message_list", minimo = 2,
    )

    // --- Amigos Próximos ---
    val options = RoleSignature(
        // No topo do perfil (a barra de titulo), nunca no meio de um post: clicavel 1 + texto 2 + topo 2.
        "options", "menu Opções", clicavel = true, texto = OPCOES, textoObrigatorio = true, regiao = Regiao.TOPO, minimo = 5,
    )
    val settings = RoleSignature(
        "settings", "Configurações e atividade", texto = CONFIG, textoObrigatorio = true, minimo = 2,
    )
    val cfEntry = RoleSignature(
        "cf_entry", "entrada Amigos Próximos", texto = AMIGOS, textoObrigatorio = true, minimo = 2,
    )
    val cfSearch = RoleSignature(
        "cf_search", "busca de Amigos Próximos", editavel = true, texto = BUSCA, regiao = Regiao.TOPO,
        idConhecido = "search_edit_text", minimo = 2,
    )
    val cfDone = RoleSignature(
        "cf_done", "botão Concluir", clicavel = true, texto = CONCLUIR, textoObrigatorio = true,
        idConhecido = "done_button", regiao = Regiao.RODAPE, minimo = 3,
    )
    val cfClear = RoleSignature(
        "cf_clear", "\"Limpar tudo\" (NUNCA tocar)", clicavel = true, texto = LIMPAR, textoObrigatorio = true,
        idConhecido = "row_header_action", minimo = 3,
    )
    val cfRow = RoleSignature(
        "cf_row", "linha de Amigos Próximos", clicavel = true, dentroDe = emLista, contem = temArroba,
        idConhecido = "row_user_container", minimo = 3,
    )
    val cfUsername = RoleSignature(
        "cf_username", "@ na linha de Amigos Próximos", texto = RoleMatcher.ARROBA, textoObrigatorio = true,
        dentroDe = emLista, idConhecido = "row_user_username", minimo = 3,
    )

    // --- Seguidores ---
    // Papeis do caminho Perfil > Seguidores (Modo DM, Origem "Seus seguidores").
    // "Conferir o Instagram" navega so o Direct/Amigos Proximos; este caminho e
    // resolvido AO VIVO pela assinatura na operacao (nao fica no perfil aprendido).
    // As LINHAS e o @ nao: so pelo id (ver SelectorProfile.SO_POR_ID).
    val profileFollowers = RoleSignature(
        // Perfil > "388 seguidores" (desc "388seguidores"): leva a lista de Seguidores.
        "profile_followers", "número de seguidores (Perfil)", clicavel = true,
        texto = SEGUIDORES, textoObrigatorio = true,
        idConhecido = "profile_header_followers_stacked_familiar", minimo = 2,
    )
    val followTab = RoleSignature(
        // As abas da lista, "388 seguidores" e "413 seguindo": a MARCADA decide (Listas.abaSeguidores /
        // abaSeguindoSelecionada). O numero do cabecalho do perfil ("388seguidores") tambem casa, mas nunca
        // esta marcado.
        "follow_tab", "abas Seguidores/Seguindo", clicavel = true, texto = ABA_DA_LISTA, textoObrigatorio = true,
        idConhecido = "title", minimo = 2,
    )
    // follow_row / follow_username / follow_end: sem assinatura, so pelo id (SelectorProfile.SO_POR_ID).
    // As linhas de "Sugestoes para voce" (estranhos) tem a forma das de seguidor, a aba "Sinalizadas"
    // parece um @, e o fim achado pela forma, com as linhas ilegiveis, daria "Fim da lista" sem ninguem.

    val todas: List<RoleSignature> = listOf(
        directTab, profileTab, inboxTitle, profileTitle, newMessage, newChatTo, inboxList,
        composer, send, messageList, options, settings, cfEntry, cfSearch, cfDone, cfClear,
        cfRow, cfUsername, profileFollowers, followTab,
    )

    private val porChave: Map<String, RoleSignature> = todas.associateBy { it.key }

    fun para(key: String): RoleSignature? = porChave[key]

    /** Papéis que "Conferir o Instagram" tenta aprender por modo (só telas alcançáveis sem tocar em ninguém). */
    val DM: List<String> = listOf("direct_tab", "inbox_title", "profile_title", "new_message", "new_chat_to", "inbox_list")
    val CF: List<String> = listOf("profile_tab", "profile_title", "options", "settings", "cf_entry", "cf_search", "cf_done", "cf_clear", "cf_row", "cf_username")

    /**
     * Papéis cujo rótulo é NOME/@ de terceiro (dado pessoal), não vocabulário fixo
     * do app. Aprende-se o id (não é pessoal), NUNCA o texto: o perfil aprendido no
     * aparelho não grava @ de ninguém. Ver Aprendiz.perfil e o requisito "sem dados
     * de terceiros". Rótulos de botão (Enviar/Concluir/Opções) ficam de fora daqui.
     */
    val ROTULO_PESSOAL: Set<String> = setOf("cf_username", "cf_row", "follow_username", "follow_row")

    /**
     * Papeis cujo rotulo NUNCA e gravado no perfil aprendido: os pessoais, o "Para:" (na caixa a assinatura casava a
     * nota de um amigo) e os titulos de conta (o @ vale so pelo id). Dos outros, so o rotulo de vocabulario
     * ([rotuloDeVocabulario]).
     */
    val SEM_ROTULO: Set<String> = ROTULO_PESSOAL + setOf("new_chat_to", "inbox_title", "profile_title")

    /**
     * O rotulo e o do controle, nao de uma pessoa: casa a regex do papel, sem @ e com no maximo 3 palavras
     * ("Amigos Próximos, 162", "Configurações e atividade"). "Mais opções para <pessoa>" e "Enviar mensagem para
     * <Nome>" casavam o papel por pedaco e iam para o aparelho (e depois casavam por igualdade em porRotulo).
     */
    fun rotuloDeVocabulario(key: String, rotulo: String): Boolean {
        val re = para(key)?.texto ?: return false
        return re.containsMatchIn(rotulo) && '@' !in rotulo && rotulo.trim().split(Regex("\\s+")).size <= 3
    }

    fun doModo(chaves: List<String>): List<RoleSignature> = chaves.mapNotNull(::para)
}
