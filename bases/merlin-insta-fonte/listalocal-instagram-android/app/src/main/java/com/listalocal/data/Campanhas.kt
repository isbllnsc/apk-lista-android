package com.listalocal.data

import android.content.Context
import com.listalocal.service.Campanha
import com.listalocal.service.ModoMensagens
import com.listalocal.service.Origem
import com.listalocal.service.Velocidade
import org.json.JSONArray
import org.json.JSONObject

/**
 * A operacao do modo C em andamento, uma por conta, para retomar do ponto
 * salvo: a escolha do dono e o ponto de parada. So no aparelho (sem backup,
 * sem rede). Encerrada no fim da lista ou quando o dono comeca outra.
 */
class Campanhas(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("campanhas", Context.MODE_PRIVATE)

    fun ler(conta: String): Campanha? = prefs.getString(conta, null)?.let { runCatching { de(JSONObject(it)) }.getOrNull() }

    /**
     * Assincrono (apply): na memoria na hora, no disco logo depois, sem travar
     * a tela. O que protege contra reenvio e o desfecho (Outcomes, sincrono).
     */
    fun salvar(c: Campanha) {
        prefs.edit().putString(c.conta, para(c).toString()).apply()
    }

    /** O ultimo processado. Assincrono: o que protege contra reenvio e o desfecho (Outcomes), nao isto. */
    fun ponto(conta: String, ultimo: String) {
        val c = ler(conta) ?: return
        prefs.edit().putString(conta, para(c.copy(ultimo = ultimo)).toString()).apply()
    }

    fun encerrar(conta: String) {
        prefs.edit().remove(conta).apply()
    }

    private fun para(c: Campanha) = JSONObject()
        .put("conta", c.conta).put("origem", c.origem.name).put("mensagens", JSONArray(c.mensagens))
        .put("velocidade", c.velocidade.name).put("naoEnviar", JSONArray(c.naoEnviar.toList()))
        .put("soPara", JSONArray(c.soPara.toList())).put("pularComerciais", c.pularComerciais)
        .put("baseLegal", c.baseLegal).put("inicio", c.inicio).put("ultimo", c.ultimo ?: "")
        .put("modoMensagens", c.modoMensagens.name).put("limite", c.limite ?: 0).put("aPartirDe", c.aPartirDe ?: "")
        .put("pularJaRecebeu", c.pularJaRecebeu).put("pararAs", c.pararAs ?: -1)

    private fun de(o: JSONObject): Campanha {
        fun em(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
        fun lista(k: String) = em(k).toSet()
        return Campanha(
            conta = o.getString("conta"),
            origem = Origem.valueOf(o.getString("origem")),
            // 0.1.1 gravava uma mensagem so, em "texto".
            mensagens = em("mensagens").ifEmpty { listOf(o.getString("texto")) },
            velocidade = Velocidade.valueOf(o.getString("velocidade")),
            naoEnviar = lista("naoEnviar"),
            soPara = lista("soPara"),
            pularComerciais = o.optBoolean("pularComerciais"),
            baseLegal = o.optString("baseLegal"),
            inicio = o.getLong("inicio"),
            ultimo = o.optString("ultimo").ifEmpty { null },
            modoMensagens = runCatching { ModoMensagens.valueOf(o.optString("modoMensagens")) }.getOrDefault(ModoMensagens.REVEZAR),
            limite = o.optInt("limite").takeIf { it > 0 },
            aPartirDe = o.optString("aPartirDe").ifEmpty { null },
            pularJaRecebeu = o.optBoolean("pularJaRecebeu", true),
            pararAs = o.optInt("pararAs", -1).takeIf { it >= 0 },
        )
    }
}
