# Test Utility Scripts

This directory contains helper scripts for swapping flows/configs in keycloak without having to login and change them manually.

## Examples

### Browser Login Flows
Set login flow, and change turnstile config (if setting turnstile login flow)
> npx tsx setRegistrationFlow.ts [flow alias] [turnstile-config]

#### Switch to default browser login flow:
> npx tsx setRegistrationFlow.ts browser

#### Switch to turnstile browser login flow, and set turnstile config:
> npx tsx setRegistrationFlow.ts browser-turnstile client-visible-pass-server-pass

`client-visible-pass-server-pass` is the default happy path test config, turnstile widget challenge succeeds, and server-side validation succeeds.

Other available config aliases are defined in ../keycloak.ts.