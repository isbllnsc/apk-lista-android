package com.listalocal.core.selectors

import com.listalocal.core.tree.Bounds
import com.listalocal.core.tree.UiNode

/**
 * Onde o controle costuma ficar na tela: topo, rodape (campo) ou, dentro do topo, a barra de titulo
 * ([CABECALHO]: o @ da conta; o numero "15posts" do perfil e as notas da caixa ficam abaixo dela).
 */
enum class Regiao { TOPO, RODAPE, QUALQUER, CABECALHO }

/**
 * Assinatura de um PAPEL do Instagram (campo de mensagem, Enviar, busca, linha
 * de seguidor, "Nova mensagem"...), combinando vários sinais em vez de um id
 * fixo. Assim a auto-calibração acha o mesmo controle em outro celular, versão
 * ou idioma. Ordem de confiança preservada: um id conhecido é só MAIS UM sinal
 * (bônus), nunca obrigatório.
 *
 * Sinais que são PORTÃO (o candidato é recusado se não passar) e sinais que são
 * BÔNUS (somam pontos; o candidato precisa passar de [minimo] para valer, e o de
 * maior pontuação vence). Recusar o controle errado é tão importante quanto
 * achar o certo: os portões (classe, estado, vizinhança, região oposta) fazem
 * isso.
 */
data class RoleSignature(
    val key: String,
    /** Rótulo humano do papel, para dizer ao dono o que faltou calibrar. */
    val rotulo: String,
    /** PORTÃO: className casa esta regex (ex.: EditText, Button). */
    val classe: Regex? = null,
    /** PORTÃO: o className parece EditText (campo de texto). */
    val editavel: Boolean = false,
    /** PORTÃO: isClickable == este valor. */
    val clicavel: Boolean? = null,
    /** PORTÃO: isScrollable == este valor. */
    val rolavel: Boolean? = null,
    /** PORTÃO: isSelected == este valor (aba marcada, filtro marcado). */
    val selecionado: Boolean? = null,
    /** Regex multi-idioma no texto ou content-desc (nunca lista fechada). */
    val texto: Regex? = null,
    /** PORTÃO: algum rótulo do no casa [texto]. Sem isso, [texto] é só bônus. */
    val textoObrigatorio: Boolean = false,
    /** BÔNUS/PORTÃO: a região da tela. Com medida (bounds), a região oposta RECUSA. */
    val regiao: Regiao = Regiao.QUALQUER,
    /** BÔNUS: o id conhecido do perfil fixo. Casar dá pontos; nunca é exigido. */
    val idConhecido: String? = null,
    /** PORTÃO: algum ancestral satisfaz (vizinhança para cima; ex.: dentro da barra do campo). */
    val dentroDe: ((UiNode) -> Boolean)? = null,
    /** PORTÃO: NENHUM ancestral satisfaz (ex.: o Enviar de verdade não fica na lista de mensagens). */
    val fora: ((UiNode) -> Boolean)? = null,
    /** BÔNUS: algum descendente satisfaz (vizinhança para baixo; ex.: a linha tem um @). */
    val contem: ((UiNode) -> Boolean)? = null,
    /**
     * PORTÃO: nunca um EditText, e na mesma faixa de altura de um EditText da tela (a barra do campo: o Enviar fica
     * ao lado do campo). Por posição, não por parentesco: na árvore real o Enviar é primo do campo
     * (row_thread_composer_buttons_container ao lado de composer_content_container), e na leitura do serviço os
     * layouts somem. Sem medida, recusa. Com texto digitado com "enviar", o campo nunca vira o Enviar.
     */
    val aoLadoDoCampo: Boolean = false,
    /** BÔNUS (+2, como a região): o nó tem o foco de entrada. O campo com o teclado aberto sobe para o meio da tela. */
    val focado: Boolean = false,
    /**
     * PORTÃO: o no casa [texto] OU tem o [idConhecido]. Para o Enviar: sem o
     * rótulo "Enviar" e sem o id conhecido, um botão qualquer da barra (câmera,
     * galeria) não pode virar Enviar. Não achar e parar é mais seguro que chutar.
     */
    val exigeTextoOuId: Boolean = false,
    /**
     * PORTÃO (+1): uma lista vertical, com 2 ou mais filhos visíveis empilhados um embaixo do outro. As notas e os
     * filtros da caixa (lado a lado) e os ViewPager (uma página visível) não são. Sem medida, recusa.
     */
    val listaVertical: Boolean = false,
    /** Pontuação mínima para o candidato valer. */
    val minimo: Int = 2,
)

/** O que a calibração aprendeu de um papel na tela: o id de verdade e os rótulos. */
data class Descoberta(val key: String, val id: String?, val rotulos: List<String>, val pontos: Int)

/**
 * Casa uma [RoleSignature] contra uma árvore de acessibilidade (funções puras:
 * o serviço passa a cópia da árvore, os testes passam FakeNode). Não toca em
 * nada; só decide QUAL no é o papel. O toque continua por no, no chamador.
 */
object RoleMatcher {

    /** @-de-usuário: o texto INTEIRO parece um @ (nunca por pedaço de um nome). */
    val ARROBA = Regex("^@?[A-Za-z0-9._]{1,30}$")

    fun ehEditText(n: UiNode): Boolean = n.className?.contains("EditText", ignoreCase = true) == true

    private fun rotulos(n: UiNode): List<String> =
        listOfNotNull(n.text, n.contentDescription).map { it.trim() }.filter { it.isNotEmpty() }

    private fun casaTexto(n: UiNode, re: Regex): Boolean = rotulos(n).any { re.containsMatchIn(it) }

    private fun idSuffix(n: UiNode): String? {
        val v = n.viewIdResourceName ?: return null
        val s = if (":id/" in v) v.substringAfter(":id/") else v
        return s.takeIf { it.isNotBlank() }
    }

    /** Altura da tela em pixels: a dada, ou o maior fundo medido na árvore. */
    private fun altura(root: UiNode, dado: Int?): Int? =
        dado?.takeIf { it > 0 } ?: root.walk().mapNotNull { it.bounds?.baixo }.maxOrNull()?.takeIf { it > 0 }

    /** A região do no pela posição, ou null sem medida. O meio não é topo nem rodapé. */
    private fun regiaoDe(n: UiNode, alturaTela: Int?): Regiao? {
        val b = n.bounds ?: return null
        val h = alturaTela ?: return null
        val f = b.centroY.toDouble() / h
        return when {
            f <= 0.10 -> Regiao.CABECALHO // a barra de titulo: 0,06 no A14 (65-223 de 2408)
            f <= 0.34 -> Regiao.TOPO
            f >= 0.60 -> Regiao.RODAPE
            else -> Regiao.QUALQUER
        }
    }

    /** Cada no com a lista dos seus ancestrais (raiz primeiro), em profundidade. */
    private fun comAncestrais(root: UiNode): Sequence<Pair<UiNode, List<UiNode>>> {
        fun rec(n: UiNode, anc: List<UiNode>): Sequence<Pair<UiNode, List<UiNode>>> = sequence {
            yield(n to anc)
            val proximos = anc + n
            n.children.forEach { yieldAll(rec(it, proximos)) }
        }
        return rec(root, emptyList())
    }

    /** As faixas de altura dos campos de texto da tela (para [RoleSignature.aoLadoDoCampo]). */
    private fun campos(root: UiNode, sig: RoleSignature): List<Bounds> =
        if (!sig.aoLadoDoCampo) emptyList() else root.walk().filter { ehEditText(it) && it.isVisibleToUser }.mapNotNull { it.bounds }.toList()

    /** 2 ou mais filhos visiveis medidos, cada um comecando onde o de cima termina (sem ficar lado a lado). */
    private fun empilhados(n: UiNode): Boolean {
        val filhos = n.children.filter { it.isVisibleToUser }.mapNotNull { it.bounds }.sortedBy { it.topo }
        return filhos.size >= 2 && filhos.zipWithNext().all { (a, b) -> b.topo >= a.baixo - 2 }
    }

    /** Pontos do candidato, ou null se um portão recusou. */
    private fun pontuar(n: UiNode, anc: List<UiNode>, sig: RoleSignature, alturaTela: Int?, campos: List<Bounds>): Int? {
        if (!n.isVisibleToUser) return null
        // ---- portões ----
        sig.classe?.let { if (n.className?.let(it::containsMatchIn) != true) return null }
        if (sig.editavel && !ehEditText(n)) return null
        sig.clicavel?.let { if (n.isClickable != it) return null }
        sig.rolavel?.let { if (n.isScrollable != it) return null }
        sig.selecionado?.let { if (n.isSelected != it) return null }
        if (sig.textoObrigatorio) {
            val re = sig.texto ?: return null
            if (!casaTexto(n, re)) return null
        }
        sig.dentroDe?.let { pred -> if (anc.none(pred)) return null }
        sig.fora?.let { pred -> if (anc.any(pred)) return null }
        if (sig.aoLadoDoCampo) {
            val b = n.bounds ?: return null
            if (ehEditText(n) || campos.none { b.centroY in it.topo..it.baixo }) return null
        }
        if (sig.exigeTextoOuId) {
            val porTexto = sig.texto?.let { casaTexto(n, it) } == true
            val porId = sig.idConhecido != null && idSuffix(n) == sig.idConhecido
            if (!porTexto && !porId) return null
        }
        if (sig.listaVertical && !empilhados(n)) return null
        val reg = regiaoDe(n, alturaTela)
        // A barra de titulo e parte do topo.
        val naRegiao = reg == sig.regiao || (sig.regiao == Regiao.TOPO && reg == Regiao.CABECALHO)
        // Região oposta MEDIDA recusa: o campo do rodapé nunca é a busca do topo.
        if (sig.regiao != Regiao.QUALQUER && reg != null && reg != Regiao.QUALQUER && !naRegiao) return null
        // ---- pontos ----
        var p = 0
        if (sig.idConhecido != null && idSuffix(n) == sig.idConhecido) p += 3
        if (sig.texto != null && casaTexto(n, sig.texto)) p += 2
        if (sig.regiao != Regiao.QUALQUER && naRegiao) p += 2
        if (sig.editavel) p += 1
        if (sig.classe != null) p += 1
        if (sig.clicavel != null) p += 1
        if (sig.rolavel != null) p += 1
        if (sig.selecionado != null) p += 1
        if (sig.dentroDe != null) p += 1
        if (sig.aoLadoDoCampo) p += 1
        if (sig.listaVertical) p += 1
        if (sig.focado && n.isFocused) p += 2
        if (sig.contem?.let { pred -> n.walk().drop(1).any(pred) } == true) p += 1
        return p.takeIf { it >= sig.minimo }
    }

    /** Todos os nós que servem, do melhor para o pior (pontos, depois ordem na árvore). */
    fun todos(root: UiNode, sig: RoleSignature, alturaTela: Int? = null): List<UiNode> {
        val h = altura(root, alturaTela)
        val c = campos(root, sig)
        return comAncestrais(root)
            .mapNotNull { (n, anc) -> pontuar(n, anc, sig, h, c)?.let { n to it } }
            .toList()
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<Pair<UiNode, Int>>> { it.value.second }.thenBy { it.index })
            .map { it.value.first }
    }

    /** O melhor no para o papel, ou null se nenhum passou (sem chutar). */
    fun achar(root: UiNode, sig: RoleSignature, alturaTela: Int? = null): UiNode? {
        val h = altura(root, alturaTela)
        val c = campos(root, sig)
        var melhor: UiNode? = null
        var melhorP = Int.MIN_VALUE
        for ((n, anc) in comAncestrais(root)) {
            val p = pontuar(n, anc, sig, h, c) ?: continue
            if (p > melhorP) { melhorP = p; melhor = n }
        }
        return melhor
    }

    /** O que aprender do papel nesta tela: o id real e os rótulos do melhor no. */
    fun descobrir(root: UiNode, sig: RoleSignature, alturaTela: Int? = null): Descoberta? {
        val h = altura(root, alturaTela)
        val c = campos(root, sig)
        var melhor: UiNode? = null
        var melhorP = Int.MIN_VALUE
        for ((n, anc) in comAncestrais(root)) {
            val p = pontuar(n, anc, sig, h, c) ?: continue
            if (p > melhorP) { melhorP = p; melhor = n }
        }
        val no = melhor ?: return null
        return Descoberta(sig.key, idSuffix(no), rotulos(no), melhorP)
    }
}
