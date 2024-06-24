package xyz.ofortune.app.keycloak

import jakarta.ws.rs.core.Response
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

class LoginTurnstile : UsernamePasswordForm(), Authenticator, AuthenticatorFactory {
    companion object {
        const val PROVIDER_ID = "login-turnstile-action"
        const val DEFAULT_ACTION = "login"

        private val REQUIREMENT_CHOICES = arrayOf(AuthenticationExecutionModel.Requirement.REQUIRED)

        private var LOGGER: Logger = Logger.getLogger(LoginTurnstile::class.java)
    }

    private var config: Turnstile.Configuration? = null
    private var lang: String? = null

    protected override fun createLoginForm(form: LoginFormsProvider): Response {
        Turnstile.prepareForm(form, config, lang)
        return super.createLoginForm(form)
    }

    override public fun authenticate(context: AuthenticationFlowContext) {
        val form = context.form()

        context.event.detail(Details.AUTH_METHOD, "auth_method")

        val configuration = Turnstile.readConfig(context.authenticatorConfig.config, DEFAULT_ACTION)
        if (configuration == null) {
            form.addError(FormMessage(null, Turnstile.MSG_CAPTCHA_NOT_CONFIGURED))
            return
        }
        val language = context.session.context.resolveLocale(context.user).toLanguageTag()

        Turnstile.prepareForm(form, configuration, language)
        config = configuration
        lang = language

        super.authenticate(context)
    }

    override public fun action(context: AuthenticationFlowContext) {
        val formData = context.httpRequest.decodedFormParameters
        val captcha = formData.getFirst(Turnstile.CF_TURNSTILE_RESPONSE)

        context.event.detail(Details.AUTH_METHOD, "auth_method")

        val configuration = Turnstile.readConfig(context.authenticatorConfig.config, DEFAULT_ACTION)
        if (configuration == null) {
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, Turnstile.MSG_CAPTCHA_NOT_CONFIGURED)
            )
            return
        }

        if (Validation.isBlank(captcha) ||
                        !Turnstile.validateTurnstile(
                                context.session.getProvider(HttpClientProvider::class.java)
                                        .httpClient,
                                context.connection.remoteAddr,
                                captcha,
                                configuration,
                                LOGGER,
                                ServicesLogger.LOGGER
                        )
        ) {
            formData.remove(Turnstile.CF_TURNSTILE_RESPONSE)
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, Turnstile.MSG_CAPTCHA_FAILED)
            )
            return
        } else {
            super.action(context)
        }
    }

    override fun create(session: KeycloakSession?): Authenticator {
        return this
    }

    override fun init(config: Config.Scope) {}

    override fun postInit(factory: KeycloakSessionFactory) {}

    override fun close() {}

    override fun getId(): String {
        return PROVIDER_ID
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

    public override fun getDisplayType(): String {
        return "Turnstile Username Password Form"
    }

    public override fun getHelpText(): String {
        return "Validates a username and password from a form and adds Cloudflare Turnstile button."
    }

    public override fun getConfigProperties(): MutableList<ProviderConfigProperty> {
        return Turnstile.CONFIG_PROPERTIES
    }

    public override fun isUserSetupAllowed(): Boolean {
        return false
    }
}
