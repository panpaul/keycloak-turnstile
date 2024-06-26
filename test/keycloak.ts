import KcAdminClient from '@keycloak/keycloak-admin-client';
import type { Credentials } from '@keycloak/keycloak-admin-client/lib/utils/auth';

export const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? "http://localhost:8080";
export const KEYCLOAK_ADMIN_USERNAME = process.env.KEYCLOAK_ADMIN_USERNAME ?? "admin";
export const KEYCLOAK_ADMIN_PASSWORD = process.env.KEYCLOAK_ADMIN_PASSWORD ?? "D3v3l0pm3nt!";
export const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM ?? "turnstile";

const adminClient = new KcAdminClient({
    baseUrl: KEYCLOAK_URL,
    realmName: KEYCLOAK_REALM
});

const KEYCLOAK_CREDS: Credentials = {
    username: KEYCLOAK_ADMIN_USERNAME,
    password: KEYCLOAK_ADMIN_PASSWORD,
    grantType: 'password',
    clientId: 'admin-cli'
};

export type BrowserAuthFlow = 'browser' | 'browser-turnstile';
export type RegistrationAuthFlow = 'registration' | 'registration-turnstile';

type TurnstileConfig = {
    alias: string,
    config: {
        'site.key'?: string,
        'secret'?: string,
        'action'?: string
    }
}

export type TurnstileAuthenticatorConfig =
    'client-visible-pass-server-pass'
    | 'client-visible-pass-server-fail'
    | 'client-visible-block-server-pass'
    | 'client-visible-block-server-fail';

const turnstileConfigs: Record<TurnstileAuthenticatorConfig, TurnstileConfig['config']> = {
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
    }
}

export function isTurnstileAuthenticatorConfig(config: string): config is TurnstileAuthenticatorConfig {
    return config === 'client-visible-pass-server-pass'
        || config === 'client-visible-pass-server-fail'
        || config === 'client-visible-block-server-fail';
}

export async function setBrowserAuthBinding(flowName: BrowserAuthFlow) {
    await adminClient.auth(KEYCLOAK_CREDS);

    await adminClient.realms.update(
        { realm: KEYCLOAK_REALM },
        {
            browserFlow: flowName
        }
    );
}

export async function setRegistrationAuthBinding(flowName: string) {
    await adminClient.auth(KEYCLOAK_CREDS);
    await adminClient.realms.update(
        { realm: KEYCLOAK_REALM },
        {
            registrationFlow: flowName
        }
    );
}

export async function setRegistrationAllowed(allowed: boolean) {
    await adminClient.auth(KEYCLOAK_CREDS);
    await adminClient.realms.update(
        { realm: KEYCLOAK_REALM },
        {
            registrationAllowed: allowed
        }
    );
}

async function getFlowExecutionId(flowName: string, executionProviderId: string) {
    await adminClient.auth(KEYCLOAK_CREDS);
    const executions = await adminClient.authenticationManagement.getExecutions({
        flow: flowName
    });

    return executions.find(execution => execution.providerId === executionProviderId)?.id;
}

async function setExecutionConfig(executionId: string, config: TurnstileConfig) {
    const response = await fetch(
        `${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/authentication/executions/${executionId}/config`,
        {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${adminClient.accessToken}`
            },
            body: JSON.stringify(config)
        }
    );

    if (!response.ok) {
        throw new Error(`Failed to set execution config: ${response.status} ${response.statusText}`);
    }
}

export async function setBrowserFlowTurnstileConfig(config: TurnstileAuthenticatorConfig) {
    const browserFlowTurnstileExecutionId = await getFlowExecutionId('browser-turnstile forms', 'login-turnstile-action');
    if (browserFlowTurnstileExecutionId === undefined) throw new Error('Browser flow turnstile execution id not found');

    await setExecutionConfig(
        browserFlowTurnstileExecutionId,
        {
            alias: `turnstile-${config}`,
            config: turnstileConfigs[config]
        }
    );
}

export async function setRegistrationFlowTurnstileConfig(config: TurnstileAuthenticatorConfig) {
    const registrationFlowTurnstileExecutionId = await getFlowExecutionId('registration-turnstile registration form', 'registration-turnstile-action');
    if (registrationFlowTurnstileExecutionId === undefined) throw new Error('Registration flow turnstile execution id not found');

    await setExecutionConfig(
        registrationFlowTurnstileExecutionId,
        {
            alias: `turnstile-${config}`,
            config: turnstileConfigs[config]
        }
    );
}