package com.listalocal.service

import com.listalocal.core.ig.Evidence
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.UiNode

/**
 * A conta da operacao e a aberta no Instagram NA HORA de iniciar, lida ao
 * vivo: qualquer conta (pessoal, comercial ou criador), nenhuma fixa no app.
 * O dono pode ter mais de uma no aparelho e trocar entre elas. Nunca a conta
 * de outra conferencia; registros e retomada ficam separados por conta.
 */
object Conta {

    /**
     * Ao iniciar ou retomar, a conta [viva] aberta no Instagram tem de ser a da operacao [daOperacao] (a mostrada no
     * plano, "Enviando como @"): a mensagem e os filtros foram escritos para ela. Outra conta: para sem enviar nada,
     * sem trocar a operacao de conta e sem apagar a salva (antes: passava sozinho para a conta aberta e mandava a
     * mensagem de uma conta como a outra). null = pode seguir; senao, o que dizer ao dono.
     */
    fun naOperacao(daOperacao: String, viva: String): String? =
        if (viva == daOperacao) null
        else "O Instagram está na conta @$viva, mas esta operação é de @$daOperacao (a conta mostrada no plano). " +
            "Nada foi enviado. Volte para @$daOperacao no Instagram e toque em Iniciar ou Retomar de novo; para " +
            "enviar como @$viva, toque em \"Trocar de conta\" ou em \"Conferir o Instagram\" e confira o plano."

    /**
     * Modo A: a conta lida no perfil ao abrir Amigos Proximos [viva] e a da
     * operacao, como no Modo DM. Outra que a da ultima conferencia [conferida]
     * (o dono trocou de conta pelo proprio Instagram) so vale se a abertura
     * acabou de conferir nela tudo o que a conferencia confere ([conferidaAgora]:
     * as mesmas telas e os mesmos papeis, so leitura). Devolve o motivo da
     * parada, ou null. Trocar de conta NO MEIO tira o Instagram da tela Amigos
     * Proximos, e cada marcar exige a busca dessa tela: para ali (CloseFriendsFlow.marcar).
     */
    fun amigosNaConta(conferida: String?, viva: String?, conferidaAgora: Boolean): String? = when {
        viva == null -> SEM_CONTA
        viva != conferida && !conferidaAgora -> naoConferida(viva)
        else -> null
    }

    /**
     * A conta aberta ao iniciar, lida por [ler] (DmFlow.contaAberta). Pausar enquanto ela e lida recusa o
     * toque no Direct e a leitura falha: e a pausa, nao [SEM_CONTA]. Espera o Continuar e le de novo.
     * Parar = [Interrompido] (a operacao fica cancelada, para retomar).
     */
    suspend fun lerAoIniciar(tela: Tela, ler: suspend () -> String?): String {
        while (true) {
            ler()?.let { return it }
            if (!AutomationController.pauseRequested && !AutomationController.cancelRequested) throw Fila.FailedSafe(SEM_CONTA)
            val antes = AutomationController.state.value
            AutomationController.setPhase(RunPhase.PAUSED, "pausado pelo dono")
            while (AutomationController.pauseRequested && !AutomationController.cancelRequested) tela.esperar(400)
            if (AutomationController.cancelRequested) throw Interrompido("cancelado pelo dono")
            AutomationController.setPhase(antes.phase, antes.message)
        }
    }

    /** O que dizer ao dono quando a conta nao pode ser lida ou conferida. */
    const val SEM_CONTA =
        "Não consegui ver qual conta está aberta no Instagram. Abra o Instagram, confira a conta e toque em Iniciar de novo."

    /** A conta do Instagram mudou no meio da operacao: pausa, nada sai como a outra. */
    fun trocada(daOperacao: String, viva: String) =
        "O Instagram mudou para a conta @$viva. Esta operação é de @$daOperacao e pausou: nada é enviado " +
            "como @$viva. Volte para @$daOperacao no Instagram e toque em Continuar."

    fun naoConferida(viva: String) =
        "O Instagram está na conta @$viva e a conferência dessa conta não passou. " +
            "Toque em \"Conferir o Instagram\" na etapa Permissões e depois em Iniciar."

    /**
     * "Trocar de conta": no Instagram, o toque longo na aba Perfil abre o
     * seletor de contas (profile_tab e long-clickable, rodada 1). O app so
     * abre; quem escolhe e o dono. Nenhuma senha e lida nem digitada.
     */
    suspend fun abrirSeletor(tela: Tela, prof: SelectorProfile): Seletor? {
        // aba Perfil pelo papel (id aprendido -> fixo -> assinatura/rotulo ao vivo): num aparelho
        // de id diferente o seletor de contas ainda abre (o toque longo sobe ao ancestral long-clickable).
        fun aba(root: UiNode) = prof.nos(root, "profile_tab").firstOrNull() ?: Evidence.porRotulo(root, prof, "profile_tab")
        tela.abrirInstagram()
        var root: UiNode? = null
        for (i in 0 until 4) {
            root = tela.esperarAte(4_000) { aba(it) != null }
            if (root != null) break
            tela.voltar()
        }
        val r = root ?: return null
        val tab = aba(r) ?: return null
        val antes = logada(r, prof)
        return if (tela.tocarLongo(tab)) Seletor(antes) else null
    }

    /**
     * O seletor de contas abriu. [antes] = a conta aberta AGORA, lida na tela da aba antes do toque longo (null = ali
     * nao da para ler). A troca so vale para outra que ela: com a da ultima conferencia, uma conta ja trocada pelo
     * proprio Instagram parecia "a escolhida" na hora, o seletor fechava sozinho e o dono nao escolhia nada.
     */
    class Seletor(val antes: String?)

    /**
     * A conta LOGADA, so onde a tela prova: a caixa de entrada (aba Direct marcada) ou o perfil proprio (aba Perfil
     * marcada e o menu Opcoes). O titulo de outro perfil e o dono dele.
     */
    private fun logada(r: UiNode, prof: SelectorProfile): String? = when {
        prof.nos(r, "direct_tab").any { it.isSelected } -> Evidence.conta(r, prof)
        prof.nos(r, "profile_tab").any { it.isSelected } && Evidence.porRotulo(r, prof, "options") != null ->
            Evidence.conta(r, prof, "profile_title")
        else -> null
    }

    /**
     * Espera o dono escolher outra conta no seletor: o @ no titulo (perfil ou
     * caixa de entrada) deixa de ser [antes]. So um aviso de que trocou; a
     * conta que vale e a que a conferencia le em seguida. null = nao trocou.
     */
    suspend fun esperarOutra(tela: Tela, prof: SelectorProfile, antes: String?, ms: Int = TROCA_MS): String? {
        val r = tela.esperarAte(ms) { outra(it, prof, antes) != null } ?: return null
        return outra(r, prof, antes)
    }

    private fun outra(r: UiNode, prof: SelectorProfile, antes: String?): String? =
        (Evidence.conta(r, prof, "profile_title") ?: Evidence.conta(r, prof))?.takeIf { it != antes }

    /** O dono tem este tempo para escolher a conta no seletor. */
    const val TROCA_MS = 90_000
}
