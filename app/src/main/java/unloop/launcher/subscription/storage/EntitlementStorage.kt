package unloop.launcher.subscription.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EntitlementStorage(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "unloop_entitlement_secure_prefs"
        private const val KEYSTORE_ALIAS = "unloop_entitlement_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        private const val PREF_KEY_ENCRYPTED_TOKEN = "enc_token"
        private const val PREF_KEY_IV = "enc_iv"
        private const val PREF_KEY_USER_ID = "entitlement_user_id"
        private const val PREF_KEY_LAST_SERVER_TIME = "last_server_time"
        private const val PREF_KEY_LAST_ELAPSED_REALTIME = "last_elapsed_realtime"
        private const val PREF_KEY_CACHED_PLANS = "cached_plans_json"

        @Volatile
        private var instance: EntitlementStorage? = null

        fun getInstance(context: Context): EntitlementStorage {
            return instance ?: synchronized(this) {
                instance ?: EntitlementStorage(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
                return entry?.secretKey
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            // AndroidKeyStore might not be available in local unit test JVM environment
            null
        }
    }

    private fun encrypt(plainText: String): Pair<String, String>? {
        val secretKey = getOrCreateSecretKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val cipherBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            Pair(cipherBase64, ivBase64)
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(cipherBase64: String, ivBase64: String): String? {
        val secretKey = getOrCreateSecretKey() ?: return null
        return try {
            val cipherText = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveEntitlement(token: String, userId: String, serverTimeMs: Long) {
        val pair = encrypt(token)
        val editor = prefs.edit()
            .putString(PREF_KEY_USER_ID, userId)
            .putLong(PREF_KEY_LAST_SERVER_TIME, serverTimeMs)
            .putLong(PREF_KEY_LAST_ELAPSED_REALTIME, SystemClock.elapsedRealtime())

        if (pair != null) {
            editor.putString(PREF_KEY_ENCRYPTED_TOKEN, pair.first)
            editor.putString(PREF_KEY_IV, pair.second)
        } else {
            // Fallback for JVM test runner or devices without keystore
            editor.putString(PREF_KEY_ENCRYPTED_TOKEN, Base64.encodeToString(token.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP))
            editor.remove(PREF_KEY_IV)
        }
        editor.apply()
    }

    fun getCachedEntitlementToken(expectedUserId: String): String? {
        val savedUserId = prefs.getString(PREF_KEY_USER_ID, null)
        if (savedUserId != expectedUserId) {
            // Token belongs to another account or is unset
            return null
        }

        val encToken = prefs.getString(PREF_KEY_ENCRYPTED_TOKEN, null) ?: return null
        val iv = prefs.getString(PREF_KEY_IV, null)

        return if (iv != null) {
            decrypt(encToken, iv)
        } else {
            try {
                String(Base64.decode(encToken, Base64.NO_WRAP), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun getLastServerTime(): Long {
        return prefs.getLong(PREF_KEY_LAST_SERVER_TIME, 0L)
    }

    fun clearEntitlement() {
        prefs.edit()
            .remove(PREF_KEY_ENCRYPTED_TOKEN)
            .remove(PREF_KEY_IV)
            .remove(PREF_KEY_USER_ID)
            .remove(PREF_KEY_LAST_SERVER_TIME)
            .remove(PREF_KEY_LAST_ELAPSED_REALTIME)
            .apply()
    }

    fun saveCachedPlans(plansJson: String) {
        prefs.edit().putString(PREF_KEY_CACHED_PLANS, plansJson).apply()
    }

    fun getCachedPlans(): String? {
        return prefs.getString(PREF_KEY_CACHED_PLANS, null)
    }
}
