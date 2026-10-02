package com.listalocal.core.ig

import com.listalocal.core.followers.FollowerImport
import com.listalocal.core.selectors.RoleMatcher
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.selectors.SignatureLibrary
import com.listalocal.core.tree.UiNode

/**
 * Provas lidas na arvore de acessibilidade do Instagram. Funcoes puras: o
 * servico passa a copia da arvore (NodeAdapter) e os testes passam FakeNode.
 * A MESMA funcao decide nos dois lugares.
 *
 * Regra do Lista Local mantida: texto EXATO para decidir quem e onde tocar;
 * "contem" so para reconhecer avisos (restricao, nao enviada, indisponivel), e
 * so fora das bolhas: dentro da lista de mensagens o texto e de gente.
 */
object Evidence {

    enum class Conversa {
        /** O cabecalho (ou o cartao do topo) mostra o @ exato da pessoa. */
        PROVADA,
        /** Ha conversa aberta, mas o @ nao aparece (ou e outro). */
        SEM_PROVA,
        /** A conta nao recebe mensagens ou foi desativada. */
        INDISPONIVEL,
        /**
         * Nao ha conversa na tela. Com @ que nao existe, o link ig.me nao faz
         * nada e o Instagram fica onde estava, sem aviso (rodada 1).
         */
        NAO_ABERTA,
    }

    fun norm(texto: String?): String = texto.orEmpty().replace("\r\n", "\n").trim()

    // Resolve o papel na ordem: id aprendido -> id fixo -> assinatura ao vivo (SelectorProfile.nos).
    private fun id(root: UiNode, prof: SelectorProfile, key: String): List<UiNode> =
        prof.nos(root, key)

    /**
     * Um rotulo [key] do Instagram ("contem") FORA das bolhas: cabecalho, faixa de aviso, barra do campo (a bandeja
     * de figurinhas), dialogo por cima. A lista de mensagens fica de fora (o texto de uma bolha, de qualquer lado,
     * nunca e aviso: "o site caiu, tente novamente mais tarde"), e o campo tambem (o que esta nele foi digitado).
     */
    private fun foraDasBolhas(root: UiNode, prof: SelectorProfile, key: String): Boolean {
        val rotulos = prof.descriptionsFor(key)
        val listas = id(root, prof, "message_list")
        fun fora(n: UiNode): Sequence<UiNode> = sequence {
            if (listas.any { it === n }) return@sequence
            if (!RoleMatcher.ehEditText(n)) yield(n)
            n.children.forEach { yieldAll(fora(it)) }
        }
        return fora(root).any { n -> n.labels().any { l -> rotulos.any { l.contains(it, ignoreCase = true) } } }
    }

    /**
     * O rotulo e SO o aviso: tirados o nosso texto (a bolha nossa pode juntar o marcador ao texto) e os avisos,
     * nao sobra letra ("Nao enviada. Toque para tentar novamente", "Oi! · Nao enviada"). "Sua encomenda foi nao
     * enviada ainda?" sobra letra: e uma bolha, nao o marcador.
     */
    private fun soAviso(rotulo: String, avisos: List<String>, nossos: Collection<String>): Boolean {
        var resto = norm(rotulo)
        nossos.map(::norm).filter(String::isNotEmpty).forEach { resto = resto.replace(it, "", ignoreCase = true) }
        if (avisos.none { resto.contains(it, ignoreCase = true) }) return false
        avisos.sortedByDescending { it.length }.forEach { resto = resto.replace(it, "", ignoreCase = true) }
        return resto.none(Char::isLetter)
    }

    /**
     * Primeiro no com o rotulo EXATO da chave (texto ou descricao), ou com a
     * descricao "Rotulo, N" (itens de Configuracoes trazem a contagem).
     */
    fun porRotulo(root: UiNode, prof: SelectorProfile, key: String): UiNode? {
        val rotulos = prof.descriptionsFor(key)
        // As abas so na barra de baixo: "Mensagem" e tambem o texto do botao de cada linha de seguidor.
        val nos = if (key in SignatureLibrary.ABAS) {
            root.walk().filter(SignatureLibrary.barraDeAbas).flatMap { it.children.asSequence() }
        } else {
            root.walk()
        }
        return nos.firstOrNull { n ->
            val t = norm(n.text)
            val d = norm(n.contentDescription)
            rotulos.any { r -> t == r || d == r || d.startsWith("$r,") }
        } ?: prof.noPorAssinatura(root, key) // idioma nao previsto: regex por forma
    }

    /**
     * O @ da conta aberta: titulo da caixa de entrada ou do perfil proprio. So o titulo VISIVEL: a arvore
     * do servico guarda telas de tras da pilha (o titulo de outro perfil aberto antes, fora da tela).
     */
    fun conta(root: UiNode, prof: SelectorProfile, key: String = "inbox_title"): String? =
        contaDe(id(root, prof, key).filter { it.isVisibleToUser }.mapNotNull { it.text ?: it.contentDescription })

    /**
     * O @ da conta pela FORMA do titulo, quando o id dele nao esta na tela (outra versao do Instagram). So para a
     * CONFERENCIA, numa tela que ela ja provou (a caixa de entrada; o perfil proprio com a aba Perfil marcada), e
     * so um titulo com id: e esse id que a conferencia aprende, e a operacao le a conta so por ele ([conta]).
     */
    fun contaPelaForma(root: UiNode, key: String = "inbox_title"): String? {
        val no = SignatureLibrary.para(key)?.let { RoleMatcher.achar(root, it) }?.takeIf { it.viewIdResourceName != null } ?: return null
        return contaDe(listOfNotNull(no.text ?: no.contentDescription))
    }

    /** O @ da conta nos rotulos do titulo (lidos por busca direta ou numa arvore). */
    fun contaDe(rotulos: List<String>): String? =
        rotulos.firstNotNullOfOrNull { norm(it).removePrefix("@").lowercase().takeIf(String::isNotEmpty) }

    private fun ehArroba(rotulo: String, alvo: String) = norm(rotulo).removePrefix("@").lowercase() == alvo

    /** Partes de um rotulo: "nome, @", "@ · Online agora", uma por linha. Nunca por espaco. */
    private fun partes(rotulo: String): List<String> = rotulo.split(',', '\n', '·', '•').map(::norm)

    /**
     * Ha conversa aberta nesta arvore: o cabecalho ou o campo da conversa. E o
     * que escolhe a janela a ler quando o Instagram tem mais de uma (NodeOps).
     */
    fun temConversa(root: UiNode, prof: SelectorProfile): Boolean = prof.conversaIds.any(root::exists)

    /**
     * Os nos do cabecalho: o bloco direct_thread_header ou, sem ele, o bloco clicavel do nome
     * (header_title_subtitle_container, onde a descricao "nome, @" mora), o titulo e o subtitulo. Na arvore do
     * servico (sem flagIncludeNotImportantViews) o direct_thread_header, um layout sem clique nem descricao, some.
     */
    private fun cabecalho(root: UiNode, prof: SelectorProfile): List<UiNode> =
        id(root, prof, "thread_header").firstOrNull()?.walk()?.toList()
            ?: (id(root, prof, "header_container").flatMap { it.walk().toList() } +
                id(root, prof, "header_title") + id(root, prof, "header_subtitle")).distinct()

    /**
     * O @ esta na conversa aberta? Compara o texto INTEIRO de cada no (ou cada
     * parte separada por virgula, ponto medio ou quebra de linha), sem quebrar
     * por espaco: o nome "Joao Silva" nunca prova o @ "joao".
     *  - cabecalho: o subtitulo mostra o @ para pessoas (alternando com
     *    "Online agora"); o titulo mostra o @ na "Conversa comercial";
     *  - cartao do topo (so no comeco do historico): o bloco com "Ver perfil"
     *    que tem o @ exato.
     * Sem o @ em nenhum dos dois, o DmFlow ainda aceita o nome ([nomeProva]).
     */
    fun conversa(root: UiNode, prof: SelectorProfile, username: String): Conversa {
        if (!temConversa(root, prof)) return Conversa.NAO_ABERTA
        if (indisponivel(root, prof)) return Conversa.INDISPONIVEL
        val alvo = username.lowercase()
        val noCabecalho = cabecalho(root, prof).any { n -> n.labels().flatMap(::partes).any { ehArroba(it, alvo) } }
        if (noCabecalho) return Conversa.PROVADA
        val cartoes = prof.descriptionsFor("profile_card")
        val noCartao = mensagens(root, prof).any { bloco ->
            bloco.children.any { c -> c.labels().any { norm(it) in cartoes } } &&
                bloco.walk().any { n -> n.labels().flatMap(::partes).any { ehArroba(it, alvo) } }
        }
        return if (noCartao) Conversa.PROVADA else Conversa.SEM_PROVA
    }

    /**
     * Prova pela linha do nome, como no envio real por adb (TESTE-ENVIO-REAL.md):
     * o subtitulo troca o @ por "Online agora" e o cartao do topo sai da tela em
     * conversa longa. Vale so quando o titulo e o nome da pessoa (o mesmo da
     * lista de seguidores; truncado com "..." vale o comeco, com 6 letras ou mais)
     * E o subtitulo nao mostra outra coisa que nao um estado conhecido. Um
     * subtitulo desconhecido (outro @, por exemplo) nega. So conta depois de a
     * conversa ter sido aberta pelo link do @, sem conversa na frente antes.
     */
    fun nomeProva(root: UiNode, prof: SelectorProfile, nome: String): Boolean {
        if (!temConversa(root, prof) || !mesmoNome(tituloDaConversa(root, prof), nome)) return false
        val estados = prof.descriptionsFor("header_status")
        // "online.shop" comeca com "Online" mas e um @: so vale o estado que nao parece @.
        fun ehEstado(l: String): Boolean {
            val pareceArroba = ARROBA.matches(l) && ".." !in l && estados.none { l.equals(it, ignoreCase = true) }
            return !pareceArroba && estados.any { l.startsWith(it, ignoreCase = true) }
        }
        return id(root, prof, "header_subtitle").all { n ->
            n.labels().map(::norm).all { l -> l.isEmpty() || ehEstado(l) }
        }
    }

    private val ARROBA = Regex("^@?[a-z0-9._]{1,30}$", RegexOption.IGNORE_CASE)

    /**
     * O nome do cabecalho e o esperado: igual, ou o titulo truncado com "..."
     * (6 letras ou mais) e o comeco do esperado.
     */
    fun mesmoNome(titulo: String?, esperado: String): Boolean {
        val t = norm(titulo)
        val e = norm(esperado)
        if (t.isEmpty() || e.length < 2) return false
        val truncado = t.removeSuffix("...").removeSuffix("…").trimEnd()
        return t.equals(e, ignoreCase = true) ||
            (truncado != t && truncado.length >= 6 && e.startsWith(truncado, ignoreCase = true))
    }

    /** O @ que o cabecalho mostra e se e "Conversa comercial" (caixa de entrada: ainda nao se sabe de quem e). */
    data class Cabecalho(val username: String?, val comercial: Boolean)

    /**
     * Le o @ da conversa aberta pela linha da caixa de entrada. So o texto
     * INTEIRO de um no conta (nunca uma parte: "ana, bia" de um grupo nao da
     * "ana"):
     *  - pessoa: o subtitulo e o @;
     *  - "Conversa comercial" no subtitulo: o titulo e o @;
     *  - cabecalho numa descricao so: "nome, @", exatamente duas partes;
     *  - sem nenhum desses (o subtitulo alterna o @ com "Online agora", ou o IgView nao expoe o texto): o
     *    cartao do topo, o bloco com "Ver perfil" e o NOME do cabecalho, e nele o no cujo texto inteiro e um @.
     * Um estado ("Online agora", "Visto", "Digitando...") nao e @. Sem @ = null.
     */
    fun arrobaDoCabecalho(root: UiNode, prof: SelectorProfile): Cabecalho {
        if (!temConversa(root, prof)) return Cabecalho(null, false)
        val arroba = arrobaSemEstado(prof)
        val sub = id(root, prof, "header_subtitle").flatMap { it.labels() }
        val comercial = sub.any { l -> prof.descriptionsFor("business_chat").any { norm(l).equals(it, ignoreCase = true) } }
        val direto = if (comercial) {
            id(root, prof, "header_title").flatMap { it.labels() }.firstNotNullOfOrNull(arroba)
        } else {
            sub.firstNotNullOfOrNull(arroba)
        }
        val junto = direto ?: cabecalho(root, prof).filter { it.isClickable }.firstNotNullOfOrNull { n ->
            norm(n.contentDescription).split(", ").takeIf { it.size == 2 }?.let { arroba(it[1]) }
        }
        return Cabecalho(junto ?: arrobaDoCartao(root, prof, arroba), comercial)
    }

    /** Um rotulo que e um @ inteiro, e nao um estado do cabecalho ("Online agora") nem nome com ponto no fim. */
    private fun arrobaSemEstado(prof: SelectorProfile): (String) -> String? {
        val estados = prof.descriptionsFor("header_status")
        return { l ->
            val t = norm(l)
            if (".." in t || t.endsWith(".") || estados.any { t.equals(it, true) || t.startsWith("$it ", true) }) null
            else FollowerImport.username(t)
        }
    }

    /**
     * O @ do cartao do topo (so no comeco do historico): o bloco com "Ver perfil" que tambem mostra o NOME do
     * cabecalho (um perfil compartilhado numa mensagem tem outro nome), e nele um unico no, fora o nome, cujo
     * texto inteiro e um @. Sem cartao, ou ambiguo: null.
     */
    private fun arrobaDoCartao(root: UiNode, prof: SelectorProfile, arroba: (String) -> String?): String? {
        val titulo = tituloDaConversa(root, prof) ?: return null
        val cartoes = prof.descriptionsFor("profile_card")
        return mensagens(root, prof).filter { bloco ->
            bloco.children.any { c -> c.labels().any { norm(it) in cartoes } } && bloco.walk().any { mesmoNome(titulo, it.text.orEmpty()) }
        }.firstNotNullOfOrNull { bloco ->
            bloco.walk().mapNotNull { n -> norm(n.text).takeIf { it.isNotEmpty() && !mesmoNome(titulo, it) } }
                .mapNotNull(arroba).distinct().toList().singleOrNull()
        }
    }

    /**
     * O subtitulo do cabecalho mostra um texto LEGIVEL que nao e @ nem estado ("Ana, Bia e mais 2"): a conversa
     * foi lida e nao e 1:1 (grupo). Subtitulo sem texto, ou so um estado ("Online agora"): nao da para saber.
     */
    fun subtituloDeGrupo(root: UiNode, prof: SelectorProfile): Boolean {
        val estados = prof.descriptionsFor("header_status")
        val arroba = arrobaSemEstado(prof)
        return id(root, prof, "header_subtitle").flatMap { it.labels() }.map(::norm).any { l ->
            l.isNotEmpty() && estados.none { l.startsWith(it, ignoreCase = true) } && arroba(l) == null
        }
    }

    /** Nome no cabecalho da conversa (truncado com "..."), para ver que a mesma conversa segue na frente. */
    fun tituloDaConversa(root: UiNode, prof: SelectorProfile): String? =
        id(root, prof, "header_title").firstNotNullOfOrNull { n -> norm(n.text).takeIf { it.isNotEmpty() } }

    /** O cartao do topo diz que a pessoa nao segue a conta: nao e seguidor. */
    fun naoSegue(root: UiNode, prof: SelectorProfile): Boolean {
        val rotulos = prof.descriptionsFor("not_following")
        return mensagens(root, prof).any { n -> n.labels().any { l -> rotulos.any { l.contains(it, ignoreCase = true) } } }
    }

    /** Mensagens temporarias ligadas (dica "Mensagem temporaria..."): a mensagem some depois de vista. */
    fun temporaria(root: UiNode, prof: SelectorProfile): Boolean {
        val t = norm(campo(root, prof)?.text)
        return prof.descriptionsFor("temporary").any { t.startsWith(it, ignoreCase = true) }
    }

    /** Campo de mensagem da conversa. */
    fun campo(root: UiNode, prof: SelectorProfile): UiNode? =
        id(root, prof, "composer").firstOrNull()

    /**
     * Texto do campo. Vazio quando nao ha Enviar habilitado: o botao so aparece
     * com texto (em conversa nova vem desabilitado), qualquer que seja a dica
     * ("Mensagem...", "Mensagem temporária..."), que a acessibilidade devolve
     * no lugar do texto quando o campo esta vazio.
     */
    fun textoDoCampo(root: UiNode, prof: SelectorProfile): String {
        if (botaoEnviar(root, prof) == null) return ""
        val t = norm(campo(root, prof)?.text)
        val dica = t in prof.descriptionsFor("composer_hint") ||
            (prof.descriptionsFor("temporary").any { t.startsWith(it) } && (t.endsWith("...") || t.endsWith("…")))
        return if (dica) "" else t
    }

    /**
     * Botao Enviar: pelo id; sem ele, pelo rotulo EXATO dentro da barra do campo
     * (fora dela, "Enviar" pode ser outra coisa, como compartilhar).
     */
    fun botaoEnviar(root: UiNode, prof: SelectorProfile): UiNode? {
        val botao = id(root, prof, "send").firstOrNull() ?: run {
            val barra = id(root, prof, "composer_bar").firstOrNull() ?: return null
            val rotulos = prof.descriptionsFor("send")
            barra.walk().firstOrNull { n -> !RoleMatcher.ehEditText(n) && n.labels().any { norm(it) in rotulos } }
        }
        // Em conversa nova o Enviar ja aparece, desabilitado, antes do texto.
        return botao?.takeIf { it.isEnabled }
    }

    /** A lista de mensagens esta na leitura: sem ela, pedido de parar e envio falho nao sao conferidos. */
    fun temMensagens(root: UiNode, prof: SelectorProfile): Boolean = id(root, prof, "message_list").isNotEmpty()

    private fun mensagens(root: UiNode, prof: SelectorProfile): Sequence<UiNode> =
        id(root, prof, "message_list").asSequence().flatMap { it.walk() }

    /**
     * Quantos nos da conversa tem este texto (antes e depois do toque): o texto
     * exato ou, a partir de 12 letras, o texto inteiro dentro do rotulo (a bolha
     * em Compose pode juntar a hora ou "Voce:" ao texto; CALIBRAR na rodada 2).
     */
    fun contarMensagem(root: UiNode, prof: SelectorProfile, texto: String): Int {
        val alvo = norm(texto)
        return mensagens(root, prof).count { n ->
            n.labels().any { l -> norm(l) == alvo || (alvo.length >= 12 && norm(l).contains(alvo)) }
        }
    }

    /**
     * Marcador de mensagem nao enviada: fora das bolhas, ou, dentro da lista, um no que e so o marcador
     * ([soAviso]; [nossos] = os textos da operacao, que a bolha nossa pode juntar ao marcador).
     */
    fun falhaDeEnvio(root: UiNode, prof: SelectorProfile, nossos: Collection<String> = emptyList()): Boolean {
        val avisos = prof.descriptionsFor("not_sent")
        return foraDasBolhas(root, prof, "not_sent") || mensagens(root, prof).any { n -> n.labels().any { soAviso(it, avisos, nossos) } }
    }

    /**
     * Conversa sem mensagens: nao e uma conversa que ja existe. Na tela real (t07h) a bandeja "Diga 'ola' enviando
     * uma figurinha" (tocar numa figurinha ENVIA) fica na barra do campo, FORA da lista de mensagens, e o Enviar ja
     * aparece desabilitado. Qualquer um: a bandeja pelo id, o rotulo fora das bolhas, ou o Enviar desabilitado.
     */
    fun conversaNova(root: UiNode, prof: SelectorProfile): Boolean =
        prof.idFor("new_thread_tray")?.let(root::exists) == true ||
            foraDasBolhas(root, prof, "new_thread") ||
            id(root, prof, "send").any { !it.isEnabled }

    /** A conta nao recebe: o aviso no lugar do campo ou o titulo "Usuario do Instagram", nunca uma bolha ou um item compartilhado. */
    fun indisponivel(root: UiNode, prof: SelectorProfile): Boolean = foraDasBolhas(root, prof, "unavailable")

    /** Aviso de restricao ou limite: dialogo ou faixa do Instagram, nunca o texto de uma bolha (nem da nossa). */
    fun restricao(root: UiNode, prof: SelectorProfile): Boolean = foraDasBolhas(root, prof, "restriction")

    /**
     * Alguem pediu para parar nesta conversa? A direcao das bolhas (nossa ou
     * da pessoa) ainda PRECISA VERIFICAR NO INSTAGRAM REAL, entao lemos todas,
     * menos o nosso proprio texto e o cartao do perfil (@ e nome no topo): um
     * "pare" de qualquer lado barra o envio.
     */
    fun pediuParaParar(
        root: UiNode, prof: SelectorProfile, nossoTexto: String, username: String,
        nossos: Collection<String> = emptyList(),
    ): Boolean {
        val ignorar = setOf(norm(nossoTexto).lowercase(), username.lowercase()) + nossos.map { norm(it).lowercase() } +
            id(root, prof, "header_title").map { norm(it.text).lowercase() }
        return mensagens(root, prof).any { n ->
            n.labels().any { l -> norm(l).lowercase() !in ignorar && OptOut.pediuParaParar(l) }
        }
    }

    /** Uma linha de Amigos Proximos: o @ e se esta marcada. */
    data class Linha(val username: String, val marcada: Boolean, val no: UiNode)

    /**
     * Linhas visiveis de Amigos Proximos. A marca vem da caixa de selecao
     * (IgdsCheckBox, sem id) ou do proprio container. So vale numa busca
     * filtrada e parada (CloseFriendsFlow.buscar): na lista inicial as caixas
     * dos membros vem sem a marca, embora marcadas na tela (t02a real).
     */
    fun linhasAmigos(root: UiNode, prof: SelectorProfile): List<Linha> {
        val rows = id(root, prof, "cf_row")
        if (rows.isEmpty()) return emptyList()
        // O @ da linha pelo papel (id aprendido -> id fixo -> assinatura ao vivo): um id
        // fixo que NAO existe neste aparelho cai para a assinatura em vez de zerar TODAS as
        // linhas (que viraria NAO_ENCONTRADO calado). O mesmo id vale em todas as linhas.
        val userId = id(root, prof, "cf_username")
            .firstNotNullOfOrNull { it.viewIdResourceName?.substringAfter(":id/") }
            ?: return emptyList()
        return rows.mapNotNull { row ->
            val nome = row.findByViewIdSuffix(userId).firstOrNull()?.text ?: return@mapNotNull null
            val marcada = row.walk().any { it.isChecked || it.isSelected }
            Linha(norm(nome).removePrefix("@").lowercase(), marcada, row)
        }
    }
}
