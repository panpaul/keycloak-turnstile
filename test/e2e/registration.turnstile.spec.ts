import { test, expect } from '@playwright/test';
import { KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_REALM, KEYCLOAK_URL, getAuthedClient, setRegistrationAllowed, setRegistrationAuthBinding, setRegistrationFlowTurnstileConfig } from '../keycloak';
import { randomUUID } from 'crypto';

test.describe('keycloak turnstile registration', async () => {

    test.beforeEach(async () => {
        await setRegistrationAllowed(await getAuthedClient(), KEYCLOAK_REALM, true);
        await setRegistrationAuthBinding(await getAuthedClient(), KEYCLOAK_REALM, 'registration-turnstile');
    });

    test.describe('client pass, server pass', () => {
        test.beforeEach(async () => {
            await setRegistrationFlowTurnstileConfig(await getAuthedClient(), KEYCLOAK_REALM, 'client-visible-pass-server-pass');
        });

        test('can register with turnstile widget', async ({ page }) => {
            await page.goto(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/account`);
            await page.waitForSelector('#kc-login');

            const pageUrl = new URL(page.url());
            expect(pageUrl.pathname).toBe(`/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`);

            await page.waitForSelector('text=Register');
            await page.click('text=Register');

            await page.waitForSelector('#kc-page-title');
            await expect(page.locator('#kc-page-title')).toHaveText('Register');
            await expect(page.locator('.cf-turnstile')).toHaveCount(1);

            const user = `keycloak-turnstile-testuser-${randomUUID()}`;
            await page.fill('input#username', user);
            await page.fill('input#password', KEYCLOAK_ADMIN_PASSWORD);
            await page.fill('input#password-confirm', KEYCLOAK_ADMIN_PASSWORD);
            await page.fill('input#email', `${user}@localhost`);
            await page.fill('input#firstName', 'Test');
            await page.fill('input#lastName', 'User');

            await page.waitForSelector('iframe[src^="https://challenges.cloudflare.com/cdn-cgi/challenge-platform/"]');
            const challengeFrame = page.frame({ url: /https\:\/\/challenges\.cloudflare\.com\/cdn-cgi\/challenge\-platform\/.*/ });
            expect(challengeFrame).not.toBeNull();
            await challengeFrame!.waitForSelector('#success');

            await page.click('input[value="Register"]');

            const pageHeadingLocator = page.getByTestId('page-heading');
            await pageHeadingLocator.waitFor();
            expect(await pageHeadingLocator.textContent()).toBe('Personal info');
        });
    });

    test.describe('client pass, server fail', () => {
        test.beforeEach(async () => {
            await setRegistrationFlowTurnstileConfig(await getAuthedClient(), KEYCLOAK_REALM, 'client-visible-pass-server-fail');
        });

        test('cannot register with turnstile widget when server-side validation fails', async ({ page }) => {
            await page.goto(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/account`);
            await page.waitForSelector('#kc-login');

            const pageUrl = new URL(page.url());
            expect(pageUrl.pathname).toBe(`/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`);

            await page.waitForSelector('text=Register');
            await page.click('text=Register');

            await page.waitForSelector('#kc-page-title');
            await expect(page.locator('#kc-page-title')).toHaveText('Register');
            await expect(page.locator('.cf-turnstile')).toHaveCount(1);

            const user = `keycloak-turnstile-testuser-${randomUUID()}`;
            await page.fill('input#username', user);
            await page.fill('input#password', KEYCLOAK_ADMIN_PASSWORD);
            await page.fill('input#password-confirm', KEYCLOAK_ADMIN_PASSWORD);
            await page.fill('input#email', `${user}@localhost`);
            await page.fill('input#firstName', 'Test');
            await page.fill('input#lastName', 'User');

            await page.waitForSelector('iframe[src^="https://challenges.cloudflare.com/cdn-cgi/challenge-platform/"]');
            const challengeFrame = page.frame({ url: /https\:\/\/challenges\.cloudflare\.com\/cdn-cgi\/challenge\-platform\/.*/ });
            expect(challengeFrame).not.toBeNull();
            await challengeFrame!.waitForSelector('#success');

            await page.click('input[value="Register"]');

            const errorMessageLocator = page.locator('.pf-c-alert__title.kc-feedback-text');
            await errorMessageLocator.waitFor();
            expect(await errorMessageLocator.textContent()).toBe('Invalid Captcha');
        });
    });

    test.describe('client block', () => {

        test.beforeEach(async () => {
            await setRegistrationFlowTurnstileConfig(await getAuthedClient(), KEYCLOAK_REALM, 'client-visible-block-server-pass');
        });

        test('cannot register with turnstile widget when turnstile client fails', async ({ page }) => {
            await page.goto(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/account`);
            await page.waitForSelector('#kc-login');

            const pageUrl = new URL(page.url());
            expect(pageUrl.pathname).toBe(`/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`);

            await page.waitForSelector('text=Register');
            await page.click('text=Register');

            await page.waitForSelector('#kc-page-title');
            await expect(page.locator('#kc-page-title')).toHaveText('Register');
            await expect(page.locator('.cf-turnstile')).toHaveCount(1);

            await page.fill('input#username', KEYCLOAK_ADMIN_USERNAME);
            await page.fill('input#password', KEYCLOAK_ADMIN_PASSWORD);

            await page.waitForSelector('iframe[src^="https://challenges.cloudflare.com/cdn-cgi/challenge-platform/"]');
            const challengeFrame = page.frame({ url: /https\:\/\/challenges\.cloudflare\.com\/cdn-cgi\/challenge\-platform\/.*/ });
            expect(challengeFrame).not.toBeNull();
            await challengeFrame!.waitForSelector('#fail');

            await page.click('input[value="Register"]');

            const errorMessageLocator = page.locator('.pf-c-alert__title.kc-feedback-text');
            await errorMessageLocator.waitFor();
            expect(await errorMessageLocator.textContent()).toBe('Invalid Captcha');
        });
    });
});