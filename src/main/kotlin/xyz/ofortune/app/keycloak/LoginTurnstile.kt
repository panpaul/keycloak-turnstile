package xyz.ofortune.app.keycloak

import jakarta.ws.rs.core.Response
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm
import org.keycloak.connections.httpclient.HttpClientProvider
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.models.*
import org.keycloak.models.utils.FormMessage
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.services.ServicesLogger
import org.keycloak.services.validation.Validation
import org.keycloak.util.JsonSerialization

class LoginTurnstile : UsernamePasswordForm(), Authenticator, AuthenticatorFactory {
    companion object {
        const val PROVIDER_ID = "login-turnstile-action"

        const val MSG_CAPTCHA_FAILED = "captchaFailed"
        const val MSG_CAPTCHA_NOT_CONFIGURED = "captchaNotConfigured"

        const val CF_TURNSTILE_RESPONSE = "cf-turnstile-response"
        const val TURNSTILE_REFERENCE_CATEGORY = "turnstile"

        const val TURNSTILE_DUMMY_TOKEN = "XXXX.DUMMY.TOKEN.XXXX" // https://developers.cloudflare.com/turnstile/troubleshooting/testing/

        const val SITE_KEY = "site.key"
        const val SITE_SECRET = "secret"
        const val ACTION = "action"
        const val DEFAULT_ACTION = "login"

        private val REQUIREMENT_CHOICES = arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED
        )

        private val CONFIG_PROPERTIES = mutableListOf(
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
                helpText = "A value that can be used to differentiate widgets under the same Site Key in analytics. " +
                        "Defaults to 'login'"
                type = ProviderConfigProperty.STRING_TYPE
            },
        )

        private var LOGGER: Logger = Logger.getLogger(LoginTurnstile::class.java)
    }

    private var siteKey: String? = null
    private var action: String? = null
    private var lang: String? = null

    protected override fun createLoginForm(form: LoginFormsProvider): Response {
        form.setAttribute("captchaRequired", true)
        form.setAttribute("captchaSiteKey", siteKey)
        form.setAttribute("captchaAction", action)
        form.setAttribute("captchaLanguage", lang)
        form.addScript("https://challenges.cloudflare.com/turnstile/v0/api.js")

        return super.createLoginForm(form)
    }

    override public fun authenticate(context: AuthenticationFlowContext) {
        val form = context.form()

        context.event.detail(Details.AUTH_METHOD, "auth_method")

        val isConfigured = context.authenticatorConfig.config?.run {
            listOf(SITE_KEY, SITE_SECRET).all { this[it] != null }
        } ?: false

        if (!isConfigured) {
            form.addError(FormMessage(null, MSG_CAPTCHA_NOT_CONFIGURED))
            return
        }

        val captchaConfig = context.authenticatorConfig
        siteKey = captchaConfig.config[SITE_KEY]
        action = captchaConfig.config[ACTION] ?: DEFAULT_ACTION
        lang = context.session.context.resolveLocale(context.user).toLanguageTag()

        form.setAttribute("captchaRequired", true)
        form.setAttribute("captchaSiteKey", siteKey)
        form.setAttribute("captchaAction", action)
        form.setAttribute("captchaLanguage", lang)
        form.addScript("https://challenges.cloudflare.com/turnstile/v0/api.js")

        super.authenticate(context)
    }

    override public fun action(context: AuthenticationFlowContext) {
        val formData = context.httpRequest.decodedFormParameters
        val captcha = formData.getFirst(CF_TURNSTILE_RESPONSE)

        context.event.detail(Details.AUTH_METHOD, "auth_method")

        if (Validation.isBlank(captcha) || !validateTurnstile(context, captcha, context.authenticatorConfig.config)) {
            formData.remove(CF_TURNSTILE_RESPONSE)
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge(context, MSG_CAPTCHA_FAILED))
            return
        } else {
            super.action(context)
        }
    }

    private fun validateTurnstile(
        context: AuthenticationFlowContext,
        captcha: String,
        config: Map<String?, String?>
    ): Boolean {
        val httpClient = context.session.getProvider(HttpClientProvider::class.java).httpClient
        val secret = config[SITE_SECRET]
        val action = config[ACTION] ?: DEFAULT_ACTION

        val post = HttpPost("https://challenges.cloudflare.com/turnstile/v0/siteverify").apply {
            entity = UrlEncodedFormEntity(
                listOf(
                    BasicNameValuePair("secret", secret),
                    BasicNameValuePair("response", captcha),
                    BasicNameValuePair("remoteip", context.connection.remoteAddr)
                ), "UTF-8"
            )
        }

        return try {
            httpClient.execute(post).use { response ->
                val content = response.entity.content
                val json = JsonSerialization.readValue(content, Map::class.java)
                LOGGER.tracef("Turnstile response: %s", json)
                json["success"] == true && (captcha == TURNSTILE_DUMMY_TOKEN || json["action"] == action)
            }
        } catch (e: Exception) {
            // reusing recaptcha logger
            ServicesLogger.LOGGER.recaptchaFailed(e)
            false
        }
    }

    override fun create(session: KeycloakSession?): Authenticator {
        return this
    }

    override fun init(config: Config.Scope) {
    }

    override fun postInit(factory: KeycloakSessionFactory) {
    }

    override fun close() {
    }

    override fun getId(): String {
        return PROVIDER_ID
    }

    override fun getReferenceCategory(): String {
        return TURNSTILE_REFERENCE_CATEGORY
    }

    override fun isConfigurable(): Boolean {
        return true
    }

    override fun getRequirementChoices(): Array<AuthenticationExecutionModel.Requirement> {
        return REQUIREMENT_CHOICES
    }

    public override fun getDisplayType(): String {
        return "Turnstile Username Password Form"
    }

    public override fun getHelpText(): String {
        return "Validates a username and password from a form and adds Cloudflare Turnstile button."
    }

    public override fun getConfigProperties(): MutableList<ProviderConfigProperty> {
        return CONFIG_PROPERTIES
    }

    public override fun isUserSetupAllowed(): Boolean {
        return false
    }
}
