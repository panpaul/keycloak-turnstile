package xyz.ofortune.app.keycloak

import org.jboss.logging.Logger
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

class RegistrationTurnstile : FormAction, FormActionFactory, ConfiguredProvider {
    companion object {
        const val PROVIDER_ID = "registration-turnstile-action"
        const val DEFAULT_ACTION = "register"

        private val REQUIREMENT_CHOICES =
                arrayOf(
                        AuthenticationExecutionModel.Requirement.REQUIRED,
                        AuthenticationExecutionModel.Requirement.DISABLED
                )

        private var LOGGER: Logger = Logger.getLogger(RegistrationTurnstile::class.java)
    }

    override fun create(session: KeycloakSession): FormAction {
        return this
    }

    override fun init(config: Config.Scope) {}

    override fun postInit(factory: KeycloakSessionFactory) {}

    override fun close() {}

    override fun buildPage(context: FormContext, form: LoginFormsProvider) {
        val config = Turnstile.readConfig(context.authenticatorConfig.config, DEFAULT_ACTION)
        if (config == null) {
            form.addError(FormMessage(null, Turnstile.MSG_CAPTCHA_NOT_CONFIGURED))
            return
        }

        val lang = context.session.context.resolveLocale(context.user).toLanguageTag()

        Turnstile.prepareForm(form, config, lang)
    }

    override fun validate(context: ValidationContext) {
        val formData = context.httpRequest.decodedFormParameters
        val captcha = formData.getFirst(Turnstile.CF_TURNSTILE_RESPONSE)

        context.event.detail(Details.REGISTER_METHOD, "form")

        val config = Turnstile.readConfig(context.authenticatorConfig.config, DEFAULT_ACTION)
        if (config == null) {
            context.error(Errors.INVALID_CONFIG)
            context.validationError(
                    formData,
                    listOf(FormMessage(null, Turnstile.MSG_CAPTCHA_NOT_CONFIGURED))
            )
            context.excludeOtherErrors()
        } else if (Validation.isBlank(captcha) ||
                        !Turnstile.validateTurnstile(
                                context.session.getProvider(HttpClientProvider::class.java)
                                        .httpClient,
                                context.connection.remoteAddr,
                                captcha,
                                config,
                                LOGGER,
                                ServicesLogger.LOGGER
                        )
        ) {
            formData.remove(Turnstile.CF_TURNSTILE_RESPONSE)
            context.error(Errors.INVALID_REGISTRATION)
            context.validationError(
                    formData,
                    listOf(FormMessage(null, Turnstile.MSG_CAPTCHA_FAILED))
            )
            context.excludeOtherErrors()
        } else {
            context.success()
        }
    }

    override fun success(context: FormContext) {}

    override fun requiresUser(): Boolean {
        return false
    }

    override fun configuredFor(
            session: KeycloakSession,
            realm: RealmModel,
            user: UserModel
    ): Boolean {
        return true
    }

    override fun setRequiredActions(session: KeycloakSession, realm: RealmModel, user: UserModel) {}

    override fun getId(): String {
        return PROVIDER_ID
    }

    override fun getHelpText(): String {
        return "Adds Cloudflare Turnstile button.  " +
                "Turnstile verify that the entity that is registering is a human.  " +
                "This can only be used on the internet and must be configured after you add it."
    }

    override fun getConfigProperties(): MutableList<ProviderConfigProperty> {
        return Turnstile.CONFIG_PROPERTIES
    }

    override fun getDisplayType(): String {
        return "Turnstile"
    }

    override fun getReferenceCategory(): String {
        return Turnstile.TURNSTILE_REFERENCE_CATEGORY
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
