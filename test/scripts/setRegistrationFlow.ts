import { KEYCLOAK_REALM, getAuthedClient, isTurnstileAuthenticatorConfigPreset, setRegistrationAllowed, setRegistrationAuthBinding, setRegistrationFlowTurnstileConfig, type RegistrationAuthFlow, type TurnstileAuthenticatorConfig } from "../keycloak";

const registrationFlow: RegistrationAuthFlow = process.argv[2] as RegistrationAuthFlow ?? 'registration';
const turnstileConfig: TurnstileAuthenticatorConfig = process.argv[3] as TurnstileAuthenticatorConfig ?? 'client-visible-pass-server-pass';

async function main() {
    switch (registrationFlow) {
        case 'registration': {
            await setRegistrationAllowed(await getAuthedClient(), KEYCLOAK_REALM, true);
            await setRegistrationAuthBinding(await getAuthedClient(), KEYCLOAK_REALM, registrationFlow);
            return;
        }
        case 'registration-turnstile': {
            await setRegistrationAllowed(await getAuthedClient(), KEYCLOAK_REALM, true);
            if (!isTurnstileAuthenticatorConfigPreset(turnstileConfig)) {
                throw new Error(`Invalid turnstile config ${turnstileConfig}`);
            }
            await Promise.all(
                [
                    setRegistrationAuthBinding(await getAuthedClient(), KEYCLOAK_REALM, registrationFlow),
                    setRegistrationFlowTurnstileConfig(await getAuthedClient(), KEYCLOAK_REALM, turnstileConfig)
                ]
            )
            return;
        }
    }

    throw new Error(`Invalid registration flow ${registrationFlow}`);
}

main();