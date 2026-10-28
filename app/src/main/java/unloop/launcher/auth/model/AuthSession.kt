package unloop.launcher.auth.model

data class AuthSession(
    val token: String,
    val user: UserProfile
)
