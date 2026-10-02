package com.listalocal.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listalocal.core.contacts.Plataforma
import com.listalocal.core.followers.Audience
import com.listalocal.core.followers.Follower
import com.listalocal.core.followers.FollowerImport
import com.listalocal.core.selectors.TargetApp
import com.listalocal.data.Campanhas
import com.listalocal.data.Outcomes
import com.listalocal.expiry.ExpiryGate
import com.listalocal.service.AutomationController
import com.listalocal.service.Campanha
import com.listalocal.service.Modo
import com.listalocal.service.ModoMensagens
import com.listalocal.service.Origem
import com.listalocal.service.PersonResult
import com.listalocal.service.Relatorio
import com.listalocal.service.RunPhase
import com.listalocal.service.RunState
import com.listalocal.service.Velocidade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Etapas da jornada guiada. Ordem = ordem da tela; RESULT fecha a operacao. */
enum class Step(val titulo: String) {
    CONSENT("Boas-vindas"),
    PERMISSIONS("Permissões"),
    RECIPIENTS("Destinatários"),
    WA_BROADCAST("Criar listas"),
    WA_MESSAGE("Enviar mensagem"),
    PLAN("Plano"),
    RUN("Execução"),
    RESULT("Resultado"),
}

/** As mesmas bases legais do Merlin (LGPD). */
val BASES_LEGAIS = listOf(
    "Convite a contatos/seguidores — legítimo interesse",
    "Pessoas que pediram para receber — consentimento",
    "Clientes atuais — execução de contrato",
)

data class UiState(
    val step: Step = Step.CONSENT,
    /** Plataforma escolhida na tela inicial. */
    val plataforma: Plataforma = Plataforma.INSTAGRAM,
    val aceitouRiscos: Boolean = false,
    val versaoInstagram: String? = null,
    /** O @ da conta aberta no Instagram, lido na conferencia. */
    val contaInstagram: String? = null,
    // Destinatarios: ao vivo no Instagram, sem arquivo.
    val origem: Origem = Origem.SEGUIDORES,
    val pularComerciais: Boolean = false,
    val naoEnviarTexto: String = "",
    val soParaTexto: String = "",
    val naoEnviar: Audience.Arrobas = Audience.Arrobas(emptySet(), emptyList()),
    val soPara: Audience.Arrobas = Audience.Arrobas(emptySet(), emptyList()),
    /** Operacao do modo C que parou antes do fim, para retomar do ponto salvo. */
    val salva: Campanha? = null,
    val salvaFeitos: List<PersonResult> = emptyList(),
    // Plano
    val modo: Modo = Modo.DM,
    /** Mensagem 1; as outras duas sao opcionais. */
    val mensagem: String = "",
    val mensagem2: String = "",
    val mensagem3: String = "",
    val modoMensagens: ModoMensagens = ModoMensagens.REVEZAR,
    // "Ate onde": vazio = sem limite, do inicio da lista, sem hora.
    val limiteTexto: String = "",
    val aPartirDeTexto: String = "",
    val pularJaRecebeu: Boolean = true,
    val pararAsTexto: String = "",
    val velocidade: Velocidade = Velocidade.NORMAL,
    val autoConfirm: Boolean = false,
    val baseLegal: String = "",
    val baseLegalOutro: String = "",
    val confirmouSeguidores: Boolean = false,
    /** Modo A: os @ de "So para estes @", menos quem pediu para parar e "Nao enviar para". */
    val plano: Audience.Plano = Audience.Plano(emptyList(), emptyMap()),
    /** Mensagem para o modo LISTAS_TRANSMISSAO (WhatsApp Etapa 3). */
    val mensagemWa: String = "",
    /** Índices das listas de transmissão selecionadas pelo usuário para envio na Etapa 4. */
    val listasSelecionadasWa: Set<Int> = emptySet(),
    /** Quando true, filtra apenas contatos com DDD 21 (+5521) na Fase 1. */
    val soPor5521: Boolean = false,
) {
    val instagramInstalado: Boolean get() = versaoInstagram != null
    val baseLegalFinal: String get() = if (baseLegal == OUTRA) baseLegalOutro.trim() else baseLegal

    /** As mensagens escritas, na ordem, sem as vazias. */
    val mensagens: List<String>
        get() = listOf(mensagem, mensagem2, mensagem3).map(String::trim).filter(String::isNotEmpty)

    val mensagemOk: Boolean
        get() = modo != Modo.DM ||
            (mensagens.isNotEmpty() && listOf(mensagem, mensagem2, mensagem3).all { it.length <= MAX_MENSAGEM })

    /** "Enviar para ate X pessoas". null = sem limite (ou o texto nao e um numero: [opcoesOk] barra). */
    val limite: Int? get() = limiteTexto.trim().toIntOrNull()?.takeIf { it > 0 }
    val aPartirDe: String? get() = FollowerImport.username(aPartirDeTexto)
    /** "Parar as HH:MM" em minutos do dia. */
    val pararAs: Int?
        get() = Regex("""(\d{1,2})[:hH](\d{2})""").matchEntire(pararAsTexto.trim())?.destructured
            ?.let { (h, m) -> (h.toInt() * 60 + m.toInt()).takeIf { h.toInt() < 24 && m.toInt() < 60 } }

    /** Cada campo de "Ate onde" vazio ou valido. */
    val opcoesOk: Boolean
        get() = (limiteTexto.isBlank() || limite != null) && (aPartirDeTexto.isBlank() || aPartirDe != null) &&
            (pararAsTexto.isBlank() || pararAs != null)

    /** "Enviar como @X · ate 50 pessoas · 3 mensagens em sequencia · Normal". */
    val linhaDoEnvio: String
        get() = listOf(
            if (plataforma == Plataforma.WHATSAPP) "Enviar via WhatsApp"
            else "Enviar como @${contaInstagram.orEmpty()}",
            limite?.let { "até $it ${if (it == 1) "pessoa" else "pessoas"}" } ?: "sem limite de pessoas",
            if (mensagens.size <= 1) "1 mensagem" else "${mensagens.size} mensagens ${modoMensagens.rotulo}",
            velocidade.rotulo,
        ).joinToString(" · ")

    /** Algo digitado que nao e @: a pessoa que o dono quis tirar (ou incluir) ficaria fora da regra. */
    val arrobasInvalidos: List<String>
        get() = if (plataforma == Plataforma.WHATSAPP) emptyList()  // WA usa números, sem validação de @
        else naoEnviar.invalidos + soPara.invalidos

    /** A conta conferida e listas sem @ invalido; Amigos Proximos precisa de alguem em "So para estes @". */
    val podeAvancar: Boolean
        get() = when (plataforma) {
            Plataforma.WHATSAPP -> arrobasInvalidos.isEmpty()  // WA: sem contaInstagram exigida
            else -> contaInstagram != null && arrobasInvalidos.isEmpty() && (modo == Modo.DM || plano.total > 0)
        }

    val podeIniciar: Boolean
        get() = podeAvancar && mensagemOk && baseLegalFinal.isNotEmpty() && confirmouSeguidores &&
            (modo != Modo.DM || opcoesOk)

    companion object {
        const val MAX_MENSAGEM = 2000
        const val MAX_LISTA = 20_000
        const val OUTRA = "outra"
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val runState = AutomationController.state

    /** Pedidos de parar lidos pelo proprio app nas conversas desta conta. */
    private var locaisParar: Set<String> = emptySet()

    init {
        // A conta e a da conferencia DO MODO escolhido, nunca a de outro modo: o dono
        // tem duas contas e pode ter conferido cada modo numa (26/09). Ao iniciar, o
        // servico le a conta aberta ao vivo e refaz a conferencia se ela mudou.
        viewModelScope.launch {
            combine(AutomationController.state.map { it.compat }, _ui.map { it.modo }) { compat, modo ->
                compat[modo]?.conta
            }
                .distinctUntilChanged()
                .collect { conta ->
                    _ui.value = _ui.value.copy(contaInstagram = conta)
                    carregarLocais()
                }
        }
    }

    private fun active(): Boolean {
        if (!ExpiryGate.isExpired(getApplication<Application>())) return true
        expireNow()
        return false
    }

    fun expireNow() {
        if (!ExpiryGate.enabled) return
        _ui.value = UiState()
        AutomationController.cancel()
        AutomationController.pendingPlan = null
        AutomationController.pendingCompat = null
        AutomationController.update {
            RunState(phase = RunPhase.CANCELLED, message = "Prazo encerrado em 04/10/2026.")
        }
    }

    fun goTo(step: Step) { if (active()) _ui.value = _ui.value.copy(step = step) }

    fun aceitarRiscos(v: Boolean) { _ui.value = _ui.value.copy(aceitouRiscos = v) }

    fun detectarInstagram() {
        if (!active()) return
        val versao = try {
            @Suppress("DEPRECATION")
            getApplication<Application>().packageManager.getPackageInfo(TargetApp.INSTAGRAM.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        _ui.value = _ui.value.copy(versaoInstagram = versao)
    }

    /** Pede ao servico a conferencia na tela real, para um modo. */
    fun verificarCompatibilidade(modo: Modo) {
        if (active()) AutomationController.checkCompatibility(modo)
    }

    fun verificarCompatibilidadeWa(modo: Modo) {
        if (active()) AutomationController.checkCompatibilityWa(modo)
    }

    /** Abre o seletor de contas do Instagram; o app confere a conta escolhida e volta. */
    fun trocarConta() { if (active()) AutomationController.trocarConta(_ui.value.modo) }

    /**
     * Pedidos de parar e a operacao salva desta conta. Le o disco fora da
     * thread principal: com milhares de desfechos gravados, ler e separar
     * todos travaria a tela.
     */
    private suspend fun carregarLocais() {
        val conta = _ui.value.contaInstagram
        if (conta == null) {
            locaisParar = emptySet()
            _ui.value = _ui.value.copy(salva = null, salvaFeitos = emptyList())
        } else {
            val app = getApplication<Application>()
            val (parar, salva, feitos) = withContext(Dispatchers.IO) {
                val store = Outcomes(app, conta)
                val salva = Campanhas(app).ler(conta)
                Triple(store.pediramParaParar(), salva, salva?.let { store.desde(it.inicio) }.orEmpty())
            }
            if (_ui.value.contaInstagram != conta) return // a conta mudou enquanto lia
            locaisParar = parar
            _ui.value = _ui.value.copy(salva = salva, salvaFeitos = feitos)
        }
        recomputar()
    }

    
    fun setPlataforma(p: Plataforma) {
        _ui.value = _ui.value.copy(plataforma = p)
    }

    fun setOrigem(o: Origem) { if (active()) _ui.value = _ui.value.copy(origem = o) }
    fun setPularComerciais(v: Boolean) { if (active()) _ui.value = _ui.value.copy(pularComerciais = v) }
    fun setNaoEnviar(t: String) {
        if (active()) { _ui.value = _ui.value.copy(naoEnviarTexto = t.take(UiState.MAX_LISTA)); recomputar() }
    }
    fun setSoPara(t: String) {
        if (active()) { _ui.value = _ui.value.copy(soParaTexto = t.take(UiState.MAX_LISTA)); recomputar() }
    }

    fun setModo(m: Modo) { if (active()) { _ui.value = _ui.value.copy(modo = m); recomputar() } }
    fun setMensagem(t: String) { if (active()) _ui.value = _ui.value.copy(mensagem = t.take(UiState.MAX_MENSAGEM + 200)) }
    /** [n]: 1 a 3. */
    fun setMensagemN(n: Int, t: String) {
        if (!active()) return
        val v = t.take(UiState.MAX_MENSAGEM + 200)
        _ui.value = when (n) {
            1 -> _ui.value.copy(mensagem = v)
            2 -> _ui.value.copy(mensagem2 = v)
            else -> _ui.value.copy(mensagem3 = v)
        }
    }
    fun setModoMensagens(m: ModoMensagens) { if (active()) _ui.value = _ui.value.copy(modoMensagens = m) }
    fun setLimite(t: String) { if (active()) _ui.value = _ui.value.copy(limiteTexto = t.filter(Char::isDigit).take(6)) }
    fun setAPartirDe(t: String) { if (active()) _ui.value = _ui.value.copy(aPartirDeTexto = t.take(40)) }
    fun setPularJaRecebeu(v: Boolean) { if (active()) _ui.value = _ui.value.copy(pularJaRecebeu = v) }
    fun setMensagemWa(t: String) { if (active()) _ui.value = _ui.value.copy(mensagemWa = t.take(4000)) }
    fun setPararAs(t: String) { if (active()) _ui.value = _ui.value.copy(pararAsTexto = t.take(5)) }
    fun setVelocidade(v: Velocidade) { if (active()) _ui.value = _ui.value.copy(velocidade = v) }
    fun setAutoConfirm(v: Boolean) { if (active()) _ui.value = _ui.value.copy(autoConfirm = v) }
    fun setBaseLegal(b: String) { if (active()) _ui.value = _ui.value.copy(baseLegal = b) }
    fun setBaseLegalOutro(t: String) { if (active()) _ui.value = _ui.value.copy(baseLegalOutro = t.take(500)) }
    fun setConfirmou(v: Boolean) { if (active()) _ui.value = _ui.value.copy(confirmouSeguidores = v) }

    private fun recomputar() {
        val s = _ui.value
        val isWa = s.plataforma == Plataforma.WHATSAPP
        // WhatsApp usa números de telefone (E.164); Instagram usa @usernames
        val naoEnviar = if (isWa) Audience.numeros(s.naoEnviarTexto) else Audience.arrobas(s.naoEnviarTexto)
        val soPara   = if (isWa) Audience.numeros(s.soParaTexto)   else Audience.arrobas(s.soParaTexto)
        val plano = Audience.montar(soPara.validos.map { Follower(it, it) }, locaisParar, naoEnviar.validos)
        _ui.value = s.copy(naoEnviar = naoEnviar, soPara = soPara, plano = plano)
    }

    private fun liberado(modo: Modo): Boolean =
        if (_ui.value.plataforma == Plataforma.WHATSAPP) {
            // WA: o usuário já passou pela etapa de verificação (ConferirWaScreen)
            // Se chegou até a Etapa 4, o WA está OK. compatWa opcional como confirmação.
            runState.value.compatWa.isNotEmpty() || true  // libera mesmo sem compat salvo
        } else {
            runState.value.compat[modo]?.valeParaVersao(_ui.value.versaoInstagram) == true
        }

    fun iniciar() {
        if (!active()) return
        recomputar()
        val s = _ui.value
        if (!s.podeIniciar || !liberado(s.modo)) return

        if (s.plataforma == Plataforma.WHATSAPP) {
            // WhatsApp: não exige contaInstagram; usa agenda do celular
            val c = Campanha(
                conta = "",  // WA não usa @conta
                origem = Origem.SEGUIDORES,
                mensagens = s.mensagens,
                velocidade = s.velocidade,
                naoEnviar = s.naoEnviar.validos,
                soPara = s.soPara.validos,
                pularComerciais = false,
                baseLegal = s.baseLegalFinal,
                inicio = System.currentTimeMillis(),
                modoMensagens = s.modoMensagens,
                limite = s.limite,
                aPartirDe = null,  // campo removido da UI WA
                pularJaRecebeu = s.pularJaRecebeu,
                pararAs = s.pararAs,
            )
            Campanhas(getApplication()).salvar(c)
            if (s.modo == Modo.LISTAS_TRANSMISSAO) {
                // Fase 1: criar listas de transmissão da agenda
                // Fase 2: enviar mensagem por cada lista criada
                val mensagem = s.mensagens.firstOrNull() ?: ""
                AutomationController.startBroadcastWa(c, mensagem)
            } else {
                AutomationController.startCampanhaWa(c)
            }
            _ui.value = s.copy(step = Step.RUN, salva = null, salvaFeitos = emptyList())
            return
        }

        val conta = s.contaInstagram ?: return
        if (s.modo == Modo.DM) {
            val c = Campanha(
                conta = conta, origem = s.origem, mensagens = s.mensagens, velocidade = s.velocidade,
                naoEnviar = s.naoEnviar.validos, soPara = s.soPara.validos,
                pularComerciais = s.origem == Origem.CONVERSAS && s.pularComerciais,
                baseLegal = s.baseLegalFinal, inicio = System.currentTimeMillis(),
                modoMensagens = s.modoMensagens, limite = s.limite, aPartirDe = s.aPartirDe,
                pularJaRecebeu = s.pularJaRecebeu, pararAs = s.pararAs,
            )
            Campanhas(getApplication()).salvar(c)
            AutomationController.startCampanha(c)
        } else {
            AutomationController.start(
                plan = s.plano.lotes,
                modo = s.modo,
                texto = "",
                intervaloMs = 0,
                autoConfirm = s.autoConfirm,
                remover = locaisParar.sorted(),
            )
        }
        _ui.value = s.copy(step = Step.RUN, salva = null, salvaFeitos = emptyList())
    }

    /**
     * Inicia apenas a Fase 1 (criar listas) — sem mensagem.
     * O app navegará para [Step.WA_MESSAGE] quando [RunPhase.LISTS_CREATED] for detectado.
     */
    fun iniciarBroadcastWa() {
        if (!active()) return
        val s = _ui.value
        val c = Campanha(
            conta = "",
            origem = Origem.SEGUIDORES,
            mensagens = emptyList(),
            velocidade = s.velocidade,
            naoEnviar = emptySet(),
            soPara = emptySet(),
            pularComerciais = false,
            baseLegal = "",
            inicio = System.currentTimeMillis(),
            modoMensagens = s.modoMensagens,
            limite = null,
            aPartirDe = null,
            pularJaRecebeu = false,
            pararAs = null,
        )
        Campanhas(getApplication()).salvar(c)
        AutomationController.soPor5521 = s.soPor5521
        AutomationController.startCreateListsWa(c)
        _ui.value = s.copy(step = Step.RUN, salva = null, salvaFeitos = emptyList(), listasSelecionadasWa = emptySet())
    }

    /** Alterna o filtro de DDD 21 (+5521) na tela de criar listas. */
    fun toggleSoPor5521(v: Boolean) {
        _ui.value = _ui.value.copy(soPor5521 = v)
    }

    /**
     * Abre o WhatsApp e relê todas as listas de transmissão existentes — sem criar novas.
     * Útil para descobrir listas criadas manualmente pelo usuário após a Etapa 3.
     */
    fun relerListasWa() {
        if (!active()) return
        AutomationController.startScanOnlyWa()
        _ui.value = _ui.value.copy(step = Step.RUN)
    }

    /** Alterna a seleção de uma lista de transmissão na Etapa 4. */
    fun toggleListaWa(index: Int) {
        val current = _ui.value.listasSelecionadasWa
        val updated = if (current.contains(index)) current - index else current + index
        _ui.value = _ui.value.copy(listasSelecionadasWa = updated)
    }

    /** Seleciona todas as listas de transmissão disponíveis. */
    fun selectAllListasWa(indices: Set<Int>) {
        _ui.value = _ui.value.copy(listasSelecionadasWa = indices)
    }

    /** Desmarca todas as listas de transmissão. */
    fun deselectAllListasWa() {
        _ui.value = _ui.value.copy(listasSelecionadasWa = emptySet())
    }

    /**
     * Inicia a Fase 2 (enviar mensagem pelas listas selecionadas).
     * Usa [UiState.mensagemWa] como texto e [UiState.listasSelecionadasWa] como alvos.
     */
    fun enviarMensagemWa() {
        if (!active()) return
        val s = _ui.value
        val mensagem = s.mensagemWa.trim()
        if (mensagem.isEmpty()) return
        val selecionadas = if (s.listasSelecionadasWa.isNotEmpty()) {
            s.listasSelecionadasWa
        } else {
            runState.value.broadcastListas.map { it.index }.toSet()
        }
        if (selecionadas.isEmpty()) return
        AutomationController.startSendViaListsWa(mensagem, selecionadas)
        _ui.value = s.copy(step = Step.RUN)
    }

    /** Retoma a operacao salva: quem ja tem desfecho nela nao volta; o resto segue do ponto de parada. */
    fun retomarSalva() {
        if (!active()) return
        viewModelScope.launch {
            carregarLocais()
            val s = _ui.value
            val c = s.salva ?: return@launch
            if (c.conta != s.contaInstagram || !liberado(Modo.DM) || runState.value.running) return@launch
            AutomationController.startCampanha(c, s.salvaFeitos)
            _ui.value = s.copy(step = Step.RUN, modo = Modo.DM, salva = null, salvaFeitos = emptyList())
        }
    }

    /** O dono prefere comecar outra: a salva deixa de existir (os desfechos gravados continuam). */
    fun descartarSalva() {
        val conta = _ui.value.contaInstagram ?: return
        Campanhas(getApplication()).encerrar(conta)
        _ui.value = _ui.value.copy(salva = null, salvaFeitos = emptyList())
    }

    /** Depois do resultado: o que foi gravado sai da proxima fila. */
    fun novaOperacao() {
        _ui.value = _ui.value.copy(step = Step.RECIPIENTS, confirmouSeguidores = false)
        viewModelScope.launch { carregarLocais() }
    }

    fun exportarResultado(uri: Uri) {
        val run = runState.value
        // A conta da operacao (lida ao vivo ao iniciar), nao a da ultima conferencia.
        val conta = run.conta ?: _ui.value.contaInstagram.orEmpty()
        val base = _ui.value.baseLegalFinal.ifEmpty { AutomationController.campanha?.baseLegal.orEmpty() }
        val mensagens = AutomationController.campanha?.mensagens.orEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(Relatorio.csv(conta, run.modo, base, run.results, mensagens).toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    fun confirmar(ok: Boolean) { if (active()) AutomationController.confirm(ok) }
    fun pausar() { if (active()) AutomationController.pause() }
    fun retomar() { if (active()) AutomationController.resume() }
    fun cancelar() = AutomationController.cancel()
}
