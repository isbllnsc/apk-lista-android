package com.listalocal.core.contacts

data class RawContactEntry(
    val localId: Long,
    val displayName: String,
    val phones: List<String>,
)
