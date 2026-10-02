package com.listalocal.ui.components

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.listalocal.core.contacts.Agenda
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Um contato da agenda com nome e número já normalizado (E.164). */
data class ContatoAgenda(val nome: String, val e164: String)

/**
 * Lê todos os contatos com número de telefone da agenda do aparelho.
 * Filtra e normaliza os números usando a mesma lógica de [Agenda.normalizar].
 * Roda em Dispatchers.IO — chame sempre fora da main thread.
 */
suspend fun lerContatosDaAgenda(context: Context): List<ContatoAgenda> =
    withContext(Dispatchers.IO) {
        val cr: ContentResolver = context.contentResolver
        val resultado = mutableListOf<ContatoAgenda>()

        val cursorContatos = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ),
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        ) ?: return@withContext emptyList()

        cursorContatos.use { c ->
            val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (c.moveToNext()) {
                val contactId = c.getLong(idIdx)
                val name = c.getString(nameIdx)?.ifBlank { null } ?: continue

                val cursorFones = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null,
                ) ?: continue

                cursorFones.use { f ->
                    val numIdx = f.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (f.moveToNext()) {
                        val raw = f.getString(numIdx) ?: continue
                        val e164 = Agenda.normalizar(raw) ?: continue
                        resultado += ContatoAgenda(nome = name, e164 = e164)
                    }
                }
            }
        }

        resultado.distinctBy { it.e164 }
    }

/**
 * Bottom sheet para selecionar contatos da agenda.
 *
 * @param jaAdicionados  números já presentes no campo de texto (para marcar como já adicionados)
 * @param onConfirmar    chamado com a lista de números E.164 novos selecionados
 * @param onDismiss      fecha sem adicionar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContatoPickerDialog(
    jaAdicionados: Set<String>,
    onConfirmar: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var busca by remember { mutableStateOf("") }
    var todosContatos by remember { mutableStateOf<List<ContatoAgenda>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var semPermissao by remember { mutableStateOf(false) }
    var selecionados by remember { mutableStateOf<Set<String>>(emptySet()) }

    val permissaoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            scope.launch {
                todosContatos = lerContatosDaAgenda(context)
                carregando = false
            }
        } else {
            semPermissao = true
            carregando = false
        }
    }

    LaunchedEffect(Unit) {
        val permOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (permOk) {
            todosContatos = lerContatosDaAgenda(context)
            carregando = false
        } else {
            permissaoLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val contatosFiltrados = remember(busca, todosContatos) {
        if (busca.isBlank()) todosContatos
        else todosContatos.filter {
            it.nome.contains(busca, ignoreCase = true) ||
                it.e164.contains(busca, ignoreCase = true)
        }
    }

    val novosSelecionados = remember(selecionados, jaAdicionados) {
        selecionados.filter { it !in jaAdicionados }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {

            // ── Cabeçalho ─────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Selecionar contatos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Campo de busca ────────────────────────────────────────────────
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar por nome ou número") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (busca.isNotEmpty()) {
                        IconButton(onClick = { busca = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            // ── Contador ──────────────────────────────────────────────────────
            Text(
                if (novosSelecionados.isEmpty()) "Nenhum contato novo selecionado"
                else "${novosSelecionados.size} contato${if (novosSelecionados.size == 1) "" else "s"} para adicionar",
                style = MaterialTheme.typography.bodySmall,
                color = if (novosSelecionados.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()

            // ── Lista de contatos ─────────────────────────────────────────────
            when {
                semPermissao -> {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Permissão de contatos negada. Abra as configurações do app para conceder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                }
                carregando -> {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Carregando contatos…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                }
                contatosFiltrados.isEmpty() -> {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        if (busca.isBlank()) "Nenhum contato com número válido encontrado."
                        else "Nenhum resultado para \"$busca\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                }
                else -> {
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(contatosFiltrados, key = { it.e164 }) { contato ->
                            val isChecked = contato.e164 in selecionados || contato.e164 in jaAdicionados
                            val jaEra = contato.e164 in jaAdicionados
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !jaEra) {
                                        selecionados = if (contato.e164 in selecionados) {
                                            selecionados - contato.e164
                                        } else {
                                            selecionados + contato.e164
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null, // clique tratado no Row
                                    enabled = !jaEra,
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        contato.nome,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (jaEra) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        contato.e164,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (jaEra) {
                                    Text(
                                        "já adicionado",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ── Botões de ação ────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onConfirmar(novosSelecionados) },
                    enabled = novosSelecionados.isNotEmpty(),
                ) {
                    Text(
                        if (novosSelecionados.isEmpty()) "Adicionar"
                        else "Adicionar (${novosSelecionados.size})"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
