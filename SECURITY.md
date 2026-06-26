# Security Policy

Dayfold handles families' personal signals (calendar, email-derived content, lists,
location), so we take security seriously and welcome good-faith reports. This policy
explains how to report a vulnerability and what to expect.

## Reporting a vulnerability

**Please do not open a public issue, pull request, or discussion for a security
problem** — that discloses it to everyone before there is a fix.

Instead, use **GitHub's private vulnerability reporting**:

1. Go to the repository's **Security** tab → **Report a vulnerability**
   (`https://github.com/SloopWorks/dayfold/security/advisories/new`).
2. Describe the issue, the impact, and the steps (or a proof of concept) to reproduce it.

This opens a private advisory visible only to you and the maintainers. If you cannot
use GitHub advisories, open a minimal public issue that says only "security report —
please enable a private channel" (no details), and we will follow up privately.

Please include, where you can:

- the component (`apps/api` server · `apps/client` / `apps/androidApp` · `apps/cli`),
- the version, commit, or environment (e.g. prod, a self-hosted build),
- the impact (what an attacker can read, change, or bypass),
- reproduction steps and any logs/PoC.

## What's in scope

Reports that demonstrate a concrete weakness in:

- **Authentication / sessions** — token minting, refresh, the device-grant (RFC 8628)
  flow, Firebase ID-token verification.
- **Authorization / tenancy** — one family reading or writing another family's data;
  scope or per-hub visibility bypass; mass-assignment.
- **Input handling** — injection, SSRF, deserialization, or content that renders unsafely
  in the app.
- **Secrets / data exposure** — credentials in logs, responses, or the repo; PII leakage.

By design, **security is enforced on the server, not by obscurity** — the client and CLI
ship no secrets (everything is via environment/keychain), so source availability is not a
vulnerability in itself. Reports should show real impact rather than the mere presence of
open-source code.

## Out of scope

- Findings that require a compromised device, a malicious OS, or physical access.
- Social engineering, phishing of maintainers, or spam/rate-of-email concerns.
- Issues only reproducible on a third party's **self-hosted** instance with non-default,
  insecure configuration.
- Missing best-practice headers with no demonstrated impact, automated-scanner output
  without a working exploit, and denial-of-service via brute volume.

## Supported versions

Dayfold is **pre-1.0** and under active development. Security fixes are applied to the
`main` branch (and the latest release, once releases are cut). Older commits/builds are
not separately patched — update to the latest before reporting.

## Coordinated disclosure

- Please give us a reasonable chance to investigate and ship a fix before any public
  disclosure. As a small, **solo-maintained** project we aim to acknowledge a report
  within a few business days and to agree on a disclosure timeline with you; we will keep
  you updated and credit you (if you wish) when a fix lands.
- We support **coordinated disclosure** and will not pursue good-faith security research
  that respects this policy, stays within scope, avoids privacy violations and service
  disruption, and does not access or modify data beyond what is needed to demonstrate the
  issue. **Do not access, store, or share other families' data**; stop and report as soon
  as you confirm access.

Thank you for helping keep families' data safe.
