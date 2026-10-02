package com.listalocal.core.contacts

import com.listalocal.core.selectors.TargetApp

/**
 * Plataforma que o usuário escolheu usar nesta operação.
 * A seleção acontece na ConsentScreen e determina todo o fluxo.
 */
enum class Plataforma(
    val titulo: String,
    val descricao: String,
    val targetApp: TargetApp,
) {
    INSTAGRAM(
        titulo = "Instagram",
        descricao = "Envia DMs ou monta a lista Amigos Próximos para seus seguidores.",
        targetApp = TargetApp.INSTAGRAM,
    ),
    WHATSAPP(
        titulo = "WhatsApp",
        descricao = "Envia mensagens para contatos salvos na agenda do celular.",
        targetApp = TargetApp.WHATSAPP,
    ),
}
