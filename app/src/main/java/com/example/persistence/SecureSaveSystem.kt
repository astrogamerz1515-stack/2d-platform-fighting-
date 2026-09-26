package com.example.persistence

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private object SafeLog {
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        System.err.println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
        tr?.printStackTrace(System.err)
    }
    fun w(tag: String, msg: String) {
        System.err.println("[$tag] WARN: $msg")
    }
    fun i(tag: String, msg: String) {
        println("[$tag] INFO: $msg")
    }
}

/**
 * Production-grade, tamper-proof local Save/Load system for 2D Platform Fighter.
 * Implements AES-256-GCM authenticated encryption, SHA-256 payload integrity validation,
 * and crash-resilient atomic file writing.
 */
class SecureSaveSystem(
    private val saveDirectory: File,
    private val devicePassphrase: String = "BRAWL_ARENA_SECURE_ENGINE_V1_SECRET"
) {

    private val saveFileName = "savegame.dat"
    private val tempFileName = "savegame.dat.tmp"
    private val backupFileName = "savegame.dat.bak"

    private val magicHeader = 0x4252574C // "BRWL"
    private val currentFormatVersion = 1
    private val gcmTagLengthBits = 128
    private val ivLengthBytes = 12
    private val saltLengthBytes = 16
    private val pbkdf2Iterations = 10000

    private val fileLock = Any()

    /**
     * Secondary constructor for standard Android Context files directory.
     */
    constructor(context: Context) : this(context.filesDir)

    private fun getSecretKeyFactory(): SecretKeyFactory {
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: Exception) {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
    }

    /**
     * Atomically writes and encrypts GameSaveData to disk.
     * Prevents corruption if app is killed mid-write by writing to .tmp first and fsyncing.
     */
    fun save(data: GameSaveData): Boolean {
        synchronized(fileLock) {
            val mainFile = File(saveDirectory, saveFileName)
            val tempFile = File(saveDirectory, tempFileName)
            val backupFile = File(saveDirectory, backupFileName)

            try {
                if (!saveDirectory.exists()) {
                    saveDirectory.mkdirs()
                }

                // Update metadata before serialization
                data.lastSavedTimestamp = System.currentTimeMillis()
                val jsonString = data.toJsonString()

                // Generate random cryptographic salt & IV
                val secureRandom = SecureRandom()
                val salt = ByteArray(saltLengthBytes)
                secureRandom.nextBytes(salt)

                val iv = ByteArray(ivLengthBytes)
                secureRandom.nextBytes(iv)

                // Derive 256-bit AES key via PBKDF2
                val keySpec = PBEKeySpec(devicePassphrase.toCharArray(), salt, pbkdf2Iterations, 256)
                val factory = getSecretKeyFactory()
                val keyBytes = factory.generateSecret(keySpec).encoded
                val secretKey = SecretKeySpec(keyBytes, "AES")

                // Encrypt payload using AES/GCM/NoPadding
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(gcmTagLengthBits, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

                val plaintextBytes = jsonString.toByteArray(Charsets.UTF_8)
                val ciphertextWithTag = cipher.doFinal(plaintextBytes)

                // Calculate SHA-256 integrity hash over ciphertext
                val sha256 = MessageDigest.getInstance("SHA-256")
                val integrityDigest = sha256.digest(ciphertextWithTag)

                // Build binary packet
                val bufferSize = 4 + 4 + saltLengthBytes + ivLengthBytes + 4 + ciphertextWithTag.size + integrityDigest.size
                val byteBuffer = ByteBuffer.allocate(bufferSize)
                byteBuffer.putInt(magicHeader)
                byteBuffer.putInt(currentFormatVersion)
                byteBuffer.put(salt)
                byteBuffer.put(iv)
                byteBuffer.putInt(ciphertextWithTag.size)
                byteBuffer.put(ciphertextWithTag)
                byteBuffer.put(integrityDigest)

                // 1. Write to temporary file first
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val fos = FileOutputStream(tempFile)
                try {
                    fos.write(byteBuffer.array())
                    fos.flush()
                    try {
                        fos.fd.sync()
                    } catch (_: Exception) {}
                } finally {
                    fos.close()
                }

                // 2. Backup existing save file if present
                if (mainFile.exists()) {
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }
                    mainFile.copyTo(backupFile, overwrite = true)
                }

                // 3. Atomic rename .tmp -> .dat
                if (!tempFile.renameTo(mainFile)) {
                    tempFile.copyTo(mainFile, overwrite = true)
                    tempFile.delete()
                }

                // Ensure a verified backup exists even on initial creation for fail-safe recovery
                if (!backupFile.exists() && mainFile.exists()) {
                    mainFile.copyTo(backupFile, overwrite = true)
                }

                return true
            } catch (e: Exception) {
                SafeLog.e("SecureSaveSystem", "Failed to atomically save encrypted file", e)
                return false
            }
        }
    }

    /**
     * Reads, verifies, and decrypts the save file.
     * Automatically attempts backup recovery if tampering or corruption is detected.
     */
    fun load(): GameSaveData {
        synchronized(fileLock) {
            val mainFile = File(saveDirectory, saveFileName)
            val backupFile = File(saveDirectory, backupFileName)

            if (mainFile.exists()) {
                val primaryResult = decryptAndDeserialize(mainFile)
                if (primaryResult != null) {
                    return primaryResult
                }
                SafeLog.w("SecureSaveSystem", "Primary save corrupted or tampered! Attempting backup recovery...")
            }

            // Attempt backup recovery
            if (backupFile.exists()) {
                val backupResult = decryptAndDeserialize(backupFile)
                if (backupResult != null) {
                    SafeLog.i("SecureSaveSystem", "Successfully recovered state from backup file.")
                    backupFile.copyTo(mainFile, overwrite = true)
                    return backupResult
                }
                SafeLog.e("SecureSaveSystem", "Backup save is also corrupted or tampered!")
            }

            // Fallback: create fresh, valid default profile
            val freshData = GameSaveData()
            save(freshData)
            return freshData
        }
    }

    /**
     * Resets the persistent save file to pristine factory state.
     */
    fun resetSave(): GameSaveData {
        synchronized(fileLock) {
            val mainFile = File(saveDirectory, saveFileName)
            val tempFile = File(saveDirectory, tempFileName)
            val backupFile = File(saveDirectory, backupFileName)

            if (mainFile.exists()) mainFile.delete()
            if (tempFile.exists()) tempFile.delete()
            if (backupFile.exists()) backupFile.delete()

            val fresh = GameSaveData()
            save(fresh)
            return fresh
        }
    }

    private fun decryptAndDeserialize(file: File): GameSaveData? {
        try {
            val fileBytes = file.readBytes()
            if (fileBytes.size < 4 + 4 + saltLengthBytes + ivLengthBytes + 4 + 32) {
                SafeLog.e("SecureSaveSystem", "Save file truncated (${fileBytes.size} bytes)")
                return null
            }

            val buffer = ByteBuffer.wrap(fileBytes)
            val magic = buffer.getInt()
            if (magic != magicHeader) {
                SafeLog.e("SecureSaveSystem", "Invalid magic header: 0x${Integer.toHexString(magic)}")
                return null
            }

            val version = buffer.getInt()
            if (version > currentFormatVersion) {
                SafeLog.e("SecureSaveSystem", "Unsupported save format version: $version")
                return null
            }

            val salt = ByteArray(saltLengthBytes)
            buffer.get(salt)

            val iv = ByteArray(ivLengthBytes)
            buffer.get(iv)

            val cipherLength = buffer.getInt()
            if (cipherLength <= 0 || cipherLength > fileBytes.size) {
                SafeLog.e("SecureSaveSystem", "Invalid cipher payload length: $cipherLength")
                return null
            }

            val ciphertextWithTag = ByteArray(cipherLength)
            buffer.get(ciphertextWithTag)

            val expectedDigest = ByteArray(32)
            buffer.get(expectedDigest)

            // Verify SHA-256 integrity hash first
            val sha256 = MessageDigest.getInstance("SHA-256")
            val computedDigest = sha256.digest(ciphertextWithTag)
            if (!MessageDigest.isEqual(expectedDigest, computedDigest)) {
                SafeLog.e("SecureSaveSystem", "Tampering detected! SHA-256 digest mismatch.")
                return null
            }

            // Derive key
            val keySpec = PBEKeySpec(devicePassphrase.toCharArray(), salt, pbkdf2Iterations, 256)
            val factory = getSecretKeyFactory()
            val keyBytes = factory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // Decrypt with AES/GCM/NoPadding (GCM will reject modified or fabricated ciphertext tags)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(gcmTagLengthBits, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val plaintextBytes = cipher.doFinal(ciphertextWithTag)
            val jsonString = String(plaintextBytes, Charsets.UTF_8)

            return GameSaveData.fromJsonString(jsonString)
        } catch (e: Exception) {
            SafeLog.e("SecureSaveSystem", "Decryption failed (Authentication Tag failed or file modified)", e)
            return null
        }
    }
}

