package ch.remaker.restart.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    // Few rounds keep the test fast; the format and the code path are the same
    private val rounds = 1_000
    private val plain = """{"format":2,"notes":"Kapı kodu 4711"}""".toByteArray()

    @Test
    fun theRightPasswordOpensTheFile() {
        val file = BackupCrypto.encrypt(plain, "doğru şifre".toCharArray(), rounds)
        assertTrue(BackupCrypto.isEncrypted(file))
        assertArrayEquals(plain, BackupCrypto.decrypt(file, "doğru şifre".toCharArray()))
    }

    @Test
    fun theTextIsNotInTheFile() {
        val file = BackupCrypto.encrypt(plain, "password1".toCharArray(), rounds)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("4711"))
    }

    @Test
    fun aWrongPasswordIsRefused() {
        val file = BackupCrypto.encrypt(plain, "password1".toCharArray(), rounds)
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(file, "password2".toCharArray())
        }
    }

    @Test
    fun aDamagedFileIsRefused() {
        val file = BackupCrypto.encrypt(plain, "password1".toCharArray(), rounds)
        file[file.size - 5] = (file[file.size - 5] + 1).toByte()
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(file, "password1".toCharArray())
        }
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(file.copyOfRange(0, 12), "password1".toCharArray())
        }
    }

    @Test
    fun aChangedHeaderIsRefused() {
        val file = BackupCrypto.encrypt(plain, "password1".toCharArray(), rounds)
        // The salt is part of the authenticated header
        file[14] = (file[14] + 1).toByte()
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(file, "password1".toCharArray())
        }
    }

    @Test
    fun aKeptKeyWritesFilesThePasswordOpens() {
        // The automatic backup keeps the derived key, not the password
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("password1".toCharArray(), salt, rounds)
        val first = BackupCrypto.encryptWithKey(plain, key, salt, rounds)
        val second = BackupCrypto.encryptWithKey(plain, key, salt, rounds)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, BackupCrypto.decrypt(second, "password1".toCharArray()))
    }

    @Test
    fun plainJsonIsNotTakenForAnEncryptedFile() {
        assertFalse(BackupCrypto.isEncrypted("""{"APP_THEME":"Dark"}""".toByteArray()))
    }
}
