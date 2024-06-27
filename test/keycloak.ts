import KcAdminClient from '@keycloak/keycloak-admin-client';
import type { Credentials } from '@keycloak/keycloak-admin-client/lib/utils/auth';

export const KEYCLOAK_URL: string = process.env.KEYCLOAK_URL ?? "http://localhost:8080";
export const KEYCLOAK_ADMIN_USERNAME: string = process.env.KEYCLOAK_ADMIN_USERNAME ?? "admin";
export const KEYCLOAK_ADMIN_PASSWORD: string = process.env.KEYCLOAK_ADMIN_PASSWORD ?? "D3v3l0pm3nt!";
export const KEYCLOAK_REALM: string = process.env.KEYCLOAK_REALM ?? "turnstile";

export const TURNSTILE_SITEKEY: string = process.env.TURNSTILE_SITEKEY ?? "";
export const TURNSTILE_SECRET: string = process.env.TURNSTILE_SECRET ?? "";

export function hasTurnstileEnvVars() {
    return TURNSTILE_SITEKEY !== "" && TURNSTILE_SECRET !== "";
}

const KEYCLOAK_CLIENT = new KcAdminClient({
    baseUrl: KEYCLOAK_URL,
    realmName: KEYCLOAK_REALM
});

const KEYCLOAK_CREDS: Credentials = {
    username: KEYCLOAK_ADMIN_USERNAME,
    password: KEYCLOAK_ADMIN_PASSWORD,
    grantType: 'password',
    clientId: 'admin-cli'
};

export async function getAuthedClient() {
    await KEYCLOAK_CLIENT.auth(KEYCLOAK_CREDS);
    return KEYCLOAK_CLIENT;
}

export type BrowserAuthFlow = 'browser' | 'browser-turnstile';
export type RegistrationAuthFlow = 'registration' | 'registration-turnstile';

type TurnstileConfig = {
    'site.key'?: string,
    'secret'?: string,
    'action'?: string
}

export type TurnstileAuthenticatorConfigPreset =
    'client-visible-pass-server-pass'
    | 'client-visible-pass-server-fail'
    | 'client-visible-block-server-pass'
    | 'client-visible-block-server-fail'
    | 'from-environment';

export type TurnstileAuthenticatorConfig = TurnstileAuthenticatorConfigPreset | TurnstileConfig;

const turnstileConfigs: Record<TurnstileAuthenticatorConfigPreset, TurnstileConfig> = {
    'client-visible-pass-server-pass': {
        'site.key': '1x00000000000000000000AA',
        'secret': '1x0000000000000000000000000000000AA'
    },
    'client-visible-pass-server-fail': {
        'site.key': '1x00000000000000000000AA',
        'secret': '2x0000000000000000000000000000000AA',
    },
    'client-visible-block-server-pass': {
        'site.key': '2x00000000000000000000AB',
        'secret': '2x0000000000000000000000000000000AA'
    },
    'client-visible-block-server-fail': {
        'site.key': '2x00000000000000000000AB',
        'secret': '2x0000000000000000000000000000000AA'
    },
    'from-environment': {
        'site.key': TURNSTILE_SITEKEY,
        'secret': TURNSTILE_SECRET
    }
}

export function isTurnstileAuthenticatorConfigPreset(config: TurnstileAuthenticatorConfigPreset | TurnstileConfig): config is TurnstileAuthenticatorConfigPreset {
    return config === 'client-visible-pass-server-pass'
        || config === 'client-visible-pass-server-fail'
        || config === 'client-visible-block-server-pass'
        || config === 'client-visible-block-server-fail'
        || config === 'from-environment';
}

export async function setBrowserAuthBinding(
    client: KcAdminClient,
    realm: string,
    flowName: BrowserAuthFlow
) {
    await client.realms.update(
        { realm },
        {
            browserFlow: flowName
        }
    );
}

export async function setRegistrationAuthBinding(
    client: KcAdminClient,
    realm: string,
    flowName: string
) {
    await client.realms.update(
        { realm },
        {
            registrationFlow: flowName
        }
    );
}

export async function setRegistrationAllowed(
    client: KcAdminClient,
    realm: string,
    allowed: boolean
) {
    await client.realms.update(
        { realm },
        {
            registrationAllowed: allowed
        }
    );
}

async function getFlowExecutionId(
    client: KcAdminClient,
    flowName: string,
    executionProviderId: string
) {
    const executions = await client.authenticationManagement.getExecutions({
        flow: flowName
    });

    return executions.find(execution => execution.providerId === executionProviderId)?.id;
}

async function setExecutionConfig(
    client: KcAdminClient,
    realm: string,
    executionId: string,
    alias: string,
    config: TurnstileConfig
) {
    const response = await fetch(
        `${client.baseUrl}/admin/realms/${realm}/authentication/executions/${executionId}/config`,
        {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${client.accessToken}`
            },
            body: JSON.stringify({
                alias,
                config
            })
        }
    );

    if (!response.ok) {
        throw new Error(`Failed to set execution config: ${response.status} ${response.statusText}`);
    }
}

export async function setBrowserFlowTurnstileConfig(
    client: KcAdminClient,
    realm: string,
    config: TurnstileAuthenticatorConfig) {
    const browserFlowTurnstileExecutionId = await getFlowExecutionId(client, 'browser-turnstile forms', 'login-turnstile-action');
    if (browserFlowTurnstileExecutionId === undefined) throw new Error('Browser flow turnstile execution id not found');

    const isConfigPreset = isTurnstileAuthenticatorConfigPreset(config);
    const configAlias = isConfigPreset ? `turnstile-preset-${config}` : `turnstile-custom`;
    const authConfig = isConfigPreset ? turnstileConfigs[config] : config;

    await setExecutionConfig(
        client, realm,
        browserFlowTurnstileExecutionId,
        configAlias,
        authConfig
    );
}

export async function setRegistrationFlowTurnstileConfig(
    client: KcAdminClient,
    realm: string,
    config: TurnstileAuthenticatorConfig
) {
    const registrationFlowTurnstileExecutionId = await getFlowExecutionId(client, 'registration-turnstile registration form', 'registration-turnstile-action');
    if (registrationFlowTurnstileExecutionId === undefined) throw new Error('Registration flow turnstile execution id not found');

    const isConfigPreset = isTurnstileAuthenticatorConfigPreset(config);
    const configAlias = isConfigPreset ? `turnstile-${config}` : `turnstile-custom`;
    const authConfig = isConfigPreset ? turnstileConfigs[config] : config;

    await setExecutionConfig(
        client, realm,
        registrationFlowTurnstileExecutionId,
        configAlias,
        authConfig
    );
}