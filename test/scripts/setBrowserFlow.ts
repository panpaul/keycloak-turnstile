import { BrowserAuthFlow, isTurnstileAuthenticatorConfig, setBrowserAuthBinding, setBrowserFlowTurnstileConfig, type TurnstileAuthenticatorConfig } from "../keycloak";

const browserFlow: BrowserAuthFlow = process.argv[2] as BrowserAuthFlow ?? 'browser';
const turnstileConfig: TurnstileAuthenticatorConfig = process.argv[3] as TurnstileAuthenticatorConfig ?? 'client-visible-pass-server-pass';

async function main() {
    switch (browserFlow) {
        case 'browser': {
            await setBrowserAuthBinding(browserFlow);
            return;
        }
        case 'browser-turnstile': {
            if (!isTurnstileAuthenticatorConfig(turnstileConfig)) {
                throw new Error(`Invalid turnstile config ${turnstileConfig}`);
            }
            await Promise.all(
                [
                    setBrowserAuthBinding(browserFlow),
                    setBrowserFlowTurnstileConfig(turnstileConfig)
                ]
            )
            return;
        }
    }
    throw new Error(`Invalid browser flow ${browserFlow}`);
}

main();