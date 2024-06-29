# keycloak-turnstile

This Keycloak plugin enables [Cloudflare Turnstile](https://developers.cloudflare.com/turnstile/) support.

The code is derived from the [vanilla implementation](https://github.com/keycloak/keycloak/blob/main/services/src/main/java/org/keycloak/authentication/forms/RegistrationRecaptcha.java) of reCAPTCHA in Keycloak.

## Usage

1. Download from [releases](https://github.com/panpaul/keycloak-turnstile/releases)
2. Place the JAR with the suffix `with-dependencies.jar` into the `providers` directory of your Keycloak installation
3. Configure/Modify your themes
4. Create your own theme
5. Go to the Keycloak Admin Console
    1. Navigate to `Authentication` -> `Flows` -> `registration`
    2. Duplicate the `registration` flow: `Action` -> `Duplicate`
    3. Remove `Recaptcha` from the new flow
    4. On the `registration form`, click `+` -> `Add Step` -> `Turnstile`
    5. Enable and fill in the site key and secret key
    6. Navigate to `Realm Settings` -> `Themes` -> `Login Theme` and select your theme
    7. Navigate to `Realm Settings` -> `Security Defenses` -> `Content-Security-Policy` and add `https://challenges.cloudflare.com` to `frame-src`
6. Done!

## Compile

### Plugin

```bash
mvn clean compile package
```

### Theme
 
If you want to use the default theme, you could just download `turnstile-login-theme-*.jar` and place it in the `providers` directory.

Or you could check out the [theme](./theme) directory and modify it to your needs.

```diff
--- a/base-register.ftl
+++ b/register.ftl
@@ -69,10 +69,10 @@
 
             <@registerCommons.termsAcceptance/>
 
-            <#if recaptchaRequired??>
+            <#if captchaRequired??>
                 <div class="form-group">
                     <div class="${properties.kcInputWrapperClass!}">
-                        <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}"></div>
+                        <div class="cf-turnstile" data-sitekey="${captchaSiteKey}" data-action="${captchaAction}" data-language="${captchaLanguage}"></div>
                     </div>
                 </div>
             </#if>

```

### Running E2E Playwright tests (requires Docker)

The playwright tests can either be run inside a docker container, or directly.  In either case, the keycloak container must be running in order for the tests to have something to connect to.

The keycloak container includes the providers built by this repo, as well as a sample realm that contains flows for login and registration using turnstile.  By default, the turnstile flows are not bound, so the default flows will be used.  The two turnstile flows are called 'browser-turnstile' and 'registration-turnstile'.

You may log in to the admin console at http://localhost:8080/admin/turnstile/console using the username `admin` and password `D3v3l0pm3nt!` when the container is running.  You may also access the account console at http://localhost:8080/realms/turnstile/account.

#### Playwright via Docker-Compose

Build and run the keycloak container, then run the playwright tests:
`docker compose -f ./docker-compose.dev.yml run --rm --build playwright`

#### Playwright directly

Prerequisites:
- NodeJS (21)

Install the playwright browsers (from the test directory):
```
npm install
npx playwright install --with-deps
```

Run the preconfigured keycloak container (from the repository root):
```
docker compose -f ./docker-compose.dev.yml up -d --build --wait keycloak
```

When this completes, you should be able to access keycloak via http://localhost:8080/admin/turnstile/console (see above for credentials).

Then, to run the tests (from the test directory):
```
npm test
```

#### Teardown

You can remove the keycloak (and playwright) container and all associated data using the following command (from the repository root):
```
docker compose -f docker-compose.dev.yml down --volumes --remove-orphans
```