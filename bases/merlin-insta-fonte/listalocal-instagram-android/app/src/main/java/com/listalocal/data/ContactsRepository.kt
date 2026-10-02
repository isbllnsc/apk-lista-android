package com.listalocal.data

import android.content.Context
import android.provider.ContactsContract

/**
 * Lê os contatos da agenda do celular (READ_CONTACTS).
 * Retorna pares (displayName, list<phoneNumber>).
 * Executa fora da main thread (IO).
 */
object ContactsRepository {

    data class RawEntry(val id: Long, val name: String, val phones: List<String>)

    fun carregar(context: Context): List<RawEntry> {
        val result = mutableListOf<RawEntry>()
        val cr = context.contentResolver

        val cursor = try {
            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )
        } catch (e: SecurityException) {
            android.util.Log.e("ContactsRepository", "Permissão negada ao ler contatos: ${e.message}")
            null
        } ?: return result

        val byId = LinkedHashMap<Long, RawEntry>()
        cursor.use {
            val idCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol).orEmpty().trim()
                val phone = it.getString(phoneCol).orEmpty().trim()
                val existing = byId[id]
                if (existing != null) {
                    byId[id] = existing.copy(phones = existing.phones + phone)
                } else {
                    byId[id] = RawEntry(id, name, listOf(phone))
                }
            }
        }
        return byId.values.toList()
    }
}
