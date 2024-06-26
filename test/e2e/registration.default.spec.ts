import { test, expect } from '@playwright/test';
import { KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_REALM, KEYCLOAK_URL, setBrowserAuthBinding, setRegistrationAllowed, setRegistrationAuthBinding } from '../keycloak';
import { randomUUID } from 'crypto';

test.describe('keycloak default registration', async () => {
    test.beforeEach(async () => {
        await setRegistrationAllowed(true);
        await setRegistrationAuthBinding('registration');
    });

    test('can register without turnstile widget', async ({ page }) => {
        await page.goto(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/account`);
        await page.waitForSelector('#kc-login');

        const pageUrl = new URL(page.url());
        expect(pageUrl.pathname).toBe(`/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`);

        await page.waitForSelector('text=Register');
        await page.click('text=Register');

        await page.waitForSelector('#kc-page-title');
        await expect(page.locator('#kc-page-title')).toHaveText('Register');
        await expect(page.locator('.cf-turnstile')).toHaveCount(0);

        const user = `keycloak-turnstile-testuser-${randomUUID()}`;
        await page.fill('input#username', user);
        await page.fill('input#password', KEYCLOAK_ADMIN_PASSWORD);
        await page.fill('input#password-confirm', KEYCLOAK_ADMIN_PASSWORD);
        await page.fill('input#email', `${user}@localhost`);
        await page.fill('input#firstName', 'Test');
        await page.fill('input#lastName', 'User');

        await page.click('input[value="Register"]');

        const pageHeadingLocator = page.getByTestId('page-heading');
        await pageHeadingLocator.waitFor();
        expect(await pageHeadingLocator.textContent()).toBe('Personal info');
    });
});