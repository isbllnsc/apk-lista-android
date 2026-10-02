package com.listalocal.core.selectors

/**
 * Os aplicativos sobre os quais a automacao pode operar.
 *
 * O nome do pacote nao e um detalhe: no Android, todo identificador de tela e
 * prefixado por ele (`com.whatsapp:id/next_btn` contra
 * `com.whatsapp.w4b:id/next_btn`). Por isso o pacote atravessa o servico
 * inteiro em vez de ficar fixo em uma constante.
 *
 * Esta lista e fechada de proposito: o servico so observa estes pacotes
 * (ver accessibility_service_config.xml) e so abre estes pacotes.
 */
enum class TargetApp(val packageName: String, val label: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
    ;

    companion object {
        val PACKAGES: List<String> = entries.map { it.packageName }

        fun fromPackage(packageName: String?): TargetApp? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
