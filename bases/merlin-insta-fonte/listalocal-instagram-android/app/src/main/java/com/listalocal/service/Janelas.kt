package com.listalocal.service

/**
 * Qual janela do Instagram ler. Kotlin puro: no servico T e o
 * AccessibilityNodeInfo da raiz de cada janela; nos testes, arvores falsas.
 *
 * Por que: neste celular a "janela ativa" fica presa na atividade principal
 * enquanto a conversa (ModalActivity) esta na frente (INSTAGRAM-APP-REAL.md,
 * 0.1). `rootInActiveWindow` devolve null ou a caixa de entrada VELHA. Por isso:
 *  1. as janelas de aplicativo de service.windows (flagRetrieveInteractiveWindows),
 *     da maior camada para a menor. Se a de cima nao e do Instagram (outro app
 *     na frente), nada e lido;
 *  2. a de cima SEM raiz (a ModalActivity neste aparelho: o uiautomator recebe
 *     "null root node" dela, 0.1): so a reserva da MESMA janela (a raiz da fonte
 *     do ultimo evento dela). Nunca a de outra janela: a principal atras e velha,
 *     e sem raiz nao da para saber se nao e outro app;
 *  3. entre as do Instagram (seguidas, a partir do topo), a que tem a conversa
 *     (direct_thread_header ou row_thread_composer_edittext). Junto vao as do
 *     Instagram por cima dela, para um dialogo de aviso ("Tente novamente mais
 *     tarde") ser visto, menos a atividade principal (barra de abas), que e
 *     velha;
 *  4. nenhuma com a conversa: uma reserva que tenha a conversa
 *     (rootInActiveWindow, raiz da fonte do ultimo evento TYPE_WINDOW_CONTENT_CHANGED);
 *     senao, a do Instagram de cima (Amigos Proximos, Nova mensagem, caixa de entrada);
 *  5. service.windows vazio (a flag nao valeu): so as reservas, a que tem a
 *     conversa primeiro.
 */
internal object Janelas {

    /**
     * Uma janela de service.windows. [pacote] e o da raiz (null = ilegivel); [app] = TYPE_APPLICATION;
     * [id] = AccessibilityWindowInfo.id.
     */
    class Janela<T : Any>(val raiz: T?, val pacote: String?, val app: Boolean, val camada: Int, val id: Int = -1)

    /** Uma raiz de reserva, o pacote dela e a janela de onde veio (null = nao se sabe). */
    class Reserva<T : Any>(val raiz: T, val pacote: String?, val janela: Int? = null)

    /** Raizes a ler, a principal primeiro. Vazio = nada do Instagram legivel na frente. */
    fun <T : Any> escolher(
        janelas: List<Janela<T>>,
        alvo: String,
        temConversa: (T) -> Boolean,
        ehPrincipal: (T) -> Boolean,
        reservas: () -> List<Reserva<T>>,
    ): List<T> {
        val doAlvo = { reservas().filter { it.pacote == alvo } }
        val daReserva = { doAlvo().map { it.raiz } }
        val apps = janelas.filter { it.app }.sortedByDescending { it.camada }
        if (apps.isEmpty()) {
            val r = daReserva()
            return listOfNotNull(r.firstOrNull(temConversa) ?: r.firstOrNull())
        }
        val topo = apps.first()
        if (topo.raiz == null) {
            val r = doAlvo().filter { it.janela == topo.id }.map { it.raiz }
            return listOfNotNull(r.firstOrNull(temConversa) ?: r.firstOrNull())
        }
        val ig = apps.takeWhile { it.pacote == alvo }.mapNotNull { it.raiz }
        if (ig.isEmpty()) return emptyList()
        val i = ig.indexOfFirst(temConversa)
        if (i < 0) return listOf(daReserva().firstOrNull(temConversa) ?: ig.first())
        return listOf(ig[i]) + ig.take(i).filterNot(ehPrincipal)
    }
}
