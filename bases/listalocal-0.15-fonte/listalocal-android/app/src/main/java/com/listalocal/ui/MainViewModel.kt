package com.listalocal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listalocal.BuildConfig
import com.listalocal.core.contacts.Batch
import com.listalocal.core.contacts.ContactExclusionPolicy
import com.listalocal.core.contacts.ContactNormalizer
import com.listalocal.core.contacts.NormalizedNumber
import com.listalocal.core.region.Regions
import com.listalocal.core.selectors.TargetApp
import com.listalocal.data.ContactsRepository
import com.listalocal.data.OneShotGate
import com.listalocal.service.AutomationController
import com.listalocal.service.RunPhase
import com.listalocal.service.RunState
import com.listalocal.expiry.ExpiryGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Etapas da jornada guiada. Ordem = ordem da tela; RESULT fecha a operacao. */
enum class Step(val titulo: String) {
    CONSENT("Boas-vindas"),
    PERMISSIONS("Permissões"),
    CONTACTS("Contatos"),
    PLAN("Plano"),
    RUN("Execução"),
    RESULT("Resultado"),
}

/** Uma pessoa da agenda com todos os seus numeros validos para exclusao manual. */
data class ExclusionChoice(
    val contactId: Long,
    val displayName: String,
    val numbers: List<String>,
    val excluded: Boolean = false,
)

data class UiState(
    val step: Step = Step.CONSENT,
    val loading: Boolean = false,
    /** Apps alvo instalados neste aparelho (WhatsApp e/ou WhatsApp Business). */
    val appsInstalados: List<TargetApp> = emptyList(),
    /** App sobre o qual a automacao vai operar. */
    val appAlvo: TargetApp = TargetApp.WHATSAPP,
    /** Contatos (pessoas) lidos da agenda. */
    val totalContatos: Int = 0,
    /** Telefones lidos, somando todos os numeros de todos os contatos. */
    val telefonesLidos: Int = 0,
    /** Telefones validos e unicos — a base de trabalho. */
    val validos: Int = 0,
    /** Telefones validos que eram repeticao de outro ja contado. */
    val duplicados: Int = 0,
    /** Contatos que nao tem nenhum telefone valido. */
    val semTelefoneValido: Int = 0,
    /** Quantos sobram depois dos filtros. */
    val filtrados: Int = 0,
    /** A exclusao vale somente ate esta operacao terminar ou o processo fechar. */
    val exclusionChoices: List<ExclusionChoice> = emptyList(),
    val excludedNumbers: Int = 0,
    val porUf: Map<String, Int> = emptyMap(),
    val porRegiao: Map<String, Int> = emptyMap(),
    val porDdd: Map<String, Int> = emptyMap(),
    val ufSelecionadas: Set<String> = emptySet(),
    val regiaoSelecionadas: Set<String> = emptySet(),
    /** DDDs escolhidos pelos chips rapidos (21, 22, 24…). */
    val dddSelecionados: Set<String> = emptySet(),
    /** DDDs digitados no campo "outros DDDs". */
    val dddTexto: String = "",
    /** Prefixos de nome (ex.: "LC, LF"): so contatos cujo nome comeca por um deles. */
    val prefixoNome: String = "",
    /** Modo turbo: selecao por prefixo (uma consulta por prefixo). */
    val turbo: Boolean = false,
    val autoConfirm: Boolean = false,
    val lotes: List<Batch> = emptyList(),
) {
    val temFiltro: Boolean
        get() = ufSelecionadas.isNotEmpty() || regiaoSelecionadas.isNotEmpty() ||
            dddSelecionados.isNotEmpty() || dddTexto.isNotBlank() || prefixos().isNotEmpty()

    fun prefixos(): List<String> = prefixoNome
        .split(",", ";", " ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    /** Uma frase que descreve o filtro ativo, para o plano e o resumo final. */
    val filtroResumo: String
        get() {
            if (!temFiltro) return "Todos os contatos"
            val partes = buildList {
                val ddds = (dddSelecionados + dddDigitados()).sorted()
                if (ddds.isNotEmpty()) add("DDD ${ddds.joinToString(", ")}")
                if (ufSelecionadas.isNotEmpty()) add(ufSelecionadas.sorted().joinToString(", "))
                if (regiaoSelecionadas.isNotEmpty()) {
                    add(regiaoSelecionadas.sorted().joinToString(", "))
                }
                if (prefixos().isNotEmpty()) add("nome começa com ${prefixos().joinToString(", ")}")
            }
            return partes.joinToString(" · ")
        }

    fun dddDigitados(): Set<String> = dddTexto
        .split(",", ";", " ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    /** Minimo do WhatsApp para uma lista de transmissao. */
    val podeAvancar: Boolean get() = filtrados >= MIN_DESTINATARIOS

    companion object {
        const val MIN_DESTINATARIOS = 2
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ContactsRepository(app)
    private val normalizer = ContactNormalizer()

    private var todos: List<NormalizedNumber> = emptyList()
    private var numbersByContactId: Map<Long, Set<String>> = emptyMap()
    private var excludedContactIds: Set<Long> = emptySet()

    // Preset de compilacao (ex.: aparelho da Leticia): prefixos e turbo ja preenchidos.
    private val _ui = MutableStateFlow(
        UiState(prefixoNome = BuildConfig.PRESET_PREFIXOS, turbo = BuildConfig.PRESET_TURBO),
    )
    val ui = _ui.asStateFlow()

    val runState = AutomationController.state

    private fun active(): Boolean {
        if (OneShotGate.isConsumed(getApplication<Application>())) {
            finishOneShot()
            return false
        }
        if (!ExpiryGate.isExpired(getApplication<Application>())) return true
        expireNow()
        return false
    }

    fun finishOneShot() {
        if (!OneShotGate.isConsumed(getApplication<Application>())) return
        todos = emptyList()
        numbersByContactId = emptyMap()
        excludedContactIds = emptySet()
        _ui.value = UiState()
        AutomationController.cancel()
        AutomationController.pendingPlan = null
        AutomationController.pendingCompatCheck = false
        AutomationController.update {
            RunState(phase = RunPhase.COMPLETED, message = "Uso único concluído.")
        }
    }

    fun expireNow() {
        if (!ExpiryGate.enabled) return
        todos = emptyList()
        numbersByContactId = emptyMap()
        excludedContactIds = emptySet()
        _ui.value = UiState()
        AutomationController.cancel()
        AutomationController.pendingPlan = null
        AutomationController.pendingCompatCheck = false
        AutomationController.update {
            RunState(phase = RunPhase.CANCELLED, message = "Prazo encerrado em 04/10/2026.")
        }
    }

    fun goTo(step: Step) { if (active()) _ui.value = _ui.value.copy(step = step) }

    /**
     * Descobre quais dos apps suportados estao instalados. Se so houver um,
     * ele ja vira o alvo — quem tem apenas o Business nao precisa escolher nada.
     */
    fun detectarApps() {
        if (!active()) return
        val pm = getApplication<Application>().packageManager
        val instalados = TargetApp.entries.filter { app ->
            try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        val alvo = when {
            instalados.size == 1 -> instalados.first()
            _ui.value.appAlvo in instalados -> _ui.value.appAlvo
            instalados.isNotEmpty() -> instalados.first()
            else -> _ui.value.appAlvo
        }
        AutomationController.selectTargetApp(alvo)
        _ui.value = _ui.value.copy(appsInstalados = instalados, appAlvo = alvo)
    }

    fun selecionarApp(app: TargetApp) {
        if (!active()) return
        AutomationController.selectTargetApp(app)
        _ui.value = _ui.value.copy(appAlvo = app)
    }

    /** Pede ao servico a verificacao na tela real do app alvo. */
    fun verificarCompatibilidade() {
        if (active()) AutomationController.checkCompatibility()
    }

    fun carregarContatos() {
        if (!active()) return
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            val carga = withContext(Dispatchers.IO) {
                if (OneShotGate.isConsumed(getApplication<Application>()) ||
                    ExpiryGate.isExpired(getApplication<Application>())) return@withContext null
                val raw = repo.readAll()
                if (OneShotGate.isConsumed(getApplication<Application>()) ||
                    ExpiryGate.isExpired(getApplication<Application>())) return@withContext null
                // Uma passagem valida cada telefone uma vez e preserva o mapa
                // antes do dedupe: excluir uma pessoa remove tambem numeros
                // compartilhados com outro contato.
                val normalized = normalizer.normalizeWithMetrics(raw)
                val nums = normalized.numbers
                val choices = raw.mapNotNull { c ->
                    val phones = normalized.numbersByContactId[c.localId].orEmpty().sorted()
                    if (phones.isEmpty()) null else ExclusionChoice(
                        contactId = c.localId,
                        displayName = c.displayName.ifBlank { "(sem nome)" },
                        numbers = phones,
                    )
                }
                Carga(
                    nums = nums,
                    numbersByContactId = normalized.numbersByContactId,
                    exclusionChoices = choices,
                    uf = Regions.countByUf(nums),
                    reg = Regions.countByRegiao(nums),
                    ddd = contarPorDdd(nums),
                    contatos = raw.size,
                    telefones = normalized.telefonesLidos,
                    duplicados = normalized.duplicados,
                    semTelefoneValido = normalized.semTelefoneValido,
                )
            }
            if (!active() || carga == null) return@launch
            todos = carga.nums
            numbersByContactId = carga.numbersByContactId
            excludedContactIds = emptySet()
            _ui.value = _ui.value.copy(
                loading = false,
                totalContatos = carga.contatos,
                telefonesLidos = carga.telefones,
                validos = carga.nums.size,
                exclusionChoices = carga.exclusionChoices,
                excludedNumbers = 0,
                duplicados = carga.duplicados,
                semTelefoneValido = carga.semTelefoneValido,
                filtrados = carga.nums.size,
                porUf = carga.uf,
                porRegiao = carga.reg,
                porDdd = carga.ddd,
                step = Step.CONTACTS,
            )
            recomputar()
        }
    }

    private data class Carga(
        val nums: List<NormalizedNumber>,
        val numbersByContactId: Map<Long, Set<String>>,
        val exclusionChoices: List<ExclusionChoice>,
        val uf: Map<String, Int>,
        val reg: Map<String, Int>,
        val ddd: Map<String, Int>,
        val contatos: Int,
        val telefones: Int,
        val duplicados: Int,
        val semTelefoneValido: Int,
    )

    /** Contagem por DDD, usando a mesma leitura de area do filtro. */
    private fun contarPorDdd(nums: List<NormalizedNumber>): Map<String, Int> =
        nums.mapNotNull { Regions.areaOf(it.e164).ddd }
            .groupingBy { it }.eachCount()

    fun toggleUf(uf: String) {
        if (!active()) return
        val cur = _ui.value.ufSelecionadas.toMutableSet()
        if (!cur.add(uf)) cur.remove(uf)
        _ui.value = _ui.value.copy(ufSelecionadas = cur)
        recomputar()
    }

    fun toggleRegiao(r: String) {
        if (!active()) return
        val cur = _ui.value.regiaoSelecionadas.toMutableSet()
        if (!cur.add(r)) cur.remove(r)
        _ui.value = _ui.value.copy(regiaoSelecionadas = cur)
        recomputar()
    }

    fun toggleDdd(ddd: String) {
        if (!active()) return
        val cur = _ui.value.dddSelecionados.toMutableSet()
        if (!cur.add(ddd)) cur.remove(ddd)
        _ui.value = _ui.value.copy(dddSelecionados = cur)
        recomputar()
    }

    fun setDdd(txt: String) {
        if (!active()) return
        _ui.value = _ui.value.copy(dddTexto = txt)
        recomputar()
    }

    fun setPrefixoNome(txt: String) {
        if (!active()) return
        _ui.value = _ui.value.copy(prefixoNome = txt)
        recomputar()
    }

    fun setTurbo(v: Boolean) {
        if (active()) _ui.value = _ui.value.copy(turbo = v && _ui.value.excludedNumbers == 0)
    }

    /** Alterna a pessoa inteira; a exclusao vale somente para esta operacao. */
    fun toggleExclusion(contactId: Long) {
        if (!active() || contactId !in numbersByContactId) return
        val next = excludedContactIds.toMutableSet()
        if (!next.add(contactId)) next.remove(contactId)
        excludedContactIds = next
        _ui.value = _ui.value.copy(exclusionChoices = _ui.value.exclusionChoices.map { choice ->
            if (choice.contactId == contactId) choice.copy(excluded = contactId in next) else choice
        })
        recomputar()
    }

    fun clearExclusions() {
        if (!active() || excludedContactIds.isEmpty()) return
        excludedContactIds = emptySet()
        _ui.value = _ui.value.copy(exclusionChoices = _ui.value.exclusionChoices.map {
            if (it.excluded) it.copy(excluded = false) else it
        })
        recomputar()
    }

    fun limparFiltros() {
        if (!active()) return
        _ui.value = _ui.value.copy(
            ufSelecionadas = emptySet(),
            regiaoSelecionadas = emptySet(),
            dddSelecionados = emptySet(),
            dddTexto = "",
            prefixoNome = "",
        )
        recomputar()
    }

    fun setAutoConfirm(v: Boolean) { if (active()) _ui.value = _ui.value.copy(autoConfirm = v) }

    private fun currentFilter(): Regions.Filter {
        val s = _ui.value
        return Regions.Filter(
            ddd = s.dddSelecionados + s.dddDigitados(),
            uf = s.ufSelecionadas,
            regiao = s.regiaoSelecionadas,
        )
    }

    private fun recomputar() {
        val porRegiao = Regions.apply(todos, currentFilter())
        val prefixos = _ui.value.prefixos()
        // Filtro por prefixo do nome e ordenacao alfabetica (ordem do seletor do
        // WhatsApp) quando ativo — o modo turbo percorre a lista nessa ordem.
        val porPrefixo = if (prefixos.isEmpty()) porRegiao else porRegiao
            .filter { n -> prefixos.any { n.displayName.trim().startsWith(it, ignoreCase = true) } }
            .sortedBy { it.displayName.trim().lowercase() }
        val excluded = ContactExclusionPolicy.excludedNumbers(numbersByContactId, excludedContactIds)
        val filtrados = ContactExclusionPolicy.keep(porPrefixo, excluded)
        val lotes = normalizer.batch(filtrados)
        val excludedNumbers = todos.count { it.e164 in excluded }
        _ui.value = _ui.value.copy(
            filtrados = filtrados.size,
            lotes = lotes,
            excludedNumbers = excludedNumbers,
            turbo = _ui.value.turbo && excludedNumbers == 0,
        )
    }

    fun iniciar() {
        if (!active()) return
        recomputar()
        if (_ui.value.filtrados < UiState.MIN_DESTINATARIOS) return
        val lotes = _ui.value.lotes
        if (lotes.isEmpty()) return
        AutomationController.start(
            lotes, _ui.value.autoConfirm,
            turbo = _ui.value.turbo && _ui.value.prefixos().isNotEmpty() &&
                _ui.value.excludedNumbers == 0,
            prefixosNome = _ui.value.prefixos(),
        )
        _ui.value = _ui.value.copy(step = Step.RUN)
    }

    fun confirmar(create: Boolean) { if (active()) AutomationController.confirm(create) }
    fun pausar() { if (active()) AutomationController.pause() }
    fun retomar() { if (active()) AutomationController.resume() }
    fun cancelar() = AutomationController.cancel()
}
