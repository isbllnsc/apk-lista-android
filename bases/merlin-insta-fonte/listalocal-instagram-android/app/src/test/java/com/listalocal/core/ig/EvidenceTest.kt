package com.listalocal.core.ig

import com.listalocal.core.ig.Evidence.Conversa
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.IG_ID
import com.listalocal.core.tree.amigosFixture
import com.listalocal.core.tree.ig
import com.listalocal.core.tree.inboxFixture
import com.listalocal.core.tree.threadFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceTest {

    private val prof = SelectorProfile.IG_448

    @Test fun `cabecalho em Online agora - o @ vem do cartao do topo com o nome do cabecalho`() {
        val online = threadFixture("Online agora", nome = "Ana Souza", cartao = "ana.souza")
        assertEquals("ana.souza", Evidence.arrobaDoCabecalho(online, prof).username)
        // Titulo truncado com "..." (6 letras ou mais) casa com o nome inteiro do cartao.
        val truncado = com.listalocal.core.tree.ArvoreReal.comRotuloNoId(online, "header_title", texto = "Ana Sou...")
        assertEquals("ana.souza", Evidence.arrobaDoCabecalho(truncado, prof).username)
    }

    @Test fun `perfil compartilhado numa mensagem nunca vira o @ da conversa`() {
        val compartilhado = ig(children = listOf(ig(text = "Zeca Lima"), ig(text = "zeca.lima"), ig(text = "Ver perfil", clickable = true)))
        val t = threadFixture("Online agora", nome = "Ana Souza", cartao = null, blocos = listOf(compartilhado))
        assertNull(Evidence.arrobaDoCabecalho(t, prof).username)
        // Grupo com um perfil compartilhado: tambem nada.
        val grupo = threadFixture("Ana, Bia e mais 2", nome = "Família", cartao = null, blocos = listOf(compartilhado))
        assertNull(Evidence.arrobaDoCabecalho(grupo, prof).username)
        assertTrue(Evidence.subtituloDeGrupo(grupo, prof))
        assertFalse(Evidence.subtituloDeGrupo(t, prof))
    }

    @Test fun `le a conta aberta no titulo da caixa de entrada`() {
        assertEquals("minhaconta", Evidence.conta(inboxFixture("MinhaConta"), prof))
    }

    @Test fun `conversa so e provada pelo arroba exato no cabecalho`() {
        assertEquals(Conversa.PROVADA, Evidence.conversa(threadFixture("joao.silva"), prof, "joao.silva"))
        assertEquals(Conversa.PROVADA, Evidence.conversa(threadFixture("@Joao.Silva"), prof, "joao.silva"))
    }

    @Test fun `arroba parecido nao prova a conversa`() {
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(threadFixture("joao.silva_"), prof, "joao.silva"))
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(threadFixture("joao.silva"), prof, "joao.silv"))
    }

    @Test fun `nome de exibicao nunca prova o arroba por pedaco`() {
        // O nome "Joao Silva" contem "joao", mas o @ aberto e outro.
        val outra = threadFixture("maria.souza", nome = "Joao Silva")
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(outra, prof, "joao"))
    }

    @Test fun `cabecalho sem o arroba exposto fica sem prova`() {
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(threadFixture(null), prof, "joao.silva"))
    }

    @Test fun `arroba na descricao do cabecalho tambem prova`() {
        val t = threadFixture("joao.silva", subtituloNaDescricao = true)
        assertEquals(Conversa.PROVADA, Evidence.conversa(t, prof, "joao.silva"))
    }

    @Test fun `sem cabecalho de conversa nao ha conversa aberta`() {
        // Com @ inexistente o link ig.me nao faz nada: a caixa de entrada continua na frente.
        assertEquals(Conversa.NAO_ABERTA, Evidence.conversa(inboxFixture("minhaconta"), prof, "joao"))
    }

    @Test fun `subtitulo em Online agora e provado pelo cartao do topo`() {
        val t = threadFixture("Online agora", cartao = "joao.silva")
        assertEquals(Conversa.PROVADA, Evidence.conversa(t, prof, "joao.silva"))
        // Conversa longa: cartao fora da tela e subtitulo sem o @ = sem prova.
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(threadFixture("Online agora", cartao = null), prof, "joao.silva"))
    }

    @Test fun `mensagem igual ao arroba fora do cartao nao prova`() {
        val t = threadFixture("Online agora", cartao = null, mensagens = listOf("joao.silva"))
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(t, prof, "joao.silva"))
    }

    @Test fun `conversa comercial mostra o arroba no titulo`() {
        val t = threadFixture("Conversa comercial", nome = "loja.oficial", cartao = null)
        assertEquals(Conversa.PROVADA, Evidence.conversa(t, prof, "loja.oficial"))
    }

    @Test fun `cartao que diz que nao se seguem e lido`() {
        val t = threadFixture("joao", cartaoExtra = listOf("Vocês não se seguem no Instagram"))
        assertTrue(Evidence.naoSegue(t, prof))
        assertFalse(Evidence.naoSegue(threadFixture("joao", cartaoExtra = listOf("Vocês se seguem mutuamente no Instagram")), prof))
    }

    @Test fun `mensagens temporarias pela dica do campo`() {
        val t = threadFixture("joao", dica = "Mensagem tempo...")
        assertTrue(Evidence.temporaria(t, prof))
        assertEquals("", Evidence.textoDoCampo(t, prof))
        assertFalse(Evidence.temporaria(threadFixture("joao"), prof))
    }

    @Test fun `enviar desabilitado nao conta`() {
        assertNull(Evidence.botaoEnviar(threadFixture("joao", campo = "Oi", enviarHabilitado = false), prof))
    }

    @Test fun `conta que nao recebe mensagens e indisponivel`() {
        val t = threadFixture("joao", avisos = listOf("Esta conta não pode receber mensagens."))
        assertEquals(Conversa.INDISPONIVEL, Evidence.conversa(t, prof, "joao"))
    }

    @Test fun `dica do campo nao conta como texto`() {
        assertEquals("", Evidence.textoDoCampo(threadFixture("joao"), prof))
        assertEquals("Oi!", Evidence.textoDoCampo(threadFixture("joao", campo = "Oi!\r\n"), prof))
    }

    @Test fun `botao enviar so existe com texto no campo`() {
        assertNull(Evidence.botaoEnviar(threadFixture("joao"), prof))
        assertNotNull(Evidence.botaoEnviar(threadFixture("joao", campo = "Oi"), prof))
    }

    @Test fun `conta a mensagem pelo texto exato`() {
        val t = threadFixture("joao", mensagens = listOf("Oi", "Oi!", "Oi"))
        assertEquals(2, Evidence.contarMensagem(t, prof, "Oi"))
        assertEquals(0, Evidence.contarMensagem(t, prof, "oi"))
    }

    @Test fun `reconhece falha de envio e restricao`() {
        assertTrue(Evidence.falhaDeEnvio(threadFixture("j", avisos = listOf("Não enviada. Toque para tentar novamente")), prof))
        assertTrue(Evidence.restricao(threadFixture("j", avisos = listOf("Tente novamente mais tarde")), prof))
        assertFalse(Evidence.restricao(threadFixture("j", mensagens = listOf("Oi")), prof))
    }

    @Test fun `pedido de parar na conversa e lido e o nosso texto nao conta`() {
        val nosso = "Evento sábado! Para não receber mais, responda PARE."
        assertTrue(Evidence.pediuParaParar(threadFixture("j", mensagens = listOf(nosso, "pare")), prof, nosso, "j"))
        assertFalse(Evidence.pediuParaParar(threadFixture("j", mensagens = listOf(nosso, "Vou sim!")), prof, nosso, "j"))
    }

    @Test fun `arroba ou nome do cartao nao viram pedido de parar`() {
        val t = threadFixture("sair", nome = "Pare")
        assertFalse(Evidence.pediuParaParar(t, prof, "Oi", "sair"))
    }

    // ---- Variacoes do cabecalho e do campo (INSTAGRAM-APP-REAL.md 1.f; TESTE-ENVIO-REAL.md) ----

    @Test fun `arroba e estado juntos no subtitulo provam`() {
        assertEquals(Conversa.PROVADA, Evidence.conversa(threadFixture("ana.souza · Online agora"), prof, "ana.souza"))
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(threadFixture("ana.souza_ · Online agora", cartao = null), prof, "ana.souza"))
    }

    @Test fun `subtitulo sem texto exposto nao prova mas o cartao do topo prova`() {
        val semTexto = threadFixture("ana.souza", subtituloTexto = false)
        assertEquals(Conversa.PROVADA, Evidence.conversa(semTexto, prof, "ana.souza"))
        assertEquals(Conversa.SEM_PROVA, Evidence.conversa(threadFixture("ana.souza", subtituloTexto = false, cartao = null), prof, "ana.souza"))
    }

    @Test fun `nome prova quando o subtitulo so mostra um estado`() {
        for (estado in listOf("Online agora", "Online há 1 h", "Ativo(a) há 5 min", "Visto ontem", "Active now")) {
            val t = threadFixture(estado, nome = "Ana Souza", cartao = null)
            assertTrue(estado, Evidence.nomeProva(t, prof, "Ana Souza"))
        }
        // Subtitulo (IgView) sem texto legivel: so o nome.
        assertTrue(Evidence.nomeProva(threadFixture("x", nome = "Ana Souza", subtituloTexto = false, cartao = null), prof, "Ana Souza"))
    }

    @Test fun `nome nao prova com outro arroba no subtitulo, nome diferente ou nome vazio`() {
        assertFalse(Evidence.nomeProva(threadFixture("ana.souza.fake", nome = "Ana Souza"), prof, "Ana Souza"))
        // Um @ que comeca com a palavra de um estado continua sendo um @.
        assertFalse(Evidence.nomeProva(threadFixture("online.shop", nome = "Ana Souza"), prof, "Ana Souza"))
        assertTrue(Evidence.nomeProva(threadFixture("Digitando...", nome = "Ana Souza"), prof, "Ana Souza"))
        assertFalse(Evidence.nomeProva(threadFixture("Online agora", nome = "Ana Sousa"), prof, "Ana Souza"))
        assertFalse(Evidence.nomeProva(threadFixture("Online agora", nome = "Ana Souza"), prof, ""))
        assertFalse(Evidence.nomeProva(inboxFixture("minhaconta"), prof, "Ana Souza"))
    }

    @Test fun `nome truncado com reticencias vale pelo comeco`() {
        assertTrue(Evidence.nomeProva(threadFixture("Online agora", nome = "Pessoa Lon..."), prof, "Pessoa Longa Exemplo"))
        assertTrue(Evidence.nomeProva(threadFixture("Online agora", nome = "Pessoa Lon…"), prof, "Pessoa Longa Exemplo"))
        assertFalse(Evidence.nomeProva(threadFixture("Online agora", nome = "Pess..."), prof, "Pessoa Longa Exemplo"))
        assertFalse(Evidence.nomeProva(threadFixture("Online agora", nome = "Pessoa Outra..."), prof, "Pessoa Longa Exemplo"))
    }

    @Test fun `campo vazio e o Enviar ausente, qualquer que seja a dica`() {
        // Uma dica que o perfil nao conhece nao vira texto: sem Enviar, o campo esta vazio.
        assertEquals("", Evidence.textoDoCampo(threadFixture("j", dica = "Escreva uma mensagem..."), prof))
        assertEquals("Oi", Evidence.textoDoCampo(threadFixture("j", campo = "Oi"), prof))
    }

    @Test fun `conversa nova tem Enviar desabilitado e bandeja de figurinhas`() {
        val nova = threadFixture("j", novaConversa = true)
        assertNull(Evidence.botaoEnviar(nova, prof))
        assertEquals("", Evidence.textoDoCampo(nova, prof))
        assertNotNull(Evidence.botaoEnviar(threadFixture("j", novaConversa = true, campo = "Oi"), prof))
    }

    @Test fun `botoes de send dentro das mensagens nunca viram o Enviar`() {
        assertNull(Evidence.botaoEnviar(threadFixture("j", iscas = true), prof))
        val comTexto = Evidence.botaoEnviar(threadFixture("j", iscas = true, campo = "Oi"), prof)
        assertEquals(IG_ID + "row_thread_composer_send_button_container", comTexto?.viewIdResourceName)
    }

    @Test fun `conversa aberta pelo cabecalho ou pelo campo`() {
        assertTrue(Evidence.temConversa(threadFixture("j"), prof))
        assertTrue(Evidence.temConversa(ig(children = listOf(ig(id = "row_thread_composer_edittext"))), prof))
        assertFalse(Evidence.temConversa(inboxFixture("minhaconta"), prof))
        assertEquals(Conversa.NAO_ABERTA, Evidence.conversa(amigosFixture(listOf("j" to true)), prof, "j"))
    }

    @Test fun `bolha com texto longo conta mesmo com a hora junto`() {
        val texto = "Sábado tem evento às 20h"
        val t = threadFixture("j", mensagens = listOf("$texto, 22:05"))
        assertEquals(1, Evidence.contarMensagem(t, prof, texto))
        // Texto curto: so exato ("Oi" dentro de "Oi, tudo bem?" nao conta).
        assertEquals(0, Evidence.contarMensagem(threadFixture("j", mensagens = listOf("Oi, tudo bem?")), prof, "Oi"))
    }

    @Test fun `linhas de amigos proximos trazem arroba e marca`() {
        val linhas = Evidence.linhasAmigos(amigosFixture(listOf("ana" to true, "Bruno.B" to false)), prof)
        assertEquals(listOf("ana" to true, "bruno.b" to false), linhas.map { it.username to it.marcada })
    }

    @Test fun `amigos com outro id do arroba lidos pela assinatura, nao zerados`() {
        // Finding 7: num aparelho onde o id fixo do @ (row_user_username) NAO existe, o @ da
        // linha e resolvido pela assinatura em vez de zerar todas as linhas (NAO_ENCONTRADO calado).
        val aoVivo = SelectorProfile.IG_448.copy(assinaturaAoVivo = true)
        val tela = ig(children = listOf(
            ig(id = "rv_x", scrollable = true, children = listOf(
                ig(id = "row_x", clickable = true, children = listOf(
                    ig(id = "uname_x", text = "ana.souza"),
                    ig(cls = "com.instagram.igds.components.checkbox.IgdsCheckBox", checked = true),
                )),
                ig(id = "row_x", clickable = true, children = listOf(
                    ig(id = "uname_x", text = "bruno.b"),
                    ig(cls = "com.instagram.igds.components.checkbox.IgdsCheckBox", checked = false),
                )),
            )),
        ))
        val linhas = Evidence.linhasAmigos(tela, aoVivo)
        assertEquals(listOf("ana.souza" to true, "bruno.b" to false), linhas.map { it.username to it.marcada })
    }
}
