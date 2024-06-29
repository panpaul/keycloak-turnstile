package xyz.ofortune.app.keycloak

import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicNameValuePair
import org.jboss.logging.Logger
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.util.JsonSerialization

class Turnstile {
    data class Configuration(val siteKey: String, val secret: String, val action: String)

    companion object {
        const val MSG_CAPTCHA_FAILED = "captchaFailed"
        const val MSG_CAPTCHA_NOT_CONFIGURED = "captchaNotConfigured"

        const val CF_TURNSTILE_RESPONSE = "cf-turnstile-response"
        const val TURNSTILE_REFERENCE_CATEGORY = "turnstile"

        private const val TURNSTILE_DUMMY_TOKEN =
            "XXXX.DUMMY.TOKEN.XXXX" // https://developers.cloudflare.com/turnstile/troubleshooting/testing/

        private const val SITE_KEY = "site.key"
        private const val SITE_SECRET = "secret"
        private const val ACTION = "action"

        val CONFIG_PROPERTIES =
            mutableListOf(
                ProviderConfigProperty().apply {
                    name = SITE_KEY
                    label = "Turnstile Site Key"
                    helpText = "Cloudflare Turnstile Site Key"
                    type = ProviderConfigProperty.STRING_TYPE
                },
                ProviderConfigProperty().apply {
                    name = SITE_SECRET
                    label = "Turnstile Secret"
                    helpText = "Cloudflare Turnstile Secret"
                    type = ProviderConfigProperty.STRING_TYPE
                },
                ProviderConfigProperty().apply {
                    name = ACTION
                    label = "Action"
                    helpText =
                        "A value that can be used to differentiate widgets under the same Site Key in analytics."
                    type = ProviderConfigProperty.STRING_TYPE
                },
            )

        private val LOGGER: Logger = Logger.getLogger(Turnstile::class.java)

        fun validate(
            config: Configuration,
            captcha: String,
            remoteAddr: String,
            httpClient: CloseableHttpClient
        ): Boolean {
            val post =
                HttpPost("https://challenges.cloudflare.com/turnstile/v0/siteverify").apply {
                    entity =
                        UrlEncodedFormEntity(
                            listOf(
                                BasicNameValuePair("secret", config.secret),
                                BasicNameValuePair("response", captcha),
                                BasicNameValuePair("remoteip", remoteAddr)
                            ),
                            "UTF-8"
                        )
                }

            return try {
                httpClient.execute(post).use { response ->
                    val content = response.entity.content
                    val json = JsonSerialization.readValue(content, Map::class.java)
                    LOGGER.debugf("Turnstile response: %s", json)
                    json["success"] == true &&
                            (captcha == TURNSTILE_DUMMY_TOKEN || json["action"] == config.action)
                }
            } catch (e: Exception) {
                LOGGER.errorf(e, "Failed to validate Turnstile response: %s", e.message)
                false
            }
        }

        fun readConfig(config: Map<String, String>, defaultAction: String): Configuration? {
            val siteKey = config[SITE_KEY] ?: return null
            val secret = config[SITE_SECRET] ?: return null
            val action = config[ACTION] ?: defaultAction

            return Configuration(siteKey, secret, action)
        }

        fun prepareForm(form: LoginFormsProvider, config: Configuration?, lang: String?): LoginFormsProvider {
            form.addScript("https://challenges.cloudflare.com/turnstile/v0/api.js")
            return form.setAttribute("captchaRequired", true)
                .setAttribute("captchaSiteKey", config?.siteKey)
                .setAttribute("captchaAction", config?.action)
                .setAttribute("captchaLanguage", lang)
        }
    }
}
