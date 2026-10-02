package com.listalocal.service

import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.state.DmState
import com.listalocal.core.tree.UiNode

/**
 * O que os fluxos precisam da tela do Instagram. O servico implementa com
 * NodeOps (cada acao reconfere janela, prazo, pausa e cancelamento logo antes
 * do toque); os testes implementam com um Instagram falso. Assim os fluxos
 * sao Kotlin puro e testados sem aparelho.
 */
interface Tela {
    /** Copia da arvore da janela do Instagram; null fora dele. */
    fun ler(): UiNode?

    /**
     * Toca no no (ou no ancestral clicavel mais proximo). [mesmoPausado] so
     * para o toque em Enviar logo depois do commit: ali, recusar o toque por
     * uma pausa pedida no mesmo instante deixaria a pessoa em INCERTO a toa.
     */
    fun tocar(no: UiNode, mesmoPausado: Boolean = false): Boolean

    /**
     * O toque em Enviar, logo depois do commit (como [tocar] com mesmoPausado). null = recusado ANTES de acontecer (o
     * Instagram fora da frente, prazo): nada foi tocado, com certeza. false = tentado, sem confirmacao.
     */
    fun tocarEnviar(no: UiNode): Boolean? = tocar(no, mesmoPausado = true)

    /** Texto inteiro de uma vez (ACTION_SET_TEXT): sem digitacao simulada. */
    fun escrever(no: UiNode, texto: String): Boolean

    /**
     * Rola a lista para a frente. false = a lista nao andou (o fim, ou o
     * circulo de carregando no fundo). Rolagem RECUSADA (o Instagram fora da
     * frente, pausa, prazo) lanca [Fila.FailedSafe]: nunca vira fim da lista.
     */
    fun rolar(no: UiNode): Boolean

    /** Rola a lista para tras (a caixa de entrada de volta ao topo). false = nao andou; recusada lanca, como [rolar]. */
    fun rolarParaTras(no: UiNode): Boolean = false

    /**
     * Relogio monotono em ms para os prazos de cada passo: uma leitura lenta
     * da arvore (0,8 s na rodada 1) nao pode esticar o prazo.
     */
    fun agora(): Long

    /** Abre a conversa pelo link https://ig.me/m/<username>, so no app do Instagram. */
    fun abrirConversa(username: String): Boolean

    fun abrirInstagram(): Boolean

    fun voltar(): Boolean

    /**
     * Espera fixa (sem sorteio). Lanca [Interrompido] se a operacao foi
     * cancelada, o prazo venceu ou o servico perdeu a permissao.
     */
    suspend fun esperar(ms: Long)

    /**
     * Espera a tela do Instagram mudar (um evento de acessibilidade) ou [ms], o
     * que vier antes. true = veio evento, ou nao ha como saber (sem eventos:
     * espera fixa). false = [ms] sem nenhuma mudanca: a arvore e a da ultima leitura.
     */
    suspend fun esperarEvento(ms: Long): Boolean {
        esperar(ms)
        return true
    }

    /** O aparelho ja entregou eventos do Instagram: esperar por eles vale. */
    val avisaMudancas: Boolean get() = false

    /** Algum destes ids na tela, sem copiar a arvore (no aparelho: busca direta por viewId). */
    fun existe(ids: List<String>): Boolean = ler()?.let { r -> ids.any(r::exists) } == true

    /**
     * Os textos (ou descricoes) dos nos com este id, por busca direta, sem
     * copiar a arvore: para conferir um rotulo (a conta no titulo da lista)
     * antes de cada pessoa sem o custo de uma leitura inteira.
     */
    fun textosPorId(id: String): List<String> =
        ler()?.findByViewIdSuffix(id)?.mapNotNull { it.text ?: it.contentDescription }.orEmpty()

    /** O teclado esta aberto (o primeiro Voltar so fecha ele). */
    fun teclado(): Boolean = false

    /**
     * Arrasta a tela para cima, por gesto. So para Configuracoes e atividade
     * (Bloks): ali nada rola pela acessibilidade (t01a-t01d). false = recusado.
     */
    fun arrastar(): Boolean = false

    /** Toque por gesto no centro do no: item Bloks sem clique pela acessibilidade. */
    fun tocarNoPonto(no: UiNode): Boolean = false

    /** Toque longo (a aba Perfil: abre o seletor de contas do Instagram). */
    fun tocarLongo(no: UiNode): Boolean = false

    /** As janelas como o servico as ve agora (tipo, camada, pacote, raiz nula), sem nada de pessoa: so para o log. */
    fun diagnostico(): String = ""
}

/** Sem eventos do Instagram, a tela e conferida a cada tanto (como antes). */
const val POLL_SEM_EVENTO_MS = 250L

/** Com eventos, a tela e conferida a cada mudanca; sem nenhuma em tanto, confere mesmo assim. */
const val RESERVA_MS = 1_000L

/** Ate a proxima mudanca da tela, sem passar de [fim] no relogio. */
suspend fun Tela.esperarMudanca(fim: Long): Boolean {
    val passo = if (avisaMudancas) RESERVA_MS else POLL_SEM_EVENTO_MS
    return esperarEvento(minOf(passo, fim - agora()).coerceAtLeast(1))
}

/** Le a tela a cada mudanca ate [cond] valer; null quando passam [ms] no relogio (le pelo menos uma vez). */
suspend fun Tela.esperarAte(ms: Int, cond: (UiNode) -> Boolean): UiNode? {
    val fim = agora() + ms
    while (true) {
        ler()?.let { if (cond(it)) return it }
        if (agora() >= fim) return null
        esperarMudanca(fim)
    }
}

/**
 * Espera algum destes ids por busca direta (sem copiar a arvore a cada volta)
 * e so entao le a arvore inteira, uma vez. null = nao apareceu em [ms].
 */
suspend fun Tela.esperarIds(ms: Int, ids: List<String>): UiNode? {
    val fim = agora() + ms
    while (true) {
        if (existe(ids)) ler()?.takeIf { r -> ids.any(r::exists) }?.let { return it }
        if (agora() >= fim) return null
        esperarMudanca(fim)
    }
}

/** Depois de um Voltar, quanto esperar a tela responder antes de outro. */
const val SAIR_MS = 1_500L

/**
 * Volta ate a conversa (cabecalho ou campo) sumir, conferindo por busca de id.
 * Com o teclado aberto o primeiro Voltar so o fecha. Depois de cada Voltar,
 * espera a tela responder (ate [SAIR_MS]) antes de outro: um Voltar a mais,
 * com a conversa ainda fechando, sairia da lista.
 */
suspend fun Tela.sairDaConversa(prof: SelectorProfile): Boolean {
    for (i in 0 until 3) {
        if (!existe(prof.conversaIds)) return true
        val soTeclado = teclado()
        voltar()
        val fim = agora() + SAIR_MS
        while (agora() < fim && (if (soTeclado) teclado() else existe(prof.conversaIds))) esperarMudanca(fim)
    }
    return !existe(prof.conversaIds)
}

/** Parada pedida de fora (cancelamento, prazo, permissao) no meio de um passo. */
class Interrompido(motivo: String) : Exception(motivo)

/**
 * Pausar ou Parar no intervalo, com a conversa aberta e nada escrito: a pessoa
 * fica sem desfecho e a fila a retoma depois (ou para).
 */
class Adiado : Exception("adiado no intervalo")

/** Desfecho final de uma pessoa, nos dois modos. */
enum class Desfecho(val rotulo: String, val falha: Boolean) {
    ENVIADO("Enviada", false),
    ADICIONADO("Adicionada aos Amigos Próximos", false),
    JA_NA_LISTA("Já estava nos Amigos Próximos", false),
    REMOVIDO("Retirada dos Amigos Próximos (pediu para parar)", false),
    PEDIU_PARA_PARAR("Pediu para parar: não enviada", false),
    /** Fora pela lista do dono, ja recebeu antes ou conversa sem @: nada foi aberto para enviar. */
    PULADO("Pulada", false),
    NAO_ENCONTRADO("Conta não encontrada", true),
    AMBIGUO("Mais de uma conta com o mesmo @", true),
    INDISPONIVEL("Não recebe mensagens", true),
    NAO_SEGUE("Não segue a conta (visto na conversa)", true),
    FALHA("Falha antes do envio", true),
    INCERTO("Sem confirmação: pode ter chegado", true),
    ;

    /** Recebeu (ou pode ter recebido) ou pediu para parar: nunca entra de novo numa operacao. */
    val bloqueiaNovoEnvio: Boolean get() = this == ENVIADO || this == INCERTO || this == PEDIU_PARA_PARAR

    companion object {
        fun de(state: DmState): Desfecho = when (state) {
            DmState.ENVIADO -> ENVIADO
            DmState.NAO_ENCONTRADO -> NAO_ENCONTRADO
            DmState.AMBIGUO -> AMBIGUO
            DmState.INDISPONIVEL -> INDISPONIVEL
            DmState.NAO_SEGUE -> NAO_SEGUE
            DmState.PEDIU_PARA_PARAR -> PEDIU_PARA_PARAR
            DmState.INCERTO, DmState.COMMIT -> INCERTO
            else -> FALHA
        }
    }
}

/** Onde o desfecho de cada pessoa fica gravado (no aparelho). */
interface Registro {
    /** "Vou enviar", gravado de forma SINCRONA antes do toque. false = nao toque. */
    fun marcarCommit(username: String): Boolean

    /** [enviadas]: os numeros das mensagens em que Enviar foi tocado (planilha e revezamento na retomada). */
    fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int> = emptyList())
}
