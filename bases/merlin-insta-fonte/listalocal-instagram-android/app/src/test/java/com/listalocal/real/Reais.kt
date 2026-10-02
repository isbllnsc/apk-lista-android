package com.listalocal.real

import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.ArvoreReal
import com.listalocal.core.tree.UiNode
import com.listalocal.service.Tela
import org.junit.Assert.assertTrue

/**
 * O corpus de telas REAIS (src/test/resources/real, anonimizado) agrupado pelo
 * que cada tela E de verdade (INSTAGRAM-APP-REAL.md e os prints da rodada 1).
 * Os testes golden afirmam o que o app deveria concluir em cada uma.
 */
object Telas {
    val PERFIL = listOf("perfil_proprio", "perfil_proprio_2", "perfil_rolado", "perfil_menu_criar")
    /** Perfil com o numero de seguidores na tela (o toque que leva a lista). */
    val PERFIL_COM_NUMEROS = listOf("perfil_proprio", "perfil_proprio_2", "perfil_menu_criar")
    val CAIXA = listOf(
        "caixa_principal", "caixa_principal_2", "caixa_principal_3", "caixa_rolada", "caixa_rolada_2",
        "caixa_velha_perfil_na_frente",
    )
    val PEDIDOS = listOf("caixa_pedidos")
    val ROLAGEM = (0..12).map { "seguidores_rolagem_%02d".format(it) }
    /** Aba "N seguidores" marcada, com linhas de seguidor. */
    val SEGUIDORES = listOf("seguidores_topo") + ROLAGEM + listOf("seguidores_fim", "seguidores_busca_1", "seguidores_busca_3")
    /** Aba seguidores marcada, depois do fim: so "Sugestoes para voce". */
    val SO_SUGESTOES = listOf("seguidores_so_sugestoes")
    val SEGUINDO = listOf("seguindo_topo")
    val SINALIZADAS = listOf("sinalizadas")
    val LISTA_DE_SEGUIDORES_ABA_OUTRA = SEGUINDO + SINALIZADAS
    val CONFIG = (1..4).map { "config_$it" }
    /** Tudo o que o uiautomator leu (atividade principal). */
    val PRINCIPAIS = PERFIL + CAIXA + PEDIDOS + SEGUIDORES + SO_SUGESTOES + SEGUINDO + SINALIZADAS + CONFIG
    /** Telas SEM a barra de abas (Pagina inicial, Direct, Perfil...). */
    val SEM_BARRA_DE_ABAS = SEGUIDORES + SO_SUGESTOES + SEGUINDO + SINALIZADAS + CONFIG

    val AMIGOS = listOf("v_amigos_topo", "v_amigos_membros_fim", "v_amigos_busca_membro", "v_amigos_rolada")
    val NOVA_MENSAGEM = listOf("v_nova_mensagem", "v_nova_mensagem_1_pessoa", "v_nova_mensagem_grupo")
    val CONVERSA = listOf("v_conversa", "v_conversa_com_texto", "v_conversa_nova", "v_conversa_temporaria")
    /** Folha de compartilhar de um post: busca, caixas de pessoa, campo, "Enviar" e "Enviar para nova conversa em grupo". */
    val FOLHA = listOf("v_compartilhar", "v_compartilhar_busca", "v_compartilhar_1", "v_compartilhar_grupo")
    /** Post aberto pela grade do perfil (atras da folha), sem a barra de abas. */
    val POST = listOf("v_post")
    /** Telas sem a barra de abas onde o Instagram pode estar ao Conferir/Iniciar (a folha e o post sao da atividade principal). */
    val MODAIS = AMIGOS + NOVA_MENSAGEM + CONVERSA + FOLHA + POST
    val TODAS = PRINCIPAIS + MODAIS

    fun raiz(nome: String): UiNode = ArvoreReal.raiz(nome)
}

object Perfis {
    /** O perfil fixo cru (NodeOps): id conhecido ou nada. */
    val FIXO: SelectorProfile = SelectorProfile.IG_448
    /** O que a operacao e a conferencia usam no aparelho antes de calibrar: fixo + assinatura ao vivo. */
    val APARELHO: SelectorProfile = SelectorProfile.IG_448.paraAparelho(null)
}

fun UiNode.id(): String? = viewIdResourceName?.substringAfter(":id/")

fun UiNode.rotulo(): String? = contentDescription ?: text

/** Descricao curta de um no para as mensagens de falha (id e rotulo, nunca a arvore). */
fun UiNode.descr(): String = "${id() ?: "-"} '${rotulo().orEmpty()}' ${bounds?.let { "[${it.topo}-${it.baixo}]" } ?: ""}".trim()

/**
 * Roda [bloco] em cada tela; ele devolve null se a tela confere, ou o que deu
 * errado. Falha uma vez so, com a lista das telas erradas.
 */
fun emTodas(telas: List<String>, oQue: String, bloco: (nome: String, raiz: UiNode) -> String?) {
    val erros = telas.mapNotNull { n -> bloco(n, Telas.raiz(n))?.let { "  $n: $it" } }
    assertTrue("$oQue\n${erros.joinToString("\n")}", erros.isEmpty())
}

/**
 * O Instagram do dono como uma sequencia de telas REAIS: cada toque leva a tela
 * que o Instagram abre de verdade (INSTAGRAM-APP-REAL.md). Toque em algo que o
 * roteiro nao previa vai para [inesperados] e a tela fica (no aparelho, abriria
 * outra coisa: conversa, story, perfil de alguem).
 *
 * [atraso]: a tela nova aparece este tanto depois do toque (a troca de tela do
 * Instagram nao e instantanea; a leitura da arvore leva ~0,8 s na rodada 1).
 */
class TelaRoteiro(
    inicio: String,
    private val atraso: Long = 0,
    /** Telas que a lista percorre a cada rolagem (a ultima nao anda mais). */
    private val rolagem: List<String> = emptyList(),
    /** Como o app enxerga cada tela (ex.: ArvoreReal::comIdsTrocados, outro aparelho/versao). */
    private val ver: (UiNode) -> UiNode = { it },
    /** Texto escrito na busca de Amigos Proximos -> a tela da resposta (chega [atraso] depois, como a do servidor). */
    private val busca: Map<String, String> = emptyMap(),
) : Tela {
    var atual: String = inicio
        private set
    private var pendente: Pair<String, Long>? = null
    var relogio = 0L
        private set
    val log = mutableListOf<String>()
    val inesperados = mutableListOf<String>()

    /** A tela onde o Instagram fica depois de terminar a troca em andamento. */
    fun assentar(): String { pendente?.let { atual = it.first }; pendente = null; return atual }

    /** Troca a tela agora (o dono mexeu no aparelho). */
    fun trocarPara(tela: String) { pendente = null; atual = tela }

    private fun agendar(tela: String) {
        if (atraso <= 0) { atual = tela; pendente = null } else pendente = tela to relogio + atraso
    }

    private fun aplicar() {
        pendente?.let { (t, em) -> if (relogio >= em) { atual = t; pendente = null } }
    }

    override fun ler(): UiNode { aplicar(); return ver(Telas.raiz(atual)) }

    /** Para onde o Instagram vai com o toque neste no, na tela [de]; null = nao previsto. */
    private fun destino(no: UiNode, de: String): String? {
        val r = no.rotulo().orEmpty()
        val id = no.id()?.removeSuffix("_v2") // o mesmo controle com ids trocados
        return when {
            id == "direct_tab" -> "caixa_principal"
            id == "profile_tab" -> "perfil_proprio"
            // A lista abre no topo: o da rolagem do teste, se houver.
            id == "profile_header_followers_stacked_familiar" -> rolagem.firstOrNull() ?: "seguidores_topo"
            r == "Opções" && de in Telas.PERFIL -> "config_1"
            r == "Nova mensagem" && (de in Telas.CAIXA || de in Telas.PEDIDOS) -> "v_nova_mensagem"
            r.startsWith("Amigos Próximos") && de in Telas.CONFIG -> "v_amigos_topo"
            else -> null
        }
    }

    private fun tocou(tipo: String, no: UiNode): Boolean {
        aplicar()
        log += "$tipo ${no.descr()}"
        val d = destino(no, atual)
        if (d == null) inesperados += "$tipo em $atual: ${no.descr()}" else agendar(d)
        return true
    }

    override fun tocar(no: UiNode, mesmoPausado: Boolean) = tocou("tocar", no)
    override fun tocarNoPonto(no: UiNode) = tocou("ponto", no)
    override fun tocarLongo(no: UiNode): Boolean {
        aplicar()
        log += "longo ${no.descr()}"
        if (no.id()?.removeSuffix("_v2") != "profile_tab") inesperados += "toque longo em $atual: ${no.descr()}"
        return true
    }

    override fun escrever(no: UiNode, texto: String): Boolean {
        aplicar()
        log += "escrever ${no.descr()}"
        val resposta = busca[texto]?.takeIf { atual in Telas.AMIGOS && no.id() == "search_edit_text" }
        if (resposta == null) { inesperados += "escrever em $atual: ${no.descr()}"; return false }
        agendar(resposta)
        return true
    }

    override fun rolar(no: UiNode): Boolean {
        aplicar()
        log += "rolar ${no.descr()}"
        val i = rolagem.indexOf(atual)
        if (i < 0 || i == rolagem.lastIndex) return false
        agendar(rolagem[i + 1])
        return true
    }

    /** A caixa rolada volta ao topo (caixa_principal) numa rolagem para tras. */
    override fun rolarParaTras(no: UiNode): Boolean {
        aplicar()
        log += "rolar para tras ${no.descr()}"
        if (atual !in listOf("caixa_rolada", "caixa_rolada_2")) return false
        agendar("caixa_principal")
        return true
    }

    override fun arrastar(): Boolean {
        aplicar()
        log += "arrastar"
        val i = Telas.CONFIG.indexOf(atual)
        if (i < 0 || i == Telas.CONFIG.lastIndex) return false
        agendar(Telas.CONFIG[i + 1])
        return true
    }

    override fun voltar(): Boolean {
        aplicar()
        log += "voltar"
        // Voltar no meio de uma troca de tela vale para a tela que esta chegando (o Instagram os processa em ordem).
        val d = when (pendente?.first ?: atual) {
            in Telas.SEGUIDORES, in Telas.SO_SUGESTOES, in Telas.SEGUINDO, in Telas.SINALIZADAS, in Telas.CONFIG -> "perfil_proprio"
            in Telas.NOVA_MENSAGEM, in Telas.CONVERSA -> "caixa_principal"
            in Telas.AMIGOS -> "config_4"
            in Telas.FOLHA -> "v_post"
            in Telas.POST -> "perfil_proprio"
            else -> null
        }
        d?.let(::agendar)
        return true
    }

    override fun agora(): Long = relogio
    override fun abrirConversa(username: String): Boolean {
        log += "abrirConversa $username"
        inesperados += "abrir conversa com @$username em $atual"
        return false
    }
    override fun abrirInstagram(): Boolean { log += "abrirInstagram"; return true }
    override suspend fun esperar(ms: Long) { relogio += ms }
}

/** null se a conta lida e a do dono; senao o que foi lido (null tambem e erro: a conta nao foi lida). */
fun soMinhaConta(lida: String?): String? = if (lida == "minhaconta") null else "leu @$lida"
