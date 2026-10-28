package unloop.launcher.auth.model

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Authenticated(val session: AuthSession) : AuthState
    data class Error(val message: String) : AuthState
}
