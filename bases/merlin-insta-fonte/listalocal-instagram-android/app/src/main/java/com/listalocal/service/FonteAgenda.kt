package com.listalocal.service

import android.content.Context
import android.util.Log
import com.listalocal.core.contacts.Agenda
import com.listalocal.core.contacts.Contato
import com.listalocal.data.ContactsRepository
import com.listalocal.service.Campanha


/**
 * Fonte de destinatários para o WhatsApp: lê a agenda do celular via
 * [ContactsRepository], normaliza os números para E.164 e os serve em ordem.
 *
 * Implementa [Fonte] para ser compatível com [Fila.aoVivo].
 */
class FonteAgenda(
    private val context: Context,
    private val campanha: Campanha,
) : Fonte {

    private var contatos: List<Contato> = emptyList()
    private var cursor: Int = 0
    private var contaLida: String? = null

    override suspend fun abrir(): String? {
        val raw = ContactsRepository.carregar(context)
        val opcoes = Agenda.OpcoesAgenda(
            naoEnviar = campanha.naoEnviar.toSet(),
            soPara = campanha.soPara.toSet(),
        )
        contatos = Agenda.plano(raw.map {
            com.listalocal.core.contacts.RawContactEntry(it.id, it.name, it.phones)
        }, opcoes).fila
        cursor = 0
        contaLida = campanha.conta
        Log.i(TAG, "FonteAgenda: ${contatos.size} contatos carregados")
        return contaLida
    }

    override suspend fun proximas(jaFeitas: (String) -> Boolean): List<Achado>? {
        if (cursor >= contatos.size) return null
        val lote = mutableListOf<Achado>()
        while (cursor < contatos.size && lote.size < LOTE) {
            val c = contatos[cursor++]
            if (jaFeitas(c.e164)) continue
            lote += Achado(
                username = c.e164,
                nome = c.nome,
                exigirNome = false,  // WhatsApp: nome da agenda pode diferir do cadastrado
            )
        }
        return if (lote.isEmpty()) null else lote
    }

    override suspend fun contaAgora(firme: Boolean): String? = contaLida

    companion object {
        private const val TAG = "FonteAgenda"
        private const val LOTE = 20
    }
}
