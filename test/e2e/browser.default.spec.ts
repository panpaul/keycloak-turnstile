import { test, expect } from '@playwright/test';
import { KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_REALM, KEYCLOAK_URL, setBrowserAuthBinding } from '../keycloak';

test.describe('keycloak default browser login', async () => {
  test.beforeEach(async () => {
    await setBrowserAuthBinding('browser');
  });

  test('can login to account client without turnstile widget', async ({ page }) => {
    await page.goto(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/account`);
    await page.waitForSelector('#kc-login');

    const pageUrl = new URL(page.url());
    expect(pageUrl.pathname).toBe(`/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`);

    await expect(page.locator('.cf-turnstile')).toHaveCount(0);

    await page.fill('input#username', KEYCLOAK_ADMIN_USERNAME);
    await page.fill('input#password', KEYCLOAK_ADMIN_PASSWORD);
    await page.click('input#kc-login');

    const pageHeadingLocator = page.getByTestId('page-heading');
    await pageHeadingLocator.waitFor();
    expect(await pageHeadingLocator.textContent()).toBe('Personal info');
  });
});