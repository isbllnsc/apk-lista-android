package com.listalocal.core.selectors

import com.listalocal.core.tree.UiNode

/**
 * O que a auto-calibracao aprendeu de um modo NESTE aparelho, por (versao do
 * Instagram + idioma + modo). Fica so no aparelho (DataStore/SharedPreferences,
 * nunca em rede; ver data/PerfisAprendidos). [ids] = papel -> id de verdade
 * achado na tela; [descricoes] = os rotulos de verdade (idioma do aparelho).
 * [faltando] = papeis obrigatorios do modo que a assinatura nao achou: fica
 * bloqueado e o app diz exatamente quais foram, para o dono calibrar.
 */
data class PerfilAprendido(
    val versao: String,
    val idioma: String,
    val modo: String,
    val quando: Long,
    val ids: Map<String, String> = emptyMap(),
    val descricoes: Map<String, List<String>> = emptyMap(),
    val encontrados: List<String> = emptyList(),
    val faltando: List<String> = emptyList(),
) {
    /** Achou todos os papeis obrigatorios do modo: pode liberar. */
    val completo: Boolean get() = faltando.isEmpty()

    /**
     * Este perfil com os ids/rotulos aprendidos NOUTROS modos (mesma versao +
     * idioma) por baixo; os proprios vencem. Um papel compartilhado (ex.:
     * profile_tab, aprendido ao calibrar Amigos Proximos) passa a servir tambem
     * ao Modo DM + Origem "Seus seguidores", em vez de so por assinatura ao vivo.
     */
    fun comIdsDe(outros: List<PerfilAprendido>): PerfilAprendido {
        if (outros.isEmpty()) return this
        val idsBase = outros.fold(emptyMap<String, String>()) { acc, p -> acc + p.ids }
        val descBase = outros.fold(emptyMap<String, List<String>>()) { acc, p -> acc + p.descricoes }
        return copy(ids = idsBase + ids, descricoes = descBase + descricoes)
    }
}

/**
 * Aprende os controles do Instagram na tela de verdade: para cada assinatura
 * ([alvos]), acha o melhor no e guarda o id/rotulos que descobriu. Ve varias
 * telas do modo ([viu]) e mantem, por papel, a melhor descoberta. So leitura:
 * nao toca em nada. Funcao pura (recebe a copia da arvore); testavel sem aparelho.
 */
class Aprendiz(private val alvos: List<RoleSignature>) {

    private val melhores = LinkedHashMap<String, Descoberta>()

    /**
     * Uma tela do modo. [alturaTela] em pixels, se conhecida (para a regiao).
     * [excluir] = papeis a NAO aprender nesta tela: os papeis de linha/@ (cf_row,
     * cf_username) so devem ser aprendidos na tela certa (o seletor de Amigos
     * Proximos), senao uma lista de posts do Feed (tambem clicavel, em lista, com
     * @) casaria a assinatura fraca deles e roubaria o slot (o primeiro visto vence
     * no empate). O chamador passa a tela e o escopo certo dela.
     */
    fun viu(root: UiNode, alturaTela: Int? = null, excluir: Set<String> = emptySet()) {
        for (sig in alvos) {
            if (sig.key in excluir) continue
            val d = RoleMatcher.descobrir(root, sig, alturaTela) ?: continue
            val atual = melhores[sig.key]
            if (atual == null || d.pontos > atual.pontos) melhores[sig.key] = d
        }
    }

    val encontrados: Set<String> get() = melhores.keys.toSet()

    /**
     * O perfil aprendido. [obrigatorios] = os papeis que o modo exige para
     * liberar (os que faltarem entram em [PerfilAprendido.faltando]).
     */
    fun perfil(versao: String, idioma: String, modo: String, obrigatorios: List<String>, quando: Long): PerfilAprendido {
        val ids = melhores.values.mapNotNull { d -> d.id?.let { d.key to it } }.toMap()
        // Guarda o id (não é pessoal), mas NUNCA o texto de papéis cujo rótulo é @/nome
        // de terceiro (cf_username, cf_row...), nem um rótulo que não seja o do controle: o perfil no
        // aparelho não grava dado de ninguém.
        val descricoes = melhores.values
            .filter { it.key !in SignatureLibrary.SEM_ROTULO }
            .mapNotNull { d -> d.rotulos.filter { SignatureLibrary.rotuloDeVocabulario(d.key, it) }.takeIf { it.isNotEmpty() }?.let { d.key to it } }
            .toMap()
        val faltando = obrigatorios.filter { it !in melhores.keys }
        return PerfilAprendido(versao, idioma, modo, quando, ids, descricoes, melhores.keys.toList(), faltando)
    }
}
