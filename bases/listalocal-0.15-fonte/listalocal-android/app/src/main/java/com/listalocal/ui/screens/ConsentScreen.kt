package com.listalocal.ui.screens

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
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Space

/**
 * Etapa 1 — o que o app faz, em seis frases. O detalhamento de privacidade
 * fica atras de "Ver detalhes": quem quiser ler, le; quem nao, comeca.
 */
@Composable
fun ConsentScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
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

        Text(
            "Desenvolvido por João Girão — jvictorgirao@poli.ufrj.br",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        ScreenTitle(
            "Lista Local",
            "Monta listas de transmissão no seu WhatsApp a partir dos contatos do aparelho.",
        )

        SectionCard {
            Text("Como funciona", style = MaterialTheme.typography.titleMedium)
            Marcador("Todo o processamento acontece neste aparelho.")
            Marcador("O app lê os contatos que você autorizar.")
            Marcador("O serviço é configurado para operar no WhatsApp e no WhatsApp Business.")
            Marcador("Ele monta as listas. Não envia mensagens.")
            Marcador("Você escolhe quem fica fora, sem apagar contatos da agenda.")
            Marcador("Você acompanha a operação e escolhe se confirma cada lista.")
            Marcador("Ao concluir todas as listas, esta instalação encerra o uso.")
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
                    "Sem histórico de conversas",
                    "O fluxo não foi projetado para abrir conversas, escrever ou enviar mensagens.",
                )
                Detalhe(
                    "Registros técnicos reduzidos",
                    "Os registros podem conter um identificador resumido do contato. Não registram o nome ou o número completo.",
                )
                Detalhe(
                    "Seleção conservadora",
                    "Só marca o contato quando a correspondência é exata e única. Em dúvida, pula e registra.",
                )
            }
        }

        PrimaryActionButton(
            text = "Começar configuração",
            onClick = onStart,
            testTag = "consent_start",
        )

        Text(
            "Na próxima etapa, você decide separadamente sobre Contatos e Acessibilidade. " +
                "Pode negar ou revogar cada acesso nas Configurações.",
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
    ListaLocalTheme(darkTheme = false) { ConsentScreen(onStart = {}) }
}

@Preview(name = "Consentimento · escuro", showBackground = true)
@Composable
private fun ConsentPreviewDark() {
    ListaLocalTheme(darkTheme = true) { ConsentScreen(onStart = {}) }
}
