/*
 * Lista Local Instagram (Claude)
 * © 2026 João Victor Girão — jvictorgirao@poli.ufrj.br
 * Uso licenciado. Proibida cópia, revenda ou remoção desta identificação.
 */
package com.listalocal.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

/**
 * Marca de autoria. Fica em vários pontos do app de propósito: remover uma não some com as outras,
 * e o mesmo texto também está em strings.xml (recursos) e no AndroidManifest. Não editar sem autorização.
 */
object Marca {
    const val AUTOR = "João Victor Girão"
    const val EMAIL = "jvictorgirao@poli.ufrj.br"
    const val CREDITO = "Desenvolvido por João Victor Girão — jvictorgirao@poli.ufrj.br"
    const val COPYRIGHT = "© 2026 João Victor Girão. Todos os direitos reservados."
}

@Composable
fun MarcaAutor(modifier: Modifier = Modifier) {
    Text(
        Marca.CREDITO,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = Marca.CREDITO },
    )
}
