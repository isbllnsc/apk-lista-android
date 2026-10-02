package com.listalocal.service

import com.listalocal.core.followers.Batch
import com.listalocal.core.followers.FollowerImport
import com.listalocal.core.selectors.PerfilAprendido
import com.listalocal.core.selectors.SelectorProfile

/** Os modos disponíveis. */
enum class Modo(val titulo: String, val chaves: List<String>) {
    DM("Mensagem direta, uma a uma", SelectorProfile.DM_KEYS),
    AMIGOS_PROXIMOS("Amigos Próximos para um story", SelectorProfile.CF_KEYS),
    /** Fase 1: cria listas de transmissão da agenda; Fase 2: envia mensagem por cada lista. */
    LISTAS_TRANSMISSAO("Listas de transmissão (agenda)", emptyList()),
}

/**
 * Intervalo FIXO entre uma pessoa e a proxima, escolhido pelo dono. Sem
 * sorteio: o app nao disfarca nada.
 */
enum class Velocidade(val rotulo: String, val segundos: Int) {
    NORMAL("Normal", 30),
    RAPIDO("Rápido", 10),
    MUITO_RAPIDO("Muito rápido", 1),
}

/**
 * De onde vem cada pessoa no modo C: a lista do proprio Instagram, lida ao
 * vivo. Sem extracao de seguidores e sem arquivo (decisao do dono).
 */
enum class Origem(val titulo: String) {
    SEGUIDORES("Seus seguidores"),
    CONVERSAS("Conversas do Direct"),
}

/** Como saem as ate 3 mensagens escritas pelo dono. */
enum class ModoMensagens(val rotulo: String) {
    /** Cada pessoa recebe UMA das mensagens, em turnos 1, 2, 3, 1... */
    REVEZAR("em revezamento"),
    /** Cada pessoa recebe todas, na ordem; a seguinte so depois da anterior confirmada. */
    SEQUENCIA("em sequência"),
}

/**
 * Uma operacao do modo C, gravada no aparelho para retomar do ponto salvo:
 * a escolha do dono e o ponto de parada. Nunca a lista de seguidores.
 */
data class Campanha(
    val conta: String,
    val origem: Origem,
    /** 1 a 3 mensagens, sem as vazias. */
    val mensagens: List<String>,
    val velocidade: Velocidade,
    val naoEnviar: Set<String> = emptySet(),
    val soPara: Set<String> = emptySet(),
    val pularComerciais: Boolean = false,
    val baseLegal: String = "",
    val modoMensagens: ModoMensagens = ModoMensagens.REVEZAR,
    /** "Enviar para ate X pessoas": contam so as que receberam (ou podem ter recebido). null = sem limite. */
    val limite: Int? = null,
    /** "Comecar a partir de @": quem vem antes na lista fica de fora, sem contar. */
    val aPartirDe: String? = null,
    /** "Pular quem ja recebeu desta conta". Desligado, so quem recebeu com certeza volta (INCERTO e pedido de parar, nunca). */
    val pularJaRecebeu: Boolean = true,
    /** "Parar as HH:MM", em minutos do dia (0..1439). null = sem hora. */
    val pararAs: Int? = null,
    /** Quando comecou: desfecho gravado desde entao e desta operacao (a retomada pula). */
    val inicio: Long,
    /** O ultimo @ processado (na caixa de entrada sem @, o nome da conversa). */
    val ultimo: String? = null,
)

/** Fase corrente para a UI (espelha, em alto nivel, as maquinas de estado). */
enum class RunPhase {
    IDLE, PREPARING, OPENING_APP, OPENING_SCREEN, READING_LIST, WORKING, WAITING_INTERVAL,
    WAITING_CONFIRMATION, SAVING, COMPLETED, PAUSED, RESTRICTED, CANCELLED,
    UI_CHANGED, VERSION_UNSUPPORTED, PERMISSION_LOST, FAILED_SAFE,
    /** Fase 1 das listas de transmissão: abrindo o seletor de nova transmissão. */
    OPENING_BROADCAST,
    /** Fase 1: selecionando contatos no seletor. */
    SELECTING,
    /** Fase 1 concluída — app deve mostrar tela de mensagem antes de iniciar Fase 2. */
    LISTS_CREATED,
    /** Fase 1 concluída, iniciando Fase 2 (envio via listas). */
    SENDING_PHASE,
}

/** O que aconteceu com uma pessoa. A tela mostra o @ normal; os registros tecnicos, nao. */
data class PersonResult(
    val username: String,
    val name: String,
    val lote: Int,
    val desfecho: Desfecho,
    val motivo: String,
    /** Numeros (1 a 3) das mensagens em que Enviar foi tocado para esta pessoa. */
    val enviadas: List<Int> = emptyList(),
) {
    /** Conta para "Enviar para ate X pessoas": recebeu, ou pode ter recebido (sem confirmacao). */
    val contaNoLimite: Boolean get() = desfecho == Desfecho.ENVIADO || desfecho == Desfecho.INCERTO
}

/** Um bloco de conferencia (Lote NNN). */
data class ListProgress(
    val index: Int,
    val label: String,
    val total: Int,
    val done: Boolean = false,
)

/** Progresso de criação e identificação de uma lista de transmissão. */
data class BroadcastListaProgress(
    val index: Int,
    val label: String,
    /** Contatos encontrados no seletor ou quantidade de destinatários identificados. */
    val selecionados: Int = 0,
    /** Total de contatos no lote (se criado nesta sessão). */
    val total: Int = 0,
    /** true = lista criada com sucesso ou pronta para envio. */
    val criada: Boolean = false,
    /** Motivo de falha, se houver. */
    val motivo: String = "",
    /** true se foi lida/escaneada do WhatsApp (já existia), false se foi criada nesta sessão pelo APK. */
    val jaExistia: Boolean = false,
    /** Subtítulo lido no WhatsApp (ex: "256 destinatários"). */
    val subtitulo: String = "",
    /** Se está selecionada para envio na Etapa 4. */
    val selecionadaParaEnvio: Boolean = true,
)

/**
 * Resultado de "Conferir o Instagram" para um modo: o que o app achou de
 * verdade na tela, naquela versao, neste aparelho. E o que liga o modo.
 */
data class CompatCheck(
    val modo: Modo,
    val version: String,
    /** O @ da conta aberta no Instagram, lido na tela. */
    val conta: String? = null,
    /** chave do seletor -> encontrada na tela. */
    val found: Map<String, Boolean> = emptyMap(),
    val failure: String? = null,
) {
    val missing: List<String> get() = modo.chaves.filter { found[it] != true }

    /** O que faltou, em rótulo humano (o que dizer ao dono), não a chave interna. */
    val faltaram: List<String>
        get() = missing.map { com.listalocal.core.selectors.SignatureLibrary.para(it)?.rotulo ?: it }
    val ok: Boolean get() = failure == null && missing.isEmpty()

    /** Vale para esta versao do Instagram? Atualizou, confere de novo. */
    fun valeParaVersao(versao: String?): Boolean = ok && versao != null && versao == version

    companion object {
        /**
         * O fim de "Conferir o Instagram". Liga o modo so o que o FLUXO mediu na tela ([achou]): o aprendiz viu
         * todas as telas do caminho (a caixa com uma nota "Para ...", a lista de seguidores) e o que ele achou la
         * nao prova nada. O perfil [aprendido] so e gravado de uma conferencia que passou: um id aprendido numa
         * conferencia que falhou (o Instagram numa tela qualquer) ficava no aparelho e valia no outro modo.
         * Devolve a conferencia e o perfil a gravar (null = nao grava).
         */
        fun fechar(
            modo: Modo, version: String, achou: Map<String, Boolean>, conta: String?, aprendido: PerfilAprendido,
        ): Pair<CompatCheck, PerfilAprendido?> {
            val c = CompatCheck(modo, version, conta?.let(FollowerImport::username), achou)
            val check = if (c.missing.isEmpty()) c else c.copy(failure = "faltou na tela: ${c.faltaram.joinToString()}")
            return check to aprendido.takeIf { check.ok }
        }
    }
}

/** Estado completo exposto para a UI observar. */
data class RunState(
    val phase: RunPhase = RunPhase.IDLE,
    val modo: Modo = Modo.DM,
    /** Modo C: de onde vem cada pessoa (ao vivo). */
    val origem: Origem? = null,
    val plan: List<Batch> = emptyList(),
    val currentListIndex: Int = 0, // 1-based; 0 = nenhum
    val lists: List<ListProgress> = emptyList(),
    val results: List<PersonResult> = emptyList(),
    /** @ da pessoa em andamento (tela do dono). */
    val atual: String = "",
    val message: String = "",
    val needsConfirmation: Boolean = false,
    val appVersion: String = "",
    /** Versão do WhatsApp instalado (lida pelo serviço WA). */
    val appVersionWa: String = "",
    val running: Boolean = false,
    /** Ultima conferencia de cada modo neste aparelho (Instagram). */
    val compat: Map<Modo, CompatCheck> = emptyMap(),
    /** Ultima conferencia de cada modo no WhatsApp. */
    val compatWa: Map<Modo, CompatCheck> = emptyMap(),
    val checkingCompat: Modo? = null,
    /** A conta da operacao em andamento, lida ao vivo no Instagram ao iniciar ("Enviando como @"). */
    val conta: String? = null,
    /** O seletor de contas do Instagram esta aberto, esperando o dono escolher. */
    val trocandoConta: Boolean = false,
    /** Plataforma: true = WhatsApp, false = Instagram. Usado pela UI para adaptar textos. */
    val isWa: Boolean = false,
    /** "Enviar para ate X pessoas" da operacao em andamento. */
    val limite: Int? = null,
    // ── Listas de transmissão (Fase 1 + Fase 2) ────────────────────────────────
    /** Progresso de cada lista de transmissão (modo LISTAS_TRANSMISSAO). */
    val broadcastListas: List<BroadcastListaProgress> = emptyList(),
    /** Fase atual dentro do modo LISTAS_TRANSMISSAO: 1 = criação, 2 = envio. */
    val broadcastFase: Int = 0,
) {
    val totalPessoas: Int get() = plan.sumOf { it.followers.size }
    /** Pessoas que receberam (ou podem ter recebido) nesta operacao: o "12" de "12 de 50". */
    val enviados: Int get() = results.count { it.contaNoLimite }
    val feitos: Int get() = results.size
    fun contar(d: Desfecho): Int = results.count { it.desfecho == d }

    // helpers para o modo broadcast
    val broadcastListasCriadas: Int get() = broadcastListas.count { it.criada }
    val broadcastListasTotal: Int get() = broadcastListas.size
}
