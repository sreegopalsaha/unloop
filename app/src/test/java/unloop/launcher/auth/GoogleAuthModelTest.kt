package unloop.launcher.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.AuthState
import unloop.launcher.auth.model.UserProfile
import unloop.launcher.auth.network.AuthApiException

class GoogleAuthModelTest {

    @Test
    fun testUserProfile_creation() {
        val user = UserProfile(
            id = "user_12345",
            name = "Test User",
            email = "test@example.com",
            picture = "https://example.com/avatar.jpg"
        )

        assertEquals("user_12345", user.id)
        assertEquals("Test User", user.name)
        assertEquals("test@example.com", user.email)
        assertEquals("https://example.com/avatar.jpg", user.picture)
    }

    @Test
    fun testAuthSession_creation() {
        val user = UserProfile(
            id = "user_12345",
            name = "Test User",
            email = "test@example.com",
            picture = null
        )
        val session = AuthSession(
            token = "jwt.header.payload.signature",
            user = user
        )

        assertEquals("jwt.header.payload.signature", session.token)
        assertEquals(user, session.user)
        assertNull(session.user.picture)
    }

    @Test
    fun testAuthState_transitions() {
        var state: AuthState = AuthState.Idle
        assertEquals(AuthState.Idle, state)

        state = AuthState.Loading
        assertEquals(AuthState.Loading, state)

        val user = UserProfile("id1", "Alice", "alice@example.com")
        val session = AuthSession("app_token_xyz", user)
        state = AuthState.Authenticated(session)
        assertTrue(state is AuthState.Authenticated)
        assertEquals("Alice", (state as AuthState.Authenticated).session.user.name)

        state = AuthState.Error("Network error occurred")
        assertTrue(state is AuthState.Error)
        assertEquals("Network error occurred", (state as AuthState.Error).message)
    }

    @Test
    fun testGoogleAuthExceptions() {
        val cancellation = GoogleAuthCancellationException("Cancelled by user")
        assertTrue(cancellation is GoogleAuthException)
        assertEquals("Cancelled by user", cancellation.message)

        val generalError = GoogleAuthException("Network failure")
        assertEquals("Network failure", generalError.message)
    }

    @Test
    fun testAuthApiResponse_successParsing() {
        val rawJson = """
            {
                "success": true,
                "message": "Google login successful",
                "data": {
                    "token": "signed_application_jwt_token",
                    "user": {
                        "id": "65a4f78b9c1d2e3f4a5b6c7d",
                        "name": "Jane Doe",
                        "email": "jane@example.com",
                        "picture": "https://lh3.googleusercontent.com/avatar.jpg"
                    }
                }
            }
        """.trimIndent()

        val json = JSONObject(rawJson)
        assertTrue(json.getBoolean("success"))

        val data = json.getJSONObject("data")
        val token = data.getString("token")
        assertEquals("signed_application_jwt_token", token)

        val userObj = data.getJSONObject("user")
        val user = UserProfile(
            id = userObj.getString("id"),
            name = userObj.getString("name"),
            email = userObj.getString("email"),
            picture = userObj.optString("picture")
        )

        assertEquals("Jane Doe", user.name)
        assertEquals("jane@example.com", user.email)
        assertEquals("65a4f78b9c1d2e3f4a5b6c7d", user.id)
        assertEquals("https://lh3.googleusercontent.com/avatar.jpg", user.picture)
    }

    @Test
    fun testAuthApiResponse_errorParsing() {
        val errorJson = """
            {
                "success": false,
                "message": "Invalid Google ID token: Token used too late"
            }
        """.trimIndent()

        val json = JSONObject(errorJson)
        assertFalse(json.getBoolean("success"))
        val message = json.getString("message")
        assertEquals("Invalid Google ID token: Token used too late", message)

        val exception = AuthApiException(message, 401)
        assertEquals(401, exception.statusCode)
        assertEquals(message, exception.message)
    }
}
