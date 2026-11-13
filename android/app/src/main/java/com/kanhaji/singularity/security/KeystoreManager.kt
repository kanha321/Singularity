package com.kanhaji.singularity.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricPrompt
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

object KeystoreManager {
    private const val TAG = "SingularityKeystore"
    const val KEY_ALIAS = "singularity_hardware_auth_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val SIGN_ALGORITHM = "SHA256withECDSA"

    fun getOrGeneratePublicKeyBase64(): String? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            Log.i(TAG, "Generating new hardware-backed ECDSA keypair: $KEY_ALIAS")
            generateKeypair()
        }
        val cert = keyStore.getCertificate(KEY_ALIAS) ?: return null
        return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    fun hasKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.containsAlias(KEY_ALIAS)
        } catch (_: Exception) {
            false
        }
    }

    fun generateKeypair(): Boolean {
        return try {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN
            ).apply {
                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                setDigests(KeyProperties.DIGEST_SHA256)
                setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
                setInvalidatedByBiometricEnrollment(true)
            }
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            Log.i(TAG, "Successfully generated hardware-backed ECDSA keypair")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate keypair in AndroidKeystore: ${e.message}", e)
            false
        }
    }

    fun createBiometricCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKeypair()
            }
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
            val signature = Signature.getInstance(SIGN_ALGORITHM).apply {
                initSign(privateKey)
            }
            BiometricPrompt.CryptoObject(signature)
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating BiometricPrompt.CryptoObject: ${e.message}")
            null
        }
    }

    fun signData(cryptoObject: BiometricPrompt.CryptoObject?, data: ByteArray): String? {
        return try {
            val signature = cryptoObject?.signature ?: return null
            signature.update(data)
            val derBytes = signature.sign()
            Base64.encodeToString(derBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Hardware signature generation failed: ${e.message}", e)
            null
        }
    }
}

