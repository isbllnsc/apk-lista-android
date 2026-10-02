package com.listalocal.core.state

import com.listalocal.core.state.DmState.*

/**
 * Estados do envio de UMA mensagem direta a UMA pessoa. A fronteira e o
 * COMMIT: o "vou enviar" gravado no disco antes do toque em Enviar.
 *  - antes do COMMIT, qualquer imprevisto e FALHA: nada foi enviado e a
 *    pessoa pode entrar numa operacao futura;
 *  - depois do COMMIT, sem evidencia de envio e INCERTO: conta como falha e
 *    nunca e reenviado (a mensagem pode ter chegado).
 */
enum class DmState {
    PRONTO,
    ABRINDO,
    CONVERSA_PROVADA,
    TEXTO_CONFERIDO,
    COMMIT,

    // Finais
    ENVIADO,
    NAO_ENCONTRADO,
    AMBIGUO,
    INDISPONIVEL,
    NAO_SEGUE,
    PEDIU_PARA_PARAR,
    FALHA,
    INCERTO;

    val isTerminal: Boolean get() = this in FINAIS

    companion object {
        val FINAIS = setOf(ENVIADO, NAO_ENCONTRADO, AMBIGUO, INDISPONIVEL, NAO_SEGUE, PEDIU_PARA_PARAR, FALHA, INCERTO)
    }
}

sealed interface DmEvent {
    data object Abrir : DmEvent
    data object ConversaProvada : DmEvent
    data object NaoEncontrado : DmEvent
    data object Ambiguo : DmEvent
    data object Indisponivel : DmEvent
    /** O cartao da conversa diz que a pessoa nao segue a conta. */
    data object NaoSegue : DmEvent
    data object PediuParaParar : DmEvent
    data object TextoConferido : DmEvent
    data object Commit : DmEvent
    data object Enviado : DmEvent
    /** Tela desconhecida, prova que nao veio, cancelamento, prazo: o imprevisto. */
    data object Inesperado : DmEvent
    /**
     * O toque em Enviar foi recusado e a tela provou que nada saiu (o mesmo texto no campo, o Enviar ainda la, nenhuma
     * bolha nova). A unica saida do COMMIT para FALHA.
     */
    data object ToqueRecusado : DmEvent
}

/**
 * Maquina pura, dirigida por eventos. Par (estado, evento) nao previsto vira
 * FALHA antes do COMMIT e INCERTO depois dele. Estado final nao reativa.
 */
class DmStateMachine {

    var state: DmState = PRONTO
        private set

    /** Trilha para diagnostico. So estados: nunca o @ nem o texto. */
    private val _trail = mutableListOf(PRONTO)
    val trail: List<DmState> get() = _trail.toList()

    fun transition(event: DmEvent): DmState {
        if (state.isTerminal) return state
        val next = compute(state, event)
        state = next
        _trail += next
        return next
    }

    private fun compute(current: DmState, event: DmEvent): DmState = when (current) {
        PRONTO -> when (event) {
            DmEvent.Abrir -> ABRINDO
            else -> FALHA
        }
        ABRINDO -> when (event) {
            DmEvent.ConversaProvada -> CONVERSA_PROVADA
            DmEvent.NaoEncontrado -> NAO_ENCONTRADO
            DmEvent.Ambiguo -> AMBIGUO
            DmEvent.Indisponivel -> INDISPONIVEL
            else -> FALHA
        }
        CONVERSA_PROVADA -> when (event) {
            DmEvent.TextoConferido -> TEXTO_CONFERIDO
            DmEvent.PediuParaParar -> PEDIU_PARA_PARAR
            DmEvent.NaoSegue -> NAO_SEGUE
            DmEvent.Indisponivel -> INDISPONIVEL
            else -> FALHA
        }
        TEXTO_CONFERIDO -> when (event) {
            DmEvent.Commit -> COMMIT
            else -> FALHA
        }
        COMMIT -> when (event) {
            DmEvent.Enviado -> ENVIADO
            DmEvent.ToqueRecusado -> FALHA
            else -> INCERTO
        }
        else -> current
    }
}
