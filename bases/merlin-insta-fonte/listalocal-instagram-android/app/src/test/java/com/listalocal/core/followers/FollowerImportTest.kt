package com.listalocal.core.followers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowerImportTest {

    @Test fun `arroba normalizado e rotas reservadas recusadas`() {
        assertEquals("ana", FollowerImport.username("'@Ana"))
        assertEquals("joao.silva_", FollowerImport.username(" @Joao.Silva_ "))
        assertNull(FollowerImport.username("explore"))
        assertNull(FollowerImport.username("a".repeat(31)))
        assertNull(FollowerImport.username("com espaço"))
    }
}
