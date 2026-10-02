package com.listalocal.data

import android.content.Context
import com.listalocal.service.Desfecho
import com.listalocal.service.PersonResult
import com.listalocal.service.Registro

/**
 * Desfecho por pessoa, por conta remetente, gravado no aparelho. E o que
 * impede um segundo envio: o "vou enviar" e gravado (commit SINCRONO) antes do
 * toque em Enviar; se o app morrer depois dele, a pessoa fica INCERTO e nunca
 * mais entra numa operacao desta conta (nem na retomada).
 *
 * Fica so no aparelho: sem backup em nuvem (data_extraction_rules.xml) e sem
 * permissao de rede. Some ao desinstalar ou limpar os dados do app.
 */
class Outcomes(context: Context, conta: String) : Registro {

    private val prefs = context.applicationContext
        .getSharedPreferences("desfechos_$conta", Context.MODE_PRIVATE)

    /** A linha gravada antes do "vou enviar" desta tentativa (so na memoria): o desfecho final decide sobre ela. */
    private val antesDoCommit = HashMap<String, String?>()

    override fun marcarCommit(username: String): Boolean {
        antesDoCommit[username] = prefs.getString(username, null)
        return prefs.edit().putString(username, linha(Desfecho.INCERTO, "vou enviar")).commit()
    }

    /**
     * Assincrono (apply): na memoria na hora, no disco logo depois. Quem pode ter
     * recebido ja esta INCERTO no disco ([marcarCommit], sincrono); um pedido de
     * parar perdido num desligamento e relido na conversa. Nunca rebaixa ([manter]).
     */
    override fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int>) {
        val antes = if (antesDoCommit.containsKey(username)) antesDoCommit.remove(username) else prefs.getString(username, null)
        prefs.edit().putString(username, manter(antes, desfecho) ?: linha(desfecho, motivo, enviadas)).apply()
    }

    /** "DESFECHO|quando|motivo"; com mensagens tocadas, "DESFECHO|quando|1,2|motivo". */
    private fun linha(d: Desfecho, motivo: String, enviadas: List<Int> = emptyList()): String {
        val m = motivo.replace('|', '/').take(200)
        return if (enviadas.isEmpty()) "${d.name}|${System.currentTimeMillis()}|$m"
        else "${d.name}|${System.currentTimeMillis()}|${enviadas.joinToString(",")}|$m"
    }

    /** Todos os desfechos gravados desta conta. Registro ilegivel conta como INCERTO (falha fechada). */
    fun todos(): Map<String, Desfecho> = prefs.all.mapValues { (_, v) ->
        runCatching { Desfecho.valueOf(v.toString().substringBefore('|')) }.getOrDefault(Desfecho.INCERTO)
    }

    /**
     * Recebeu, pode ter recebido ou pediu para parar: fora de qualquer nova
     * operacao. Sem [pularJaRecebeu] ("Pular quem ja recebeu" desligado), quem
     * recebeu com certeza volta; INCERTO e pedido de parar, nunca.
     */
    fun bloqueados(pularJaRecebeu: Boolean = true): Set<String> =
        todos().filterValues { it.bloqueiaNovoEnvio && (pularJaRecebeu || it != Desfecho.ENVIADO) }.keys

    /**
     * Desfechos gravados desde [inicio] (a operacao em andamento), para a
     * retomada contar e pular quem ja foi feito. Sem nome: so o @ e gravado.
     */
    fun desde(inicio: Long): List<PersonResult> = prefs.all.mapNotNull { (u, v) ->
        val quando = v.toString().split('|').getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        if (quando < inicio) return@mapNotNull null
        ler(u, v.toString()) to quando
    }.sortedBy { it.second }.map { it.first }

    companion object {
        /**
         * Nunca rebaixa: quem recebeu, pode ter recebido ou pediu para parar continua assim, mesmo com um desfecho
         * depois que nao bloqueia. Antes, uma FALHA numa operacao com "Pular quem ja recebeu" desligado apagava o
         * ENVIADO, e a operacao seguinte (com a opcao ligada) mandava de novo. [antes] = a linha gravada antes desta
         * tentativa. Devolve a linha a manter, ou null para gravar a nova.
         */
        fun manter(antes: String?, novo: Desfecho): String? =
            antes?.takeIf { !novo.bloqueiaNovoEnvio && ler("", it).desfecho.bloqueiaNovoEnvio }

        /** Uma linha gravada de volta em desfecho. O motivo nunca tem '|': 4 partes = com as mensagens tocadas. */
        fun ler(u: String, v: String): PersonResult {
            val partes = v.split('|', limit = 4)
            val d = runCatching { Desfecho.valueOf(partes[0]) }.getOrDefault(Desfecho.INCERTO)
            val enviadas = if (partes.size == 4) partes[2].split(',').mapNotNull(String::toIntOrNull) else emptyList()
            return PersonResult(u, u, 0, d, partes.last().takeIf { partes.size >= 3 }.orEmpty(), enviadas)
        }
    }

    /**
     * Quando saiu (ou pode ter saido) o ultimo envio desta conta, no relogio de parede (a linha e gravada logo depois
     * do toque: um pouco depois dele, nunca antes). null = nenhum.
     */
    fun ultimoEnvio(): Long? = prefs.all.mapNotNull { (u, v) ->
        v.toString().takeIf { ler(u, it).contaNoLimite }?.split('|')?.getOrNull(1)?.toLongOrNull()
    }.maxOrNull()

    fun pediramParaParar(): Set<String> = todos().filterValues { it == Desfecho.PEDIU_PARA_PARAR }.keys
}
