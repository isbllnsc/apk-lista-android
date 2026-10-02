package com.listalocal.data

import android.content.Context
import android.provider.ContactsContract
import com.listalocal.core.contacts.RawContact

/** Le os contatos do aparelho via ContactsContract. Requer READ_CONTACTS. */
class ContactsRepository(private val context: Context) {

    fun readAll(): List<RawContact> {
        val byId = LinkedHashMap<Long, Pair<String, MutableList<String>>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(uri, cols, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val name = c.getString(nameIdx) ?: ""
                val num = c.getString(numIdx) ?: continue
                val entry = byId.getOrPut(id) { name to mutableListOf() }
                entry.second.add(num)
            }
        }
        return byId.map { (id, pair) -> RawContact(id, pair.first, pair.second) }
    }
}
