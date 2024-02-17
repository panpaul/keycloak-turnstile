package xyz.ofortune.app.keylocak.authenticator

import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
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
import org.keycloak.services.messages.Messages
import org.keycloak.services.validation.Validation
import org.keycloak.util.JsonSerialization

class RegistrationTurnstile : FormAction, FormActionFactory, ConfiguredProvider {
    companion object {
        const val PROVIDER_ID = "registration-turnstile-action"

        const val CF_TURNSTILE_RESPONSE = "g-recaptcha-response" // using compat mode
        const val TURNSTILE_REFERENCE_CATEGORY = "turnstile"

        const val SITE_KEY = "site.key"
        const val SITE_SECRET = "secret"

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
            })
    }

    private fun validateTurnstile(context: ValidationContext, captcha: String, secret: String?): Boolean {
        val httpClient = context.session.getProvider(HttpClientProvider::class.java).httpClient

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
                json["success"] == true
            }
        } catch (e: Exception) {
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
        val captchaConfig = context.authenticatorConfig
        if (captchaConfig == null || captchaConfig.config == null
            || captchaConfig.config[SITE_KEY] == null
            || captchaConfig.config[SITE_SECRET] == null
        ) {
            form.addError(FormMessage(null, Messages.RECAPTCHA_NOT_CONFIGURED))
            return
        }

        val siteKey = captchaConfig.config[SITE_KEY]
        // NOTICE: reuse recaptcha ftl to avoid modifying the theme
        form.setAttribute("recaptchaRequired", true)
        form.setAttribute("recaptchaSiteKey", siteKey)
        form.addScript("https://challenges.cloudflare.com/turnstile/v0/api.js?compat=recaptcha")
    }

    override fun validate(context: ValidationContext) {
        val formData = context.httpRequest.decodedFormParameters
        val captcha = formData.getFirst(CF_TURNSTILE_RESPONSE)
        val secret = context.authenticatorConfig.config[SITE_SECRET]

        context.event.detail(Details.REGISTER_METHOD, "form")

        if (Validation.isBlank(captcha) || !validateTurnstile(context, captcha, secret)) {
            formData.remove(CF_TURNSTILE_RESPONSE)
            context.error(Errors.INVALID_REGISTRATION)
            context.validationError(formData, listOf(FormMessage(null, Messages.RECAPTCHA_FAILED)))
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
