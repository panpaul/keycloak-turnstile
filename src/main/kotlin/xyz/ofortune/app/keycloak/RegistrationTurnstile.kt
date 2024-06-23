package xyz.ofortune.app.keylocak

import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.jboss.logging.Logger;
import org.keycloak.Config
import org.keycloak.authentication.FormAction
import org.keycloak.authentication.FormActionFactory
import org.keycloak.authentication.FormContext
import org.keycloak.authentication.ValidationContext
import org.keycloak.connections.httpclient.HttpClientProvider
import org.keycloak.events.Details
import org.keycloak.events.Errors
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.models.*
import org.keycloak.models.utils.FormMessage
import org.keycloak.provider.ConfiguredProvider
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.services.ServicesLogger
import org.keycloak.services.validation.Validation
import org.keycloak.util.JsonSerialization


class RegistrationTurnstile : FormAction, FormActionFactory, ConfiguredProvider {
    companion object {
        const val PROVIDER_ID = "registration-turnstile-action"

        const val MSG_CAPTCHA_FAILED = "captchaFailed"
        const val MSG_CAPTCHA_NOT_CONFIGURED = "captchaNotConfigured"

        const val CF_TURNSTILE_RESPONSE = "cf-turnstile-response"
        const val TURNSTILE_REFERENCE_CATEGORY = "turnstile"

        const val TURNSTILE_DUMMY_TOKEN = "XXXX.DUMMY.TOKEN.XXXX" // https://developers.cloudflare.com/turnstile/troubleshooting/testing/

        const val SITE_KEY = "site.key"
        const val SITE_SECRET = "secret"
        const val ACTION = "action"
        const val DEFAULT_ACTION = "register"

        private val REQUIREMENT_CHOICES = arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
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
                        "Defaults to 'register'"
                type = ProviderConfigProperty.STRING_TYPE
            },
        )

        private var LOGGER: Logger = Logger.getLogger(RegistrationTurnstile::class.java)
    }

    private fun validateTurnstile(
        context: ValidationContext,
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

    override fun create(session: KeycloakSession): FormAction {
        return this
    }

    override fun init(config: Config.Scope) {
    }

    override fun postInit(factory: KeycloakSessionFactory) {
    }

    override fun close() {
    }

    override fun buildPage(context: FormContext, form: LoginFormsProvider) {
        val isConfigured = context.authenticatorConfig.config?.run {
            listOf(SITE_KEY, SITE_SECRET).all { this[it] != null }
        } ?: false

        if (!isConfigured) {
            form.addError(FormMessage(null, MSG_CAPTCHA_NOT_CONFIGURED))
            return
        }

        val captchaConfig = context.authenticatorConfig
        val siteKey = captchaConfig.config[SITE_KEY]
        val action = captchaConfig.config[ACTION] ?: DEFAULT_ACTION
        val lang = context.session.context.resolveLocale(context.user).toLanguageTag()

        form.setAttribute("captchaRequired", true)
        form.setAttribute("captchaSiteKey", siteKey)
        form.setAttribute("captchaAction", action)
        form.setAttribute("captchaLanguage", lang)
        form.addScript("https://challenges.cloudflare.com/turnstile/v0/api.js")
    }

    override fun validate(context: ValidationContext) {
        val formData = context.httpRequest.decodedFormParameters
        val captcha = formData.getFirst(CF_TURNSTILE_RESPONSE)

        context.event.detail(Details.REGISTER_METHOD, "form")

        if (Validation.isBlank(captcha) || !validateTurnstile(context, captcha, context.authenticatorConfig.config)) {
            formData.remove(CF_TURNSTILE_RESPONSE)
            context.error(Errors.INVALID_REGISTRATION)
            context.validationError(formData, listOf(FormMessage(null, MSG_CAPTCHA_FAILED)))
            context.excludeOtherErrors()
        } else {
            context.success()
        }
    }

    override fun success(context: FormContext) {
    }

    override fun requiresUser(): Boolean {
        return false
    }

    override fun configuredFor(session: KeycloakSession, realm: RealmModel, user: UserModel): Boolean {
        return true
    }

    override fun setRequiredActions(session: KeycloakSession, realm: RealmModel, user: UserModel) {
    }

    override fun getId(): String {
        return PROVIDER_ID
    }

    override fun getHelpText(): String {
        return "Adds Cloudflare Turnstile button.  " +
                "Turnstile verify that the entity that is registering is a human.  " +
                "This can only be used on the internet and must be configured after you add it."
    }

    override fun getConfigProperties(): MutableList<ProviderConfigProperty> {
        return CONFIG_PROPERTIES
    }

    override fun getDisplayType(): String {
        return "Turnstile"
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

    override fun isUserSetupAllowed(): Boolean {
        return false
    }
}
