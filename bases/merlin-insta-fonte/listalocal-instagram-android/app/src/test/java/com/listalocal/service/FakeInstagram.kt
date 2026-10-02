package com.listalocal.service

import com.listalocal.core.ig.Evidence
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.UiGroup
import com.listalocal.core.tree.UiNode
import com.listalocal.core.tree.abasDeBaixo
import com.listalocal.core.tree.amigosFixture
import com.listalocal.core.tree.ig
import com.listalocal.core.tree.LINHA_ALTURA
import com.listalocal.core.tree.LINHA_TOPO
import com.listalocal.core.tree.inboxFixture
import com.listalocal.core.tree.perfilFixture
import com.listalocal.core.tree.seguidoresFixture
import com.listalocal.core.tree.threadFixture

/**
 * Instagram falso para os fluxos: um modelo pequeno (conversas, Amigos
 * Proximos) desenhado com as arvores de IgFixtures. Tudo o que o fluxo faz
 * vai para [log], na ordem, para os testes conferirem a sequencia.
 *
 * As telas ficam em janelas, como no aparelho (INSTAGRAM-APP-REAL.md, 0.1):
 * a atividade principal (abas, caixa de entrada) numa, a ModalActivity
 * (conversa, Nova mensagem, Amigos Proximos) noutra por cima, com a principal
 * velha atras. [ler] escolhe com a MESMA regra do servico ([Janelas]).
 */
class FakeInstagram(
    /** A conta aberta no Instagram. O dono pode trocar (duas contas no mesmo aparelho). */
    var conta: String = "minhaconta",
) : Tela {

    val log = mutableListOf<String>()

    // ---- Direct ----
    class Conversa(
        val username: String,
        /** Nome no cabecalho (header_title). */
        var nome: String = "Pessoa Teste",
        /** O que o subtitulo do cabecalho mostra; null = @ nao exposto. */
        var cabecalho: String? = username,
        /** O @ no cartao do topo; null = cartao fora da tela. */
        var cartao: String? = username,
        val cartaoExtra: MutableList<String> = mutableListOf(),
        var dica: String = "Mensagem...",
        var enviarHabilitado: Boolean = true,
        val mensagens: MutableList<String> = mutableListOf(),
        val avisos: MutableList<String> = mutableListOf(),
        var campo: String? = null,
        /** Conversa em grupo: so abre pela caixa de entrada, nunca pelo link de um @. */
        var grupo: Boolean = false,
        /** O que a linha da caixa de entrada mostra depois do nome. */
        var estado: String = "Visto",
        var novaConversa: Boolean = false,
        /** O nome que a linha da caixa de entrada mostra (null = o do cabecalho). */
        var nomeNaLinha: String? = null,
        /** Tocar na linha nao abre nada que o app enxergue (acessibilidade sem a conversa). */
        var linhaNaoAbre: Boolean = false,
        /** A acessibilidade traz o cabecalho e o campo, mas nao a lista de mensagens. */
        var semLista: Boolean = false,
        /** O subtitulo mostra [cabecalho] e, este tanto depois de a conversa abrir, o @ (alterna com "Online agora"). */
        var arrobaApos: Long? = null,
        /** Mensagens acima da tela: so aparecem rolando a conversa para tras (uma tela). */
        val antigas: MutableList<String> = mutableListOf(),
    )

    enum class Envio { OK, NADA, NAO_ENVIADA, RESTRICAO, RESTRICAO_DIALOGO }

    /** Como o servico enxerga as janelas neste aparelho. */
    enum class Visao {
        /** Modal por cima da principal, as duas em service.windows. */
        NORMAL,
        /** Camadas trocadas: a principal (velha) por cima da Modal. */
        MODAL_ABAIXO,
        /** service.windows so com a principal; a Modal so pela fonte dos eventos. */
        MODAL_FORA_DA_LISTA,
        /** service.windows vazio: rootInActiveWindow (a principal velha) e os eventos. */
        SO_RESERVAS,
        /** Outro app por cima do Instagram. */
        OUTRO_APP_NA_FRENTE,
        /**
         * A Modal no topo de service.windows SEM raiz (o "null root node" do uiautomator neste aparelho,
         * INSTAGRAM-APP-REAL.md 0.1); a arvore dela so vem pela fonte do evento DELA.
         */
        MODAL_SEM_RAIZ,
        /** A Modal no topo sem raiz e sem evento legivel: o servico nao enxerga a conversa de jeito nenhum. */
        MODAL_ILEGIVEL,
    }

    var visao = Visao.NORMAL
    /** Dialogo do Instagram numa janela propria, por cima da conversa. */
    var dialogo: UiNode? = null

    /** Relogio falso: anda nas esperas e em cada leitura ([custoLeitura]). */
    var relogio = 0L
    /** Quanto cada leitura da arvore leva (0,8 s na rodada 1). */
    var custoLeitura = 0L
    /** A conversa aparece este tanto depois do link (ig.me: 1,5 a 2,7 s). */
    var atrasoAbrir = 0L
    private var abertaEm = 0L

    val conversas = mutableMapOf<String, Conversa>()
    var envio = Envio.OK
    var escreverFunciona = true
    /** O toque em Enviar e recusado (outro app por cima no instante do toque): nada acontece. */
    var recusarEnviar = false
    /** A conversa aberta esta rolada para tras (mostra as [Conversa.antigas]). */
    private var recuo = 0
    /** O campo corta o texto neste tamanho (limite do Instagram, por exemplo). */
    var campoCorta: Int? = null
    /** Depois do toque em Enviar, esta conversa passa para a frente (o dono mexendo no aparelho). */
    var aposEnviarAbre: String? = null
    /** Chamado em cada espera, com o tempo pedido (os testes da fila medem intervalo e pausa por aqui). */
    var aoEsperar: (Long) -> Unit = {}
    /** Lanca [Interrompido] na proxima espera depois que esta entrada aparecer no log. */
    var interromperApos: String? = null

    // ---- Medidas (DesempenhoTest) ----
    /** Leituras da arvore inteira ([ler]): cada uma e uma copia da arvore no aparelho. */
    var leituras = 0
    /** Esperas pedidas e o tempo somado delas. */
    var esperas = 0
    var esperadoMs = 0L
    /** O relogio em cada toque em Enviar. */
    val toquesEnviar = mutableListOf<Long>()
    /** [leituras] em cada toque em Enviar. */
    val leiturasNosToques = mutableListOf<Int>()
    /** A bolha aparece este tanto depois do toque em Enviar (o campo esvazia na hora). */
    var atrasoBolha = 0L
    private val bolhas = mutableListOf<Triple<Conversa, String, Long>>()

    /** O teclado abre com o toque no campo; o primeiro Voltar so o fecha (rodada 1). */
    var tecladoAberto = false
        private set

    /** Buscas por id ([existe]): no aparelho, uma busca direta, sem copiar a arvore. */
    var buscas = 0
    /** Cada mudanca da tela chega como evento este tanto depois (notificationTimeout = 100 ms). */
    var atrasoEvento = 100L
    /** Acessibilidade sem eventos: [esperarEvento] vira espera fixa, como antes. */
    var semEventos = false
    /** Quando chegam os eventos ainda nao entregues. */
    private val eventos = mutableListOf<Long>()

    private fun mudou(em: Long = relogio) { eventos += em + atrasoEvento }

    private var aberta: Conversa? = null
    private var tela = "inicio" // inicio | direct | conversa | perfil | seguidores | config | amigos
    /** Onde o Instagram estava quando a conversa abriu: Voltar leva para la (rodada 1, 1.a). */
    private var anterior = "inicio"

    // ---- Perfil > Seguidores ----
    /** A lista de seguidores (@ to nome) da conta inicial, na ordem do Instagram. Os testes mudam ela no meio. */
    val seguidores = mutableListOf<Pair<String, String>>()
    private val contaInicial = conta
    /** As listas de seguidores das OUTRAS contas (a troca de conta mostra a da conta nova). */
    val seguidoresDe = mutableMapOf<String, MutableList<Pair<String, String>>>()
    /** A lista aberta e a de outra pessoa (o dono a abriu na pausa): o titulo e as linhas sao dela. */
    var donoDaLista: String? = null
    private fun listaAberta(): List<Pair<String, String>> =
        (donoDaLista ?: conta).let { if (it == contaInicial) seguidores else seguidoresDe[it].orEmpty() }
    /** Linhas de "Sugestoes para voce" depois do ultimo seguidor (o fim). */
    var sugestoes = 3
    /** Primeira linha visivel; cada tela mostra [porTela] linhas e cada rolagem anda [passo]. */
    var topo = 0
    /** A rolagem da lista anda por este tempo (0 = na hora); no meio, a tela mostra metade do passo. */
    var animacaoRolagem = 0L
    private var rolagemDe = 0
    private var rolagemAte = 0L
    private fun topoVisivel() = if (relogio < rolagemAte) (rolagemDe + topo) / 2 else topo
    var porTela = 8
    var passo = 7
    var abaMarcada = "seguidores"
    /** A barra de abas da lista aparece este tanto depois das linhas. */
    var barraAtrasa = 0L
    private var listaAbertaEm = 0L
    /** Voltando da conversa, o Instagram recriou a lista no topo. */
    var voltaAoTopo = false
    /** A pagina "seguindo" do ViewPager na arvore, fora da tela. */
    var seguindoAoLado = emptyList<String>()
    /** Chamado depois de cada rolagem da lista (os testes mudam a lista no meio). */
    var aoRolar: () -> Unit = {}
    /** Chamado no comeco de cada rolagem, antes de qualquer checagem. */
    var antesDeRolar: () -> Unit = {}
    /** Como o NodeOps: com pausa ou cancelamento pedidos, toque e rolagem sao recusados. */
    var bloqueado: () -> Boolean = { false }
    var rolagensDaLista = 0
        private set

    // ---- Direct > caixa de entrada ----
    /** Chaves de [conversas], a de cima primeiro. Enviar leva a conversa para o topo. */
    val caixa = mutableListOf<String>()
    /** As caixas de entrada das OUTRAS contas (a troca de conta mostra a da conta nova). */
    val caixaDe = mutableMapOf<String, MutableList<String>>()
    private fun caixaAberta(): MutableList<String> = if (conta == contaInicial) caixa else caixaDe.getOrPut(conta) { mutableListOf() }
    var topoCaixa = 0
    var caixaEmPedidos = false
    /** Os proximos toques em linhas da caixa sao recusados (o no ficou velho: performAction da false). */
    var recusarToquesEmLinha = 0
    /**
     * Mensagem nova chegando: depois que o log tiver [Triple.first], na proxima leitura da caixa a conversa
     * [Triple.second] sobe para o topo [Triple.third] ms depois do COMECO da leitura (com [copiaNoInicio], a
     * leitura leva [custoLeitura] e a copia e a do comeco: a caixa muda enquanto a arvore e copiada).
     */
    var subida: Triple<String, String, Long>? = null
    private var sobe: Pair<String, Long>? = null
    private fun aplicarSubida() {
        sobe?.takeIf { relogio >= it.second }?.let { (c, _) -> sobe = null; caixaAberta().remove(c); caixaAberta().add(0, c) }
    }

    fun grupo(chave: String, nome: String, subtitulo: String?) = conversa(chave) {
        grupo = true; this.nome = nome; cabecalho = subtitulo; cartao = null
    }

    fun conversa(username: String, montar: Conversa.() -> Unit = {}) =
        Conversa(username).apply(montar).also { conversas[username] = it }

    // ---- Amigos Proximos ----
    /** @ -> esta na lista. Contas que existem e podem ser buscadas. */
    val amigos = linkedMapOf<String, Boolean>()
    var busca = ""
    /** A resposta da busca chega este tanto depois de escrever; ate la, as linhas da busca anterior. */
    var atrasoBusca = 0L
    private var buscaAnterior = ""
    private var buscaEm = 0L
    var toqueSemEfeito = false
    /** Quantas rolagens ate o item "Amigos Proximos" aparecer em Configuracoes. */
    var rolagensAteItem = 2
    private var rolagens = 0
    /** Como no aparelho: o item Bloks nao aceita clique pela acessibilidade, so o toque no ponto. */
    var bloksSemClique = true
    /** O aparelho faz gestos (arrastar, toque no ponto). */
    var gestos = true

    private val prof = SelectorProfile.IG_448

    /** O dono abre outra tela do Instagram (na pausa, por exemplo). */
    fun irPara(t: String) { log += "irPara $t"; tela = t; mudou() }

    /** true = esta leitura volta vazia (a janela em transicao: raizes() sem nada por um instante). */
    var leituraNula: () -> Boolean = { false }
    val telaAtual: String get() = tela

    /** A copia da arvore e a do COMECO da leitura (no aparelho, a tela pode mudar enquanto ela e copiada). */
    var copiaNoInicio = false

    override fun ler(): UiNode? {
        if (!copiaNoInicio) relogio += custoLeitura
        leituras++
        val r = if (leituraNula()) null else arvore()
        subida?.takeIf { (gatilho) -> tela == "direct" && log.any { it.startsWith(gatilho) } }?.let { (_, c, d) ->
            subida = null; sobe = c to relogio + d; mudou(relogio + d)
        }
        if (copiaNoInicio) relogio += custoLeitura
        return r
    }

    override fun existe(ids: List<String>): Boolean {
        buscas++
        return arvore()?.let { r -> ids.any(r::exists) } == true
    }

    override fun teclado(): Boolean = tecladoAberto

    override fun textosPorId(id: String): List<String> {
        buscas++
        return arvore()?.findByViewIdSuffix(id)?.mapNotNull { it.text ?: it.contentDescription }.orEmpty()
    }

    override val avisaMudancas: Boolean get() = !semEventos

    override suspend fun esperarEvento(ms: Long): Boolean {
        if (semEventos) { esperar(ms); return true }
        val chega = eventos.minOrNull()?.let { maxOf(it, relogio) }
        if (chega == null || chega > relogio + ms) { esperar(ms); return false }
        esperar(chega - relogio)
        eventos.removeAll { it <= relogio }
        return true
    }

    private fun arvore(): UiNode? {
        aplicarSubida()
        bolhas.removeAll { (c, t, em) -> (relogio >= em).also { if (it) c.mensagens += t } }
        val modal = modal()
        // Com a Modal na frente, a principal fica atras com a caixa de entrada velha.
        val principal = if (tela in MODAIS) inboxFixture(conta) else principal()
        val pkg = "com.instagram.android"
        val janelas = mutableListOf(Janelas.Janela(principal, pkg, app = true, camada = 1, id = 1))
        val semRaiz = visao == Visao.MODAL_SEM_RAIZ || visao == Visao.MODAL_ILEGIVEL
        modal?.let {
            janelas += if (semRaiz) Janelas.Janela(null, null, app = true, camada = 2, id = 2)
            else Janelas.Janela(it, pkg, app = true, camada = if (visao == Visao.MODAL_ABAIXO) 0 else 2, id = 2)
        }
        dialogo?.let { janelas += Janelas.Janela(it, pkg, app = true, camada = 3, id = 3) }
        // Teclado e o painel Pausar/Parar nao sao janelas de aplicativo.
        janelas += Janelas.Janela(ig(text = "q w e r t y"), "com.samsung.android.honeyboard", app = false, camada = 4)
        janelas += Janelas.Janela(ig(text = "Pausar"), "com.listalocal.instagram.claude", app = false, camada = 5)
        if (visao == Visao.OUTRO_APP_NA_FRENTE) {
            janelas += Janelas.Janela(ig(text = "Lista Local"), "com.listalocal.instagram.claude", app = true, camada = 9)
        }
        val listadas = when (visao) {
            Visao.SO_RESERVAS -> emptyList()
            Visao.MODAL_FORA_DA_LISTA -> janelas.filter { it.raiz !== modal }
            else -> janelas
        }
        val escolhidas = Janelas.escolher(
            listadas, pkg,
            temConversa = { Evidence.temConversa(it, prof) },
            ehPrincipal = { r -> prof.abasIds.any(r::exists) },
        ) {
            // rootInActiveWindow (a principal, presa como ativa) e a fonte do evento da Modal (a janela 2).
            listOfNotNull(
                Janelas.Reserva(principal, pkg, 1),
                modal?.takeIf { visao != Visao.MODAL_ILEGIVEL }?.let { Janelas.Reserva(it, pkg, 2) },
            )
        }
        return UiGroup.juntar(escolhidas)
    }

    /** Depois do Voltar, a conversa continua na tela este tanto (animacao de saida). */
    var atrasoFechar = 0L
    private var fechando: Conversa? = null
    private var fechaEm = 0L

    private fun desenhar(it: Conversa) = threadFixture(
        it.arrobaApos?.takeIf { d -> relogio >= abertaEm + d }?.let { _ -> it.username } ?: it.cabecalho, nome = it.nome, mensagens = if (recuo > 0) it.antigas else it.mensagens, campo = it.campo, avisos = it.avisos,
        cartao = it.cartao, cartaoExtra = it.cartaoExtra, dica = it.dica,
        enviarHabilitado = it.enviarHabilitado, novaConversa = it.novaConversa, comLista = !it.semLista,
    )

    private fun modal(): UiNode? = fechando?.takeIf { relogio < fechaEm }?.let(::desenhar) ?: when (tela) {
        "conversa" -> aberta?.takeIf { relogio >= abertaEm }?.let(::desenhar)
        "nova" -> ig(children = listOf(
            ig(id = "action_bar_title", text = "Nova mensagem"),
            ig(id = "direct_new_chat_to_field", text = "Para:"),
            ig(id = "search_edit_text", text = "Pesquisar", cls = "android.widget.EditText"),
        ))
        "amigos" -> {
            // A resposta da busca vem do servidor: por [atrasoBusca], a lista ainda e a da busca anterior.
            val mostra = if (relogio < buscaEm + atrasoBusca) buscaAnterior else busca
            amigosFixture(
                amigos.filterKeys { mostra.isEmpty() || it.startsWith(mostra.lowercase()) }.toList(),
                busca = mostra, campo = busca,
            )
        }
        else -> null
    }

    /**
     * Seguidores ja carregados (null = todos). Rolando no fim do carregado, o
     * Instagram carrega mais [atrasoCarregar] depois, com um evento de
     * "carregando" a cada 300 ms no meio.
     */
    var carregadas: Int? = null
    var atrasoCarregar = 0L
    /** Rolando com a proxima pagina vindo, rolar() diz "nao andou" (o circulo de carregando no fundo). */
    var rolagemParadaAoCarregar = false
    private var carregaEm: Long? = null

    private fun linhasDaLista(): List<Any> {
        carregaEm?.takeIf { relogio >= it }?.let { carregaEm = null; carregadas = carregadas!! + 20 }
        val lista = listaAberta()
        val n = carregadas?.takeIf { it < lista.size }
        return if (n != null) lista.take(n) else lista + (1..sugestoes).map { it }
    }

    private fun principal(): UiNode = when (tela) {
        "direct" -> inboxFixture(
            conta, caixaAberta().mapNotNull { conversas[it] }.drop(topoCaixa).take(porTela).map { (it.nomeNaLinha ?: it.nome) to it.estado },
            pedidos = caixaEmPedidos, posicoes = true, noTopo = topoCaixa == 0,
        )
        "perfil" -> perfilFixture(conta, seguidores.size)
        "seguidores" -> {
            val janela = linhasDaLista().drop(topoVisivel()).take(porTela)
            @Suppress("UNCHECKED_CAST")
            seguidoresFixture(
                donoDaLista ?: conta, janela.filterIsInstance<Pair<*, *>>() as List<Pair<String, String>>,
                sugestoes = janela.count { it is Int }, aba = abaMarcada, seguindoAoLado = seguindoAoLado,
                semAbas = relogio < listaAbertaEm + barraAtrasa,
            )
        }
        // Configuracoes e atividade como na tela real (t01a-t01d): Bloks, sem id, nada
        // rola nem e clicavel pela acessibilidade; cada item e uma View com desc.
        "config" -> ig(
            id = "swipeable_nav_view_pager_inner_recycler_view",
            children = listOf(
                ig(cls = "android.widget.Button", desc = "Voltar", clickable = true),
                ig(text = "Configurações e atividade"),
                ig(children = if (rolagens >= rolagensAteItem) {
                    listOf(
                        ig(text = "Quem pode ver seu conteúdo"),
                        ig(desc = "Privacidade da conta, Público", children = listOf(ig(text = "Privacidade da conta"))),
                        ig(desc = "Amigos Próximos, ${amigos.count { it.value }}", children = listOf(ig(text = "Amigos Próximos"))),
                    )
                } else {
                    listOf(ig(desc = "Central de Contas", children = listOf(ig(text = "Central de Contas"))))
                }),
            ),
        )
        else -> ig(children = listOf(abasDeBaixo()))
    }

    private fun idDe(no: UiNode) = no.viewIdResourceName?.substringAfter(":id/")

    /** Chamado no comeco de cada toque, antes de qualquer checagem (o dono toca em Pausar naquele instante). */
    var antesDeTocar: (UiNode) -> Unit = {}

    override fun tocar(no: UiNode, mesmoPausado: Boolean): Boolean {
        antesDeTocar(no)
        aplicarSubida()
        val id = idDe(no)
        log += "tocar ${id ?: no.contentDescription ?: no.text}"
        if (!mesmoPausado && bloqueado()) { log += "recusado"; return false }
        when {
            id == "direct_tab" -> tela = "direct"
            id == "row_thread_composer_edittext" -> tecladoAberto = true
            no.contentDescription == "Nova mensagem" -> tela = "nova"
            id == "profile_tab" -> tela = "perfil"
            id == "profile_header_followers_stacked_familiar" -> {
                tela = "seguidores"; topo = 0; listaAbertaEm = relogio; mudou(relogio + barraAtrasa)
                donoDaLista = null // o numero de seguidores do proprio perfil: a lista da conta logada
            }
            tela == "direct" && no.isClickable && id == null && no.children.size >= 3 &&
                no.children.first().contentDescription != null -> {
                val nome = no.children.first().contentDescription!!.substringBefore(", ")
                log[log.lastIndex] = "tocar linha $nome"
                if (recusarToquesEmLinha > 0) { recusarToquesEmLinha--; log += "recusado"; return false }
                // A linha e uma View de Compose num LUGAR da tela: o toque abre a conversa que esta ali AGORA.
                val c = no.bounds?.let { b -> caixaAberta().mapNotNull { conversas[it] }.drop(topoCaixa)[(b.topo - LINHA_TOPO) / LINHA_ALTURA] }
                    ?: caixaAberta().mapNotNull { conversas[it] }.first { (it.nomeNaLinha ?: it.nome) == nome }
                if (!c.linhaNaoAbre) {
                    anterior = tela; aberta = c; tela = "conversa"; abertaEm = relogio + atrasoAbrir; mudou(abertaEm)
                }
            }
            no.contentDescription == "Opções" -> tela = "config"
            no.contentDescription?.startsWith("Amigos Próximos") == true -> if (bloksSemClique) { log += "sem efeito"; return false } else tela = "amigos"
            id == "row_header_action" -> amigos.keys.forEach { amigos[it] = false }
            id == "done_button" -> { tela = "config"; busca = "" }
            id == "row_user_container" && !toqueSemEfeito -> {
                val user = no.walk().firstNotNullOf { n ->
                    n.text.takeIf { idDe(n) == "row_user_username" }
                }
                amigos[user] = !(amigos[user] ?: false)
            }
            id == "row_thread_composer_send_button_container" -> {
                if (recusarEnviar) { log += "recusado"; return false }
                val c = aberta ?: return false
                toquesEnviar += relogio
                leiturasNosToques += leituras
                when (envio) {
                    Envio.OK -> {
                        if (atrasoBolha > 0) bolhas += Triple(c, c.campo!!, relogio + atrasoBolha) else c.mensagens += c.campo!!
                        mudou(relogio + atrasoBolha)
                        c.campo = null
                        caixaAberta().remove(c.username); caixaAberta().add(0, c.username); c.estado = "Enviado"
                    }
                    Envio.NADA -> Unit
                    Envio.NAO_ENVIADA -> { c.mensagens += c.campo!!; c.campo = null; c.avisos += "Não enviada" }
                    Envio.RESTRICAO -> c.avisos += "Tente novamente mais tarde"
                    Envio.RESTRICAO_DIALOGO -> dialogo = ig(id = "dialog_container", children = listOf(
                        ig(text = "Tente novamente mais tarde"), ig(text = "OK", clickable = true),
                    ))
                }
                aposEnviarAbre?.let { conversas[it] }?.let { aberta = it }
            }
        }
        mudou()
        return true
    }

    /** Os proximos toques em Enviar sao recusados ANTES de acontecer (o Instagram saiu da frente naquele instante). */
    var recusarEnviarAntes = 0
    /** Quando um toque em Enviar e recusado antes (o que o dono fez: ligacao, Home). */
    var aoRecusarEnviar: () -> Unit = {}

    override fun tocarEnviar(no: UiNode): Boolean? {
        if (recusarEnviarAntes > 0) {
            recusarEnviarAntes--
            log += "tocar ${idDe(no)}"; log += "recusado antes"
            aoRecusarEnviar()
            return null
        }
        return tocar(no, mesmoPausado = true)
    }

    override fun escrever(no: UiNode, texto: String): Boolean {
        log += "escrever ${idDe(no)} '$texto'"
        if (!escreverFunciona) return false
        // Como o NodeOps.setText: apagar vale mesmo pausado; escrever, nao.
        if (texto.isNotEmpty() && bloqueado()) { log += "recusado"; return false }
        when (idDe(no)) {
            "row_thread_composer_edittext" -> aberta?.campo = texto.ifEmpty { null }?.let { t -> campoCorta?.let(t::take) ?: t }
            "search_edit_text" -> {
                buscaAnterior = if (relogio < buscaEm + atrasoBusca) buscaAnterior else busca
                busca = texto
                buscaEm = relogio
                mudou(relogio + atrasoBusca)
            }
        }
        mudou()
        return true
    }

    override fun rolar(no: UiNode): Boolean {
        antesDeRolar()
        log += "rolar ${idDe(no) ?: ""}".trimEnd()
        // Como o servico: recusada (pausa, outro app na frente) nunca e "nao andou".
        if (bloqueado() || visao == Visao.OUTRO_APP_NA_FRENTE) { log += "recusado"; throw Fila.FailedSafe("rolagem recusada") }
        when {
            tela == "seguidores" -> {
                if (idDe(no) != "list") return true // o ViewPager: trocaria de aba
                val fim = maxOf(0, linhasDaLista().size - porTela)
                if (topo >= fim) {
                    if ((carregadas ?: Int.MAX_VALUE) >= listaAberta().size) return false
                    if (carregaEm == null) {
                        carregaEm = relogio + atrasoCarregar
                        (300L until atrasoCarregar step 300).forEach { mudou(relogio + it) }
                        mudou(carregaEm!!)
                    }
                    log += "rolar list"
                    mudou()
                    return !rolagemParadaAoCarregar
                }
                rolagemDe = topoVisivel()
                topo = minOf(topo + passo, fim)
                rolagemAte = relogio + animacaoRolagem
                // Um evento a cada 100 ms enquanto a lista anda.
                (100L..animacaoRolagem step 100).forEach { mudou(relogio + it) }
                rolagensDaLista++
                aoRolar()
            }
            tela == "conversa" -> {
                if (recuo == 0) return false // ja no fim da conversa
                recuo = 0
            }
            tela == "direct" -> {
                val fim = maxOf(0, caixaAberta().size - porTela)
                if (topoCaixa >= fim) return false
                topoCaixa = minOf(topoCaixa + passo, fim)
            }
            else -> rolagens++
        }
        mudou()
        return true
    }

    override fun rolarParaTras(no: UiNode): Boolean {
        log += "rolar para tras ${idDe(no) ?: ""}".trimEnd()
        if (bloqueado() || visao == Visao.OUTRO_APP_NA_FRENTE) { log += "recusado"; throw Fila.FailedSafe("rolagem recusada") }
        if (tela == "conversa") {
            if (recuo > 0 || aberta?.antigas.isNullOrEmpty()) return false // o comeco da conversa
            recuo = 1
            mudou()
            return true
        }
        if (tela != "direct" || topoCaixa == 0) return false
        topoCaixa = maxOf(0, topoCaixa - passo)
        mudou()
        return true
    }

    override fun arrastar(): Boolean {
        log += "arrastar"
        if (!gestos || bloqueado()) return false
        if (tela == "config") rolagens++
        mudou()
        return true
    }

    override fun tocarNoPonto(no: UiNode): Boolean {
        log += "ponto ${no.contentDescription ?: no.text}"
        if (!gestos || bloqueado()) return false
        if (no.contentDescription?.startsWith("Amigos Próximos") == true) tela = "amigos"
        mudou()
        return true
    }

    /** Toque longo na aba Perfil: o seletor de contas do Instagram abre (o dono escolhe). */
    var seletorAberto = false
    override fun tocarLongo(no: UiNode): Boolean {
        log += "tocarLongo ${idDe(no)}"
        if (idDe(no) != "profile_tab") return false
        seletorAberto = true
        tela = "perfil" // o seletor abre sobre o perfil; escolhida a conta, o perfil dela
        mudou()
        return true
    }

    /** Relogio de cada link aberto, por @. */
    val abriuEm = mutableMapOf<String, Long>()

    override fun abrirConversa(username: String): Boolean {
        log += "abrir $username"
        abriuEm[username] = relogio
        // @ inexistente: o Instagram nao faz nada e fica onde estava. Grupo nao tem link de @.
        conversas[username]?.takeIf { !it.grupo }?.let {
            if (tela != "conversa") anterior = tela
            aberta = it; tela = "conversa"; abertaEm = relogio + atrasoAbrir; mudou(abertaEm); recuo = 0
        }
        return true
    }

    override fun abrirInstagram(): Boolean {
        log += "abrirInstagram"
        // Como o servico: pausado (o dono, tela bloqueada, ligacao), nada abre por cima.
        if (bloqueado()) { log += "recusado"; return false }
        return true
    }

    override fun voltar(): Boolean {
        log += "voltar"
        mudou()
        if (tecladoAberto) { tecladoAberto = false; return true }
        if (tela == "conversa" && atrasoFechar > 0) { fechando = aberta; fechaEm = relogio + atrasoFechar; mudou(fechaEm) }
        tela = when (tela) {
            "conversa" -> anterior.also { if (it == "seguidores" && voltaAoTopo) topo = 0 }
            "direct", "perfil" -> "inicio"
            "seguidores" -> "perfil"
            "nova" -> "direct"
            "amigos" -> "config"
            "config" -> "perfil"
            else -> "inicio"
        }
        if (tela != "conversa") { aberta = null; recuo = 0 }
        return true
    }

    override fun agora(): Long = relogio

    override suspend fun esperar(ms: Long) {
        relogio += ms
        esperas++
        esperadoMs += ms
        aoEsperar(ms)
        val gatilho = interromperApos ?: return
        if (log.any { it.startsWith(gatilho) }) throw Interrompido("cancelado pelo dono")
    }

    fun indice(prefixo: String) = log.indexOfFirst { it.startsWith(prefixo) }

    private companion object {
        val MODAIS = setOf("conversa", "nova", "amigos")
    }
}

/** Registro em memoria, no mesmo log do Instagram falso. */
class FakeRegistro(private val log: MutableList<String>) : Registro {
    var commitFunciona = true
    val gravados = linkedMapOf<String, Desfecho>()

    override fun marcarCommit(username: String): Boolean {
        if (!commitFunciona) return false
        log += "commit $username"
        gravados[username] = Desfecho.INCERTO
        return true
    }

    override fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int>) {
        log += "gravar $username $desfecho"
        gravados[username] = desfecho
    }
}
