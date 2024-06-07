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
