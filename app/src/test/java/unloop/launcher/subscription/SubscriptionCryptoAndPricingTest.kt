package unloop.launcher.subscription

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import unloop.launcher.subscription.crypto.EntitlementCrypto
import unloop.launcher.subscription.model.PlanBillingType
import unloop.launcher.subscription.model.PricingHelper

class SubscriptionCryptoAndPricingTest {

    // Real ES256 server-signed tokens generated using the backend private key
    private val validMonthlyToken =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0X3VzZXJfNDU2IiwiZW1haWwiOiJ1c2VyQGV4YW1wbGUuY29tIiwicGxhbklkIjoicGxhbl9tb250aGx5IiwiaXNMaWZldGltZSI6ZmFsc2UsInN0YXR1cyI6ImFjdGl2ZSIsInN0YXJ0c0F0IjoxNzAwMDAwMDAwMDAwLCJleHBpcmVzQXQiOjI1MjQ2MDgwMDAwMDAsInN1YnNjcmlwdGlvbklkIjoic3ViX3Rlc3RfMTIzIiwidmVyc2lvbiI6MSwic3luY1RpbWVzdGFtcCI6MTc5MzI3MzY1NDQxMSwiaWF0IjoxNzkzMjczNjU0LCJleHAiOjI1MjQ2MDgwMDB9.MNu12XPuu1PUMlWy0hONM1xGKU91SB2tgnCQRM0XMxYy2s8vBGVP5U_hE38UpzSlzHEYEbVC706NvXfJHxlxMw"

    private val validLifetimeToken =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0X3VzZXJfNzg5IiwiZW1haWwiOiJsaWZldGltZUBleGFtcGxlLmNvbSIsInBsYW5JZCI6InBsYW5fbGlmZXRpbWUiLCJpc0xpZmV0aW1lIjp0cnVlLCJzdGF0dXMiOiJhY3RpdmUiLCJzdGFydHNBdCI6MTcwMDAwMDAwMDAwMCwiZXhwaXJlc0F0IjpudWxsLCJzdWJzY3JpcHRpb25JZCI6bnVsbCwidmVyc2lvbiI6MSwic3luY1RpbWVzdGFtcCI6MTc5MzI3MzY1NDQxOCwiaWF0IjoxNzkzMjczNjU0LCJleHAiOjQ5NDY4NzM2NTR9.xOSxcIYvkmuswsmGYgoLOq3REH_rhWA_GgRVNpe3QKPMc5gE-9OyfYW633BBkBybY3aLMSOeZhRA5N6wuUc2lA"

    @Test
    fun testCentralizedPricingHelper() {
        val defaultPlans = PricingHelper.getDefaultPlans()
        assertEquals(2, defaultPlans.size)

        val monthly = defaultPlans.first { it.id == PricingHelper.PLAN_MONTHLY }
        assertEquals(9900L, monthly.amountPaise)
        assertEquals("₹99", monthly.displayPrice)
        assertEquals("/month", monthly.displayPeriod)
        assertEquals(PlanBillingType.RECURRING, monthly.billingType)

        val lifetime = defaultPlans.first { it.id == PricingHelper.PLAN_LIFETIME }
        assertEquals(69900L, lifetime.amountPaise)
        assertEquals("₹699", lifetime.displayPrice)
        assertEquals("one-time", lifetime.displayPeriod)
        assertEquals(PlanBillingType.ONE_TIME, lifetime.billingType)
    }

    @Test
    fun testParsePlansJson() {
        val serverJson = """
            {
              "success": true,
              "data": {
                "plans": [
                  {
                    "id": "plan_monthly",
                    "name": "Monthly Pro",
                    "amount": 9900,
                    "currency": "INR",
                    "billingType": "recurring",
                    "displayPrice": "₹99",
                    "displayPeriod": "/month",
                    "features": ["Feature A", "Feature B"]
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = PricingHelper.parsePlansJson(serverJson)
        assertEquals(1, parsed.size)
        assertEquals("Monthly Pro", parsed[0].name)
        assertEquals(9900L, parsed[0].amountPaise)
        assertEquals(listOf("Feature A", "Feature B"), parsed[0].features)
    }

    @Test
    fun testVerifyValidMonthlyEntitlementToken() {
        val verified = EntitlementCrypto.verifyEntitlement(
            token = validMonthlyToken,
            expectedUserId = "test_user_456"
        )

        assertNotNull(verified)
        assertEquals("test_user_456", verified!!.userId)
        assertEquals("plan_monthly", verified.planId)
        assertFalse(verified.isLifetime)
        assertEquals(2524608000000L, verified.expiresAt)
        assertEquals("sub_test_123", verified.subscriptionId)
        assertTrue(verified.isCurrentlyValid())
    }

    @Test
    fun testVerifyValidLifetimeEntitlementToken() {
        val verified = EntitlementCrypto.verifyEntitlement(
            token = validLifetimeToken,
            expectedUserId = "test_user_789"
        )

        assertNotNull(verified)
        assertEquals("test_user_789", verified!!.userId)
        assertEquals("plan_lifetime", verified.planId)
        assertTrue(verified.isLifetime)
        assertNull(verified.expiresAt)
        assertTrue(verified.isCurrentlyValid())
    }

    @Test
    fun testRejectTokenForDifferentUser() {
        // Token was issued for test_user_456, cannot be verified for another_user
        val verified = EntitlementCrypto.verifyEntitlement(
            token = validMonthlyToken,
            expectedUserId = "another_user_id"
        )

        assertNull(verified)
    }

    @Test
    fun testRejectTamperedSignature() {
        val parts = validMonthlyToken.split(".")
        // Tamper signature with bad bytes
        val tamperedToken = "${parts[0]}.${parts[1]}.badsignaturebytesthatdonotmatchanything"

        val verified = EntitlementCrypto.verifyEntitlement(
            token = tamperedToken,
            expectedUserId = "test_user_456"
        )

        assertNull(verified)
    }

    @Test
    fun testDetectClockRollback() {
        // Last known server time was at timestamp 1793273654411
        // If current device wall clock is set back to year 2020 (1577836800000), rollback should be rejected
        val simulatedClockRolledBackTime = 1577836800000L
        val lastKnownServerTime = 1793273654411L

        val verified = EntitlementCrypto.verifyEntitlement(
            token = validMonthlyToken,
            expectedUserId = "test_user_456",
            lastKnownServerTimeMs = lastKnownServerTime,
            currentWallTimeMs = simulatedClockRolledBackTime
        )

        assertNull(verified)
    }

    @Test
    fun testExpiredMonthlyEntitlement() {
        val verified = EntitlementCrypto.verifyEntitlement(
            token = validMonthlyToken,
            expectedUserId = "test_user_456"
        )

        assertNotNull(verified)
        // Simulated future time after expiresAt (2524608000000L)
        val futureTimeAfterExpiration = 2600000000000L
        assertFalse(verified!!.isCurrentlyValid(futureTimeAfterExpiration))
    }
}
