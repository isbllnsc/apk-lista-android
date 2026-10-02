package com.listalocal.service

import com.listalocal.core.state.DmState
import org.junit.Assert.assertEquals
import org.junit.Test

class DesfechoTest {

    @Test fun `so recebeu, pode ter recebido ou pediu para parar barra nova operacao`() {
        val barram = Desfecho.entries.filter { it.bloqueiaNovoEnvio }.toSet()
        assertEquals(setOf(Desfecho.ENVIADO, Desfecho.INCERTO, Desfecho.PEDIU_PARA_PARAR), barram)
    }

    @Test fun `app que morreu depois do commit deixa a pessoa INCERTO`() {
        assertEquals(Desfecho.INCERTO, Desfecho.de(DmState.COMMIT))
        assertEquals(Desfecho.FALHA, Desfecho.de(DmState.TEXTO_CONFERIDO))
    }
}
