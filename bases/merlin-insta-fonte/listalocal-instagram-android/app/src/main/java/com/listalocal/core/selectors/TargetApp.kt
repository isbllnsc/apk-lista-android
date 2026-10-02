package com.listalocal.core.selectors

/**
 * O aplicativo sobre o qual a automacao pode operar: so o Instagram.
 *
 * O nome do pacote nao e um detalhe: no Android, todo identificador de tela e
 * prefixado por ele (`com.instagram.android:id/direct_thread_header`). O
 * Instagram Lite (`com.instagram.lite`) e outro app, com outras telas, e fica
 * de fora.
 *
 * Esta lista e fechada de proposito: o servico so observa este pacote
 * (ver accessibility_service_config.xml) e so abre este pacote.
 */
enum class TargetApp(val packageName: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    WHATSAPP("com.whatsapp", "WhatsApp"),
    ;

    companion object {
        val PACKAGES: List<String> = entries.map { it.packageName }

        fun fromPackage(packageName: String?): TargetApp? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
