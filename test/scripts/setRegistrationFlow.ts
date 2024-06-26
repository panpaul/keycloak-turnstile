import { isTurnstileAuthenticatorConfig, setRegistrationAllowed, setRegistrationAuthBinding, setRegistrationFlowTurnstileConfig, type RegistrationAuthFlow, type TurnstileAuthenticatorConfig } from "../keycloak";

const registrationFlow: RegistrationAuthFlow = process.argv[2] as RegistrationAuthFlow ?? 'registration';
const turnstileConfig: TurnstileAuthenticatorConfig = process.argv[3] as TurnstileAuthenticatorConfig ?? 'client-visible-pass-server-pass';

async function main() {
    switch (registrationFlow) {
        case 'registration': {
            await setRegistrationAllowed(true);
            await setRegistrationAuthBinding(registrationFlow);
            return;
        }
        case 'registration-turnstile': {
            await setRegistrationAllowed(true);
            if (!isTurnstileAuthenticatorConfig(turnstileConfig)) {
                throw new Error(`Invalid turnstile config ${turnstileConfig}`);
            }
            await Promise.all(
                [
                    setRegistrationAuthBinding(registrationFlow),
                    setRegistrationFlowTurnstileConfig(turnstileConfig)
                ]
            )
            return;
        }
    }

    throw new Error(`Invalid registration flow ${registrationFlow}`);
}

main();