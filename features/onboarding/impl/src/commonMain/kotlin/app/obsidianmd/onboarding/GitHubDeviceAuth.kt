package app.obsidianmd.onboarding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceAuthorization(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    val interval: Int,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    val error: String? = null,
)

sealed interface AuthResult {
    data class Success(val token: String) : AuthResult
    data class Failed(val reason: String) : AuthResult
}

interface DeviceAuth {
    suspend fun requestDeviceCode(): DeviceAuthorization
    suspend fun poll(auth: DeviceAuthorization): AuthResult
}

class GitHubDeviceAuth(
    private val http: HttpClient,
    private val clientId: String,
) : DeviceAuth {

    override suspend fun requestDeviceCode(): DeviceAuthorization =
        http.post("https://github.com/login/device/code") {
            headers { append(HttpHeaders.Accept, "application/json") }
            parameter("client_id", clientId)
            parameter("scope", "repo")
        }.body()

    override suspend fun poll(auth: DeviceAuthorization): AuthResult {
        var interval = auth.interval
        var waited = 0
        while (waited <= auth.expiresIn) {
            delay(interval * 1000L)
            waited += interval
            val resp: TokenResponse = try {
                http.post("https://github.com/login/oauth/access_token") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                    parameter("client_id", clientId)
                    parameter("device_code", auth.deviceCode)
                    parameter("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }.body()
            } catch (e: Exception) {
                // Временная сетевая ошибка (например, DNS-икота): продолжаем опрос до истечения кода —
                // пользователь мог уже авторизоваться, токен появится на следующем опросе.
                continue
            }
            when {
                resp.accessToken != null -> return AuthResult.Success(resp.accessToken)
                resp.error == "authorization_pending" -> Unit
                resp.error == "slow_down" -> interval += 5
                else -> return AuthResult.Failed(resp.error ?: "unknown")
            }
        }
        return AuthResult.Failed("expired")
    }
}
