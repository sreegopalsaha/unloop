package unloop.launcher.subscription.crypto

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class VerifiedEntitlement(
    val userId: String,
    val email: String,
    val planId: String,
    val isLifetime: Boolean,
    val status: String,
    val startsAt: Long,
    val expiresAt: Long?,
    val subscriptionId: String?,
    val syncTimestamp: Long,
    val rawToken: String
) {
    fun isCurrentlyValid(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isLifetime) return true
        if (expiresAt == null) return false
        return nowMillis <= expiresAt
    }
}

object EntitlementCrypto {

    // Base64 encoded SPKI (SubjectPublicKeyInfo) format for ES256 (prime256v1 / P-256)
    // Corresponding to the backend private signing key
    private const val PUBLIC_KEY_SPKI_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETR+6ZghAL2dC9wFuZsBJh3DOhe1t" +
        "FcgawhHowwPHR4kLJz/U1gcYtXlgJP7aa04DJEpA/F97ZtDw4ba9JacBjA=="

    private val publicKey: PublicKey by lazy {
        val keyBytes = Base64.decode(PUBLIC_KEY_SPKI_BASE64, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        keyFactory.generatePublic(spec)
    }

    /**
     * Verifies a server-signed ES256 JWS entitlement token.
     * Validates cryptographic authenticity, account binding, and timestamp validity.
     */
    fun verifyEntitlement(
        token: String?,
        expectedUserId: String,
        lastKnownServerTimeMs: Long = 0L,
        currentWallTimeMs: Long = System.currentTimeMillis()
    ): VerifiedEntitlement? {
        if (token.isNullOrBlank()) return null

        try {
            val parts = token.trim().split(".")
            if (parts.size != 3) {
                return null
            }

            val headerB64 = parts[0]
            val payloadB64 = parts[1]
            val signatureB64 = parts[2]

            // 1. Verify header alg is ES256
            val headerBytes = decodeBase64Url(headerB64)
            val headerJson = JSONObject(String(headerBytes, StandardCharsets.UTF_8))
            val alg = headerJson.optString("alg", "")
            if (alg != "ES256") {
                return null
            }

            // 2. Base64URL decode the 64-byte raw signature (r || s)
            val rawSignature = decodeBase64Url(signatureB64)
            if (rawSignature.size != 64) {
                return null
            }

            // 3. Convert JWS IEEE P1363 signature (64 bytes) to ASN.1 DER format for Android Signature API
            val derSignature = p1363ToDer(rawSignature)

            // 4. Cryptographic ECDSA SHA-256 verification
            val signingInput = "$headerB64.$payloadB64".toByteArray(StandardCharsets.US_ASCII)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(signingInput)

            if (!verifier.verify(derSignature)) {
                return null
            }

            // 5. Parse and validate claims
            val payloadBytes = decodeBase64Url(payloadB64)
            val payloadJson = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))

            val sub = payloadJson.optString("sub", "")
            if (sub != expectedUserId) {
                // Reject token belonging to a different account
                return null
            }

            val email = payloadJson.optString("email", "")
            val planId = payloadJson.optString("planId", "")
            val isLifetime = payloadJson.optBoolean("isLifetime", false)
            val status = payloadJson.optString("status", "none")
            val startsAt = payloadJson.optLong("startsAt", 0L)
            val expiresAt = if (payloadJson.has("expiresAt") && !payloadJson.isNull("expiresAt")) {
                payloadJson.optLong("expiresAt")
            } else {
                null
            }
            val subscriptionId = if (payloadJson.has("subscriptionId") && !payloadJson.isNull("subscriptionId")) {
                payloadJson.optString("subscriptionId")
            } else {
                null
            }
            val syncTimestamp = payloadJson.optLong("syncTimestamp", 0L)

            // 6. Clock rollback protection:
            // Allow up to 10 minutes of clock skew backwards, but if the device clock was rewound
            // significantly earlier than the last observed server timestamp, suspect tampering.
            val maxAllowedSkewMs = 10 * 60 * 1000L
            if (lastKnownServerTimeMs > 0 && currentWallTimeMs < (lastKnownServerTimeMs - maxAllowedSkewMs)) {
                return null
            }

            return VerifiedEntitlement(
                userId = sub,
                email = email,
                planId = planId,
                isLifetime = isLifetime,
                status = status,
                startsAt = startsAt,
                expiresAt = expiresAt,
                subscriptionId = subscriptionId,
                syncTimestamp = syncTimestamp,
                rawToken = token
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Converts a 64-byte raw IEEE P1363 signature (R || S, 32 bytes each)
     * into an ASN.1 DER sequence required by Java's SHA256withECDSA implementation.
     */
    fun p1363ToDer(p1363Signature: ByteArray): ByteArray {
        if (p1363Signature.size != 64) {
            throw IllegalArgumentException("Invalid ES256 signature length. Expected 64 bytes.")
        }

        val rBytes = ByteArray(32)
        val sBytes = ByteArray(32)
        System.arraycopy(p1363Signature, 0, rBytes, 0, 32)
        System.arraycopy(p1363Signature, 32, sBytes, 0, 32)

        val r = BigInteger(1, rBytes)
        val s = BigInteger(1, sBytes)

        val rDer = r.toByteArray()
        val sDer = s.toByteArray()

        val seqStream = ByteArrayOutputStream()
        // Tag 0x02: INTEGER
        seqStream.write(0x02)
        seqStream.write(rDer.size)
        seqStream.write(rDer)

        seqStream.write(0x02)
        seqStream.write(sDer.size)
        seqStream.write(sDer)

        val seqBytes = seqStream.toByteArray()

        val derStream = ByteArrayOutputStream()
        // Tag 0x30: SEQUENCE
        derStream.write(0x30)
        derStream.write(seqBytes.size)
        derStream.write(seqBytes)

        return derStream.toByteArray()
    }

    private fun decodeBase64Url(input: String): ByteArray {
        var str = input.trim()
        val remainder = str.length % 4
        if (remainder == 2) str += "=="
        else if (remainder == 3) str += "="
        return Base64.decode(str, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
