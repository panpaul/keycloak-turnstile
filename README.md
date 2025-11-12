# keycloak-turnstile

This project is **no longer actively maintained**. If you're interested in it, check out the [fork](https://github.com/systemofapwne/keycloak-turnstile)

---

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
--- login-base.ftl
+++ login.ftl
@@ -48,6 +48,14 @@
 
                     </div>
 
+                    <#if captchaRequired??>
+                        <div class="form-group">
+                            <div class="${properties.kcInputWrapperClass!}">
+                                <div class="cf-turnstile" data-sitekey="${captchaSiteKey}" data-action="${captchaAction}" data-language="${captchaLanguage}"></div>
+                            </div>
+                        </div>
+                    </#if>
+
                     <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                         <div id="kc-form-options">
                             <#if realm.rememberMe && !usernameHidden??>

```

```diff
--- register-base.ftl
+++ register.ftl
@@ -73,10 +73,10 @@
 
             <@registerCommons.termsAcceptance/>
 
-            <#if recaptchaRequired?? && (recaptchaVisible!false)>
+            <#if captchaRequired??>
                 <div class="form-group">
                     <div class="${properties.kcInputWrapperClass!}">
-                        <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}" data-action="${recaptchaAction}"></div>
+                        <div class="cf-turnstile" data-sitekey="${captchaSiteKey}" data-action="${captchaAction}" data-language="${captchaLanguage}"></div>
                     </div>
                 </div>
             </#if>
@@ -88,23 +88,9 @@
                     </div>
                 </div>
 
-                <#if recaptchaRequired?? && !(recaptchaVisible!false)>
-                    <script>
-                        function onSubmitRecaptcha(token) {
-                            document.getElementById("kc-register-form").submit();
-                        }
-                    </script>
-                    <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
-                        <button class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!} g-recaptcha" 
-                            data-sitekey="${recaptchaSiteKey}" data-callback='onSubmitRecaptcha' data-action='${recaptchaAction}' type="submit">
-                            ${msg("doRegister")}
-                        </button>
-                    </div>
-                <#else>
-                    <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
-                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doRegister")}"/>
-                    </div>
-                </#if>
+                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
+                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doRegister")}"/>
+                </div>
             </div>
         </form>
         <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>

```

```diff
--- login-reset-password-base.ftl
+++ login-reset-password.ftl
@@ -17,6 +17,13 @@
                     </#if>
                 </div>
             </div>
+            <#if captchaRequired??>
+                <div class="${properties.kcFormGroupClass!}">
+                    <div class="${properties.kcInputWrapperClass!}">
+                        <div class="cf-turnstile" data-sitekey="${captchaSiteKey}" data-action="${captchaAction}" data-language="${captchaLanguage}"></div>
+                    </div>
+                </div>
+            </#if>
             <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                 <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                     <div class="${properties.kcFormOptionsWrapperClass!}">
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