import { BrowserAuthFlow, KEYCLOAK_REALM, getAuthedClient, isTurnstileAuthenticatorConfigPreset, setBrowserAuthBinding, setBrowserFlowTurnstileConfig, type TurnstileAuthenticatorConfig } from "../keycloak";

const browserFlow: BrowserAuthFlow = process.argv[2] as BrowserAuthFlow ?? 'browser';
const turnstileConfig: TurnstileAuthenticatorConfig = process.argv[3] as TurnstileAuthenticatorConfig ?? 'client-visible-pass-server-pass';

async function main() {
    switch (browserFlow) {
        case 'browser': {
            await setBrowserAuthBinding(await getAuthedClient(), KEYCLOAK_REALM, browserFlow);
            return;
        }
        case 'browser-turnstile': {
            if (!isTurnstileAuthenticatorConfigPreset(turnstileConfig)) {
                throw new Error(`Invalid turnstile config ${turnstileConfig}`);
            }
            await Promise.all(
                [
                    setBrowserAuthBinding(await getAuthedClient(), KEYCLOAK_REALM, browserFlow),
                    setBrowserFlowTurnstileConfig(await getAuthedClient(), KEYCLOAK_REALM, turnstileConfig)
                ]
            )
            return;
        }
    }
    throw new Error(`Invalid browser flow ${browserFlow}`);
}

main();