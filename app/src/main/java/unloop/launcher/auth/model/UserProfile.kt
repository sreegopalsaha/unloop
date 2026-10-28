package unloop.launcher.auth.model

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val picture: String? = null
)
