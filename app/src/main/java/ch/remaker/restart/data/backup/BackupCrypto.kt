package ch.remaker.restart.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password encryption of a backup file, with the platform's own primitives and nothing else.
 *
 * File: "RESTART" and a format byte, the PBKDF2 iteration count, the salt and the nonce (this
 * header is also the GCM additional data, so it cannot be altered), then the AES-256-GCM
 * ciphertext. The key is PBKDF2-HMAC-SHA256 of the password. A wrong password and a damaged file
 * both fail the GCM tag and cannot be told apart.
 */
object BackupCrypto {

    private val MAGIC = "RESTART".toByteArray(Charsets.US_ASCII)
    private const val FORMAT: Byte = 2
    const val ITERATIONS = 600_000
    /** Shortest password accepted for a new backup. */
    const val MIN_PASSWORD = 8
    const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class WrongPasswordException : Exception("Wrong password or damaged file")

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_SIZE).also { random.nextBytes(it) }

    /** True when [bytes] start like an encrypted backup. */
    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** The 32-byte key for [password]; slow on purpose ([iterations] rounds). */
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypts with a fresh salt; the password is needed again to read the file. */
    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val salt = newSalt()
        return encryptWithKey(plain, deriveKey(password, salt, iterations), salt, iterations)
    }

    /** Encrypts with a key derived earlier from the password and [salt] (the automatic backup keeps one). */
    fun encryptWithKey(plain: ByteArray, key: ByteArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 4 + 1 + salt.size + 1 + nonce.size).apply {
            put(MAGIC); put(FORMAT); putInt(iterations)
            put(salt.size.toByte()); put(salt)
            put(nonce.size.toByte()); put(nonce)
        }.array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
        return header + cipher.doFinal(plain)
    }

    /** The plain content, or [WrongPasswordException] for a wrong password or a damaged file. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(file)) throw WrongPasswordException()
        try {
            val buffer = ByteBuffer.wrap(file)
            buffer.position(MAGIC.size)
            if (buffer.get() != FORMAT) throw WrongPasswordException()
            val iterations = buffer.int
            // A damaged count must not make the phone hash for minutes
            if (iterations !in 1..10_000_000) throw WrongPasswordException()
            val salt = ByteArray(buffer.get().toInt() and 0xFF).also { buffer.get(it) }
            val nonce = ByteArray(buffer.get().toInt() and 0xFF).also { buffer.get(it) }
            val header = file.copyOfRange(0, buffer.position())
            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(header)
            }
            return cipher.doFinal(file, buffer.position(), file.size - buffer.position())
        } catch (e: WrongPasswordException) {
            throw e
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (_: Exception) {
            // Truncated header, bad lengths: a damaged file
            throw WrongPasswordException()
        }
    }
}
