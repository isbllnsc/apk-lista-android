package com.listalocal.ui.screens

/* © 2026 João Victor Girão — jvictorgirao@poli.ufrj.br. Marca de autoria; não remover. */
import com.listalocal.ui.components.MarcaAutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.listalocal.R
import com.listalocal.core.contacts.Plataforma
import com.listalocal.ui.components.CaixaDeMarcar
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.components.SectionTitle
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Space

/**
 * Etapa 1 — o que o app faz e o risco que ele traz. O detalhamento de
 * privacidade fica atras de "Ver detalhes"; o risco nao: so comeca quem
 * marcar que entendeu.
 */
@Composable
fun ConsentScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    plataforma: Plataforma = Plataforma.INSTAGRAM,
    onPlataforma: (Plataforma) -> Unit = {},
    aceitouRiscos: Boolean = false,
    onAceitarRiscos: (Boolean) -> Unit = {},
) {
    var detalhes by rememberSaveable { mutableStateOf(false) }

    ScreenColumn(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF130B2B), Radii.card)
                .padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.merlin_mark),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.merlin_wordmark),
                contentDescription = "Merlin",
                modifier = Modifier.width(150.dp).height(30.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        }

        MarcaAutor()


        // ── Seletor de plataforma ─────────────────────────────────────────────
        TabRow(selectedTabIndex = Plataforma.entries.indexOf(plataforma)) {
            Plataforma.entries.forEach { p ->
                Tab(
                    selected = plataforma == p,
                    onClick = { onPlataforma(p); onAceitarRiscos(false) },
                    text = { Text(p.titulo) },
                )
            }
        }

        ScreenTitle(
            "Lista Local ${plataforma.titulo}",
            if (plataforma == Plataforma.INSTAGRAM)
                "Envia a sua mensagem aos seus seguidores no Instagram, uma a uma, ou monta a lista Amigos Próximos."
            else
                "Envia a sua mensagem aos seus contatos da agenda pelo WhatsApp, uma a uma.",
        )

        SectionCard {
            SectionTitle("Como funciona")
            Marcador("Todo o processamento acontece neste aparelho, sem internet no app.")
            if (plataforma == Plataforma.INSTAGRAM)
                Marcador("As pessoas vêm do próprio Instagram: seus seguidores ou suas conversas, lidos na tela, sem arquivo.")
            else
                Marcador("As pessoas vêm da sua agenda de contatos, lidas no momento de iniciar, sem arquivo.")
            Marcador("O app guarda só quem recebeu, quem falhou e onde parou; quem pediu para parar fica de fora.")
            Marcador("O serviço de Acessibilidade só opera no ${plataforma.titulo}.")
            if (plataforma == Plataforma.INSTAGRAM)
                Marcador("Cada mensagem vai numa conversa 1:1, aberta pelo @ exato. Nunca em grupo.")
            else
                Marcador("Cada mensagem vai numa conversa 1:1, aberta pelo número do contato. Nunca em grupo.")
            if (plataforma == Plataforma.INSTAGRAM)
                Marcador("Antes de escrever, o app confere o @ da pessoa na conversa.")
            else
                Marcador("Antes de escrever, o app confere o nome do contato na conversa.")
            Marcador("Você escolhe quem fica fora, o texto e a velocidade entre uma pessoa e a próxima.")
            Marcador("Você pode pausar e parar a qualquer momento, também por cima do ${plataforma.titulo}.")
        }

        SectionCard {
            SectionTitle("Riscos")
            if (plataforma == Plataforma.INSTAGRAM) {
                InlineWarning(
                    "Os Termos de Uso do Instagram proíbem o acesso por meios automatizados sem " +
                        "permissão. Este app automatiza o Instagram pela Acessibilidade, então a conta " +
                        "pode sofrer restrição ou desativação. O app não disfarça nada: sem atraso " +
                        "sorteado, sem digitação simulada e sem troca de IP.",
                    tone = WarningTone.ATTENTION,
                )
                Marcador("Envie só para quem segue você e espera a mensagem, com base legal (LGPD).")
                Marcador("Se o Instagram mostrar um aviso de restrição, a fila pausa e só você retoma.")
            } else {
                InlineWarning(
                    "Os Termos de Uso do WhatsApp proíbem o envio massivo de mensagens não solicitadas. " +
                        "Este app automatiza o WhatsApp pela Acessibilidade, então a conta pode sofrer " +
                        "restrição ou banimento. O app não disfarça nada: sem atraso sorteado, sem " +
                        "digitação simulada e sem troca de IP.",
                    tone = WarningTone.ATTENTION,
                )
                Marcador("Envie só para quem tem o seu contato e espera a mensagem, com base legal (LGPD).")
                Marcador("Se o WhatsApp mostrar um aviso de restrição, a fila pausa e só você retoma.")
            }
            CaixaDeMarcar("Entendi os riscos e quero usar mesmo assim.", aceitouRiscos, onAceitarRiscos)
        }

        TextButton(
            onClick = { detalhes = !detalhes },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(if (detalhes) "Ocultar detalhes de privacidade" else "Ver detalhes de privacidade")
        }

        AnimatedVisibility(visible = detalhes) {
            SectionCard(container = MaterialTheme.colorScheme.surfaceContainer) {
                Text("Privacidade", style = MaterialTheme.typography.titleSmall)
                Detalhe(
                    "Sem internet",
                    "O app não pede permissão de rede. A compilação falha se alguém tentar adicioná-la.",
                )
                Detalhe(
                    "Só no aparelho",
                    "Quem recebeu, quem pediu para parar e quem ficou sem confirmação ficam gravados " +
                        "só neste celular, para ninguém receber duas vezes. Sem backup em nuvem.",
                )
                Detalhe(
                    "Registros técnicos reduzidos",
                    if (plataforma == Plataforma.INSTAGRAM)
                        "Os registros técnicos têm só um código curto de cada @. Não registram nome nem texto."
                    else
                        "Os registros técnicos têm só o número normalizado de cada contato. Não registram nome nem texto.",
                )
                Detalhe(
                    "Seleção conservadora",
                    if (plataforma == Plataforma.INSTAGRAM)
                        "Só age quando o @ é exato e único. Em dúvida, pula e registra o motivo."
                    else
                        "Só age quando o número do contato é confirmado na conversa. Em dúvida, pula e registra o motivo.",
                )
            }
        }

        PrimaryActionButton(
            text = "Começar configuração",
            onClick = onStart,
            enabled = aceitouRiscos,
            testTag = "consent_start",
        )

        Text(
            "Na próxima etapa, você decide sobre a Acessibilidade. " +
                "Pode negar ou revogar esse acesso nas Configurações.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )

    }
}

@Composable
private fun Marcador(texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("•", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary)
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Detalhe(titulo: String, corpo: String) {
    Column(Modifier.padding(top = Space.xs)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        Text(
            corpo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Consentimento · claro", showBackground = true)
@Composable
private fun ConsentPreviewLight() {
    ListaLocalTheme(darkTheme = false) { ConsentScreen(onStart = {}, aceitouRiscos = true) }
}

@Preview(name = "Consentimento · escuro", showBackground = true)
@Composable
private fun ConsentPreviewDark() {
    ListaLocalTheme(darkTheme = true) { ConsentScreen(onStart = {}) }
}
