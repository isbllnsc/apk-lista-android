package com.listalocal.data

import android.content.Context
import com.listalocal.core.selectors.PerfilAprendido
import org.json.JSONArray
import org.json.JSONObject

/**
 * Os perfis que a auto-calibracao aprendeu, um por (versao do Instagram +
 * idioma + modo), gravados SO no aparelho (sem backup, sem rede, como Campanhas
 * e Outcomes). Reconferir por cima sobrescreve. Some ao desinstalar ou limpar
 * os dados do app.
 */
class PerfisAprendidos(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("perfis_aprendidos", Context.MODE_PRIVATE)

    init {
        // Perfis de antes do [ESQUEMA] foram gravados mesmo com a conferencia falhando e podem ter ids errados
        // (a foto de um seguidor como aba) e rotulos de terceiros: saem do aparelho. A proxima conferencia regrava.
        val velhas = prefs.all.keys.filterNot { it.startsWith(ESQUEMA) }
        if (velhas.isNotEmpty()) prefs.edit().apply { velhas.forEach(::remove) }.apply()
    }

    private fun chave(versao: String, idioma: String, modo: String) = "$ESQUEMA$versao|$idioma|$modo"

    private companion object {
        const val ESQUEMA = "2|"
    }

    fun ler(versao: String, idioma: String, modo: String): PerfilAprendido? =
        prefs.getString(chave(versao, idioma, modo), null)?.let { runCatching { de(JSONObject(it)) }.getOrNull() }

    /** Assincrono (apply): a calibracao nao trava a tela; nada de reenvio depende disto. */
    fun salvar(p: PerfilAprendido) {
        prefs.edit().putString(chave(p.versao, p.idioma, p.modo), para(p).toString()).apply()
    }

    private fun para(p: PerfilAprendido) = JSONObject()
        .put("versao", p.versao).put("idioma", p.idioma).put("modo", p.modo).put("quando", p.quando)
        .put("ids", JSONObject(p.ids as Map<*, *>))
        .put("descricoes", JSONObject(p.descricoes.mapValues { JSONArray(it.value) } as Map<*, *>))
        .put("encontrados", JSONArray(p.encontrados)).put("faltando", JSONArray(p.faltando))

    private fun de(o: JSONObject): PerfilAprendido {
        fun lista(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
        fun mapaStr(k: String): Map<String, String> {
            val obj = o.optJSONObject(k) ?: return emptyMap()
            return obj.keys().asSequence().associateWith(obj::getString)
        }
        fun mapaLista(k: String): Map<String, List<String>> {
            val obj = o.optJSONObject(k) ?: return emptyMap()
            return obj.keys().asSequence().associateWith { key ->
                obj.getJSONArray(key).let { a -> (0 until a.length()).map(a::getString) }
            }
        }
        return PerfilAprendido(
            versao = o.getString("versao"), idioma = o.getString("idioma"), modo = o.getString("modo"),
            quando = o.optLong("quando"),
            ids = mapaStr("ids"), descricoes = mapaLista("descricoes"),
            encontrados = lista("encontrados"), faltando = lista("faltando"),
        )
    }
}
