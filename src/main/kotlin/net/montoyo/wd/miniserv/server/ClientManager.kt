/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.server

import net.montoyo.wd.miniserv.KeyParameters
import net.montoyo.wd.utilities.Log
import java.math.BigInteger
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.Mac
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.SecretKeySpec

class ClientManager {
    private val random = SecureRandom() // SecureRandom is thread safe
    private val keys = HashMap<UUID, ByteArray>()
    private val keyLock = ReentrantReadWriteLock()

    fun getOrGenClientKey(uuid: UUID): ByteArray {
        keyLock.readLock().lock()
        var key = keys[uuid]
        keyLock.readLock().unlock()

        if (key == null) {
            key = ByteArray(KeyParameters.KEY_SIZE)
            random.nextBytes(key)

            keyLock.writeLock().lock()
            keys[uuid] = key
            keyLock.writeLock().unlock()
        }

        return key
    }

    fun getClientKey(uuid: UUID): ByteArray? {
        keyLock.readLock().lock()
        val ret = keys[uuid]
        keyLock.readLock().unlock()
        return ret
    }

    fun revokeClientKey(id: UUID) {
        keyLock.writeLock().lock()
        keys.remove(id)
        keyLock.writeLock().unlock()
    }

    fun generateChallenge(): ByteArray {
        val ret = ByteArray(KeyParameters.CHALLENGE_SIZE)
        random.nextBytes(ret)
        return ret
    }

    fun verifyClient(client: UUID, challenge: ByteArray?, hmac: ByteArray?): Boolean {
        keyLock.readLock().lock()
        val key = keys[client]
        keyLock.readLock().unlock()

        if (challenge == null || hmac == null || key == null) return false

        try {
            val mac = Mac.getInstance(KeyParameters.MAC_ALGORITHM)
            mac.init(SecretKeySpec(key, KeyParameters.MAC_ALGORITHM))
            val result = mac.doFinal(challenge)
            return hmac.contentEquals(result)
        } catch (ex: NoSuchAlgorithmException) {
            Log.warningEx("%s is not supported?!?!", ex, KeyParameters.MAC_ALGORITHM)
        } catch (ex: InvalidKeyException) {
            Log.warningEx("The generated key is invalid", ex)
        }

        return false
    }

    fun encryptClientKey(client: UUID, modulus: ByteArray, exponent: ByteArray): ByteArray? {
        val keySpec = RSAPublicKeySpec(BigInteger(modulus), BigInteger(exponent))

        try {
            val key = KeyFactory.getInstance("RSA").generatePublic(keySpec)
            val cipher = Cipher.getInstance(KeyParameters.RSA_CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, key, random)
            return cipher.doFinal(getOrGenClientKey(client))
        } catch (ex: NoSuchAlgorithmException) {
            Log.warningEx("%s is not supported?!?!", ex, KeyParameters.RSA_CIPHER)
        } catch (ex: NoSuchPaddingException) {
            Log.warningEx("%s is not supported?!?!", ex, KeyParameters.RSA_CIPHER)
        } catch (ex: InvalidKeySpecException) {
            Log.warningEx("A client sent a malicious key", ex)
        } catch (ex: InvalidKeyException) {
            Log.warningEx("A client sent a malicious key", ex)
        } catch (ex: IllegalBlockSizeException) {
            Log.warningEx("Could not encrypt client key", ex)
        } catch (ex: BadPaddingException) {
            Log.warningEx("Could not encrypt client key", ex)
        }

        return null
    }
}
