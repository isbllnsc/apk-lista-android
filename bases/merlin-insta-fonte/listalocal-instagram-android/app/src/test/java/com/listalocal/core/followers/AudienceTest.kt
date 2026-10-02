package com.listalocal.core.followers

import com.listalocal.core.followers.Audience.Motivo
import org.junit.Assert.assertEquals
import org.junit.Test

class AudienceTest {

    @Test fun `arrobas digitados ou colados, sem arquivo`() {
        val a = Audience.arrobas("@Ana, bia;caio\n  https://www.instagram.com/duda/ ana  @ana silva!")
        assertEquals(listOf("ana", "bia", "caio", "duda"), a.validos.toList())
        assertEquals(listOf("silva!"), a.invalidos)
    }

    @Test fun `vazio nao tem nada`() {
        assertEquals(Audience.Arrobas(emptySet(), emptyList()), Audience.arrobas(" \n , "))
    }

    @Test fun `pedido de parar vence Nao enviar para e tudo fica num lote so`() {
        val gente = listOf("ana", "bia", "caio", "duda").map { Follower(it, it) }
        val p = Audience.montar(gente, optOut = setOf("ana"), naoEnviar = setOf("ana", "caio"))
        assertEquals(listOf("bia", "duda"), p.lotes.single().followers.map { it.username })
        assertEquals(mapOf(Motivo.PEDIU_PARA_PARAR to 1, Motivo.NAO_ENVIAR to 1), p.pulados)
    }

    @Test fun `sem ninguem nao ha lote`() {
        assertEquals(0, Audience.montar(emptyList()).lotes.size)
    }
}
