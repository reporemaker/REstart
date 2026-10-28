package ch.remaker.restart.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import ch.remaker.common.AppLogger
import ch.remaker.restart.data.AUTO_BACKUP_FAILED
import ch.remaker.restart.data.AUTO_BACKUP_FOLDER
import ch.remaker.restart.data.AUTO_BACKUP_HASH
import ch.remaker.restart.data.AUTO_BACKUP_KEY
import ch.remaker.restart.data.AUTO_BACKUP_LAST
import ch.remaker.restart.data.AUTO_BACKUP_SALT
import ch.remaker.restart.data.AUTO_BACKUP_SECTIONS
import ch.remaker.restart.data.Prefs
import java.security.KeyStore
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The automatic backup: an encrypted file in a folder the user chose (it stays when RE*start is
 * removed), written after each update and at most once a day when something changed; the last
 * [KEEP] are kept. The password is never stored: the key derived from it is kept wrapped by a
 * key in the Android Keystore that cannot leave the phone.
 */
object AutoBackup {

    private const val KEEP = 5
    private const val PREFIX = "RE-start-auto-"
    private const val DAY = 24 * 60 * 60 * 1000L
    private const val WRAP_ALIAS = "restart_auto_backup"
    private const val MIME = "application/octet-stream"

    fun isOn(prefs: Prefs): Boolean =
        prefs.prefsNormal.getString(AUTO_BACKUP_FOLDER, null) != null && prefs.prefsNormal.getString(AUTO_BACKUP_KEY, null) != null

    /** True when the last automatic backup could not be written (folder gone, card removed). */
    fun failed(prefs: Prefs): Boolean = prefs.prefsNormal.getBoolean(AUTO_BACKUP_FAILED, false)

    fun sections(prefs: Prefs): Set<BackupSection> =
        prefs.prefsNormal.getStringSet(AUTO_BACKUP_SECTIONS, null).orEmpty()
            .mapNotNull { name -> BackupSection.entries.firstOrNull { it.name == name } }.toSet()

    /** The folder's name for the settings line, e.g. "Documents". */
    fun folderName(context: Context): String? {
        val folder = Prefs(context).prefsNormal.getString(AUTO_BACKUP_FOLDER, null)?.let(Uri::parse) ?: return null
        return runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: folder.lastPathSegment?.substringAfterLast(':')
    }

    /** Turns it on for [folder] and writes the first backup. Slow (key derivation): off the main thread. */
    fun turnOn(context: Context, folder: Uri, sections: Set<BackupSection>, password: CharArray): Boolean {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        context.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        Prefs(context).prefsNormal.edit(commit = true) {
            putString(AUTO_BACKUP_FOLDER, folder.toString())
            putStringSet(AUTO_BACKUP_SECTIONS, sections.map { it.name }.toSet())
            putString(AUTO_BACKUP_KEY, wrap(key))
            putString(AUTO_BACKUP_SALT, Base64.getEncoder().encodeToString(salt))
            remove(AUTO_BACKUP_HASH)
            remove(AUTO_BACKUP_LAST)
            remove(AUTO_BACKUP_FAILED)
        }
        key.fill(0)
        return run(context, force = true)
    }

    fun turnOff(context: Context) {
        val prefs = Prefs(context)
        prefs.prefsNormal.getString(AUTO_BACKUP_FOLDER, null)?.let { folder ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs.prefsNormal.edit(commit = true) {
            listOf(AUTO_BACKUP_FOLDER, AUTO_BACKUP_SECTIONS, AUTO_BACKUP_KEY, AUTO_BACKUP_SALT, AUTO_BACKUP_HASH, AUTO_BACKUP_LAST, AUTO_BACKUP_FAILED)
                .forEach { remove(it) }
        }
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(WRAP_ALIAS) }
    }

    /**
     * Writes a backup when one is due: [force] after an update or when turned on, otherwise once a
     * day and only when something changed since the last one. Off the main thread.
     */
    fun run(context: Context, force: Boolean = false): Boolean {
        val prefs = Prefs(context)
        if (!isOn(prefs)) return false
        val now = System.currentTimeMillis()
        if (!force && now - prefs.prefsNormal.getLong(AUTO_BACKUP_LAST, 0L) < DAY) return false
        return try {
            val content = BackupManager.collect(context, sections(prefs))
            // The time of writing is left out, or every backup would look changed
            val hash = sha256(BackupCodec.toJson(content.copy(created = 0L)))
            if (!force && hash == prefs.prefsNormal.getString(AUTO_BACKUP_HASH, null)) {
                prefs.prefsNormal.edit { putLong(AUTO_BACKUP_LAST, now) }
                return false
            }
            val key = unwrap(prefs.prefsNormal.getString(AUTO_BACKUP_KEY, null)!!)
            val salt = Base64.getDecoder().decode(prefs.prefsNormal.getString(AUTO_BACKUP_SALT, null))
            val bytes = BackupCrypto.encryptWithKey(BackupCodec.toJson(content).toByteArray(), key, salt)
            key.fill(0)
            write(context, Uri.parse(prefs.prefsNormal.getString(AUTO_BACKUP_FOLDER, null)), bytes)
            prefs.prefsNormal.edit(commit = true) {
                putLong(AUTO_BACKUP_LAST, now)
                putString(AUTO_BACKUP_HASH, hash)
                putBoolean(AUTO_BACKUP_FAILED, false)
            }
            AppLogger.i("AutoBackup", "Written")
            true
        } catch (e: Exception) {
            AppLogger.e("AutoBackup", "Could not write the automatic backup", e)
            prefs.prefsNormal.edit(commit = true) { putBoolean(AUTO_BACKUP_FAILED, true) }
            false
        }
    }

    private fun write(context: Context, folder: Uri, bytes: ByteArray) {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(folder)
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, treeId)
        val name = PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date()) + ".restart"
        val doc = DocumentsContract.createDocument(resolver, parent, MIME, name) ?: error("Could not create $name")
        resolver.openOutputStream(doc, "w")!!.use { it.write(bytes) }

        // Keep the newest few of our own files; nothing else in the folder is touched
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, treeId)
        val ours = mutableListOf<Pair<String, String>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(1) ?: continue
                if (displayName.startsWith(PREFIX) && displayName.endsWith(".restart")) ours += it.getString(0) to displayName
            }
        }
        ours.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun wrap(key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(key))
    }

    private fun unwrap(wrapped: String): ByteArray {
        val bytes = Base64.getDecoder().decode(wrapped)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, bytes, 0, 12))
        }
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
}
