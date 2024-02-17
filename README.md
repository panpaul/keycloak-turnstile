# keycloak-turnstile

This Keycloak plugin enables [Cloudflare Turnstile](https://developers.cloudflare.com/turnstile/) support.

The code is derived from the [vanilla implementation](https://github.com/keycloak/keycloak/blob/main/services/src/main/java/org/keycloak/authentication/forms/RegistrationRecaptcha.java) of reCAPTCHA in Keycloak.

## Usage

1. Download from [releases](https://github.com/panpaul/keycloak-turnstile/releases)
2. Place the JAR with the suffix `with-dependencies.jar` into the `providers` directory of your Keycloak installation
3. Go to the Keycloak Admin Console
   1. Navigate to `Authentication` -> `Flows` -> `registration`
   2. Duplicate the `registration` flow: `Action` -> `Duplicate`
   3. Remove `Recaptcha` from the new flow
   4. On the `registration form`, click `+` -> `Add Step` -> `Turnstile`
   5. Enable and fill in the site key and secret key
   6. Navigate to `Realm Settings` -> `Security Defenses` -> `Content-Security-Policy` and add `https://challenges.cloudflare.com` to `frame-src`
4. Done!

## Compile

```bash
mvn clean compile package
```
