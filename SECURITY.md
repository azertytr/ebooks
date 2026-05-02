# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x.x   | ✅        |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security issues by emailing: **security@example.com**

Include the following in your report:
- A clear description of the vulnerability
- Steps to reproduce the issue
- The potential impact and attack scenario
- Any proof-of-concept code or screenshots (if applicable)
- Your suggested fix (if any)

We will acknowledge your report within **48 hours** and aim to release a fix
within **7 days** for critical issues and **30 days** for non-critical ones.
We will keep you informed of our progress throughout the process.

We do not currently offer a bug bounty program, but we deeply appreciate
responsible disclosure.

## Security Architecture

This application is designed with a privacy-first, minimal-attack-surface approach:

- **Local storage only** — books and reading progress are stored on-device; no data is sent to external servers.
- **No network access** — the app does not connect to any remote endpoints.
- **Scoped file access** — files are opened exclusively via Android's Storage Access Framework (SAF); no broad storage permissions are requested.
- **No analytics or telemetry** — no usage data, crash reports, or identifiers are collected or transmitted.
- **No accounts or authentication** — no credentials or personal data are handled.

## Threat Model

| Threat | Mitigation |
|--------|------------|
| Malicious EPUB/FB2 file with crafted XML | XmlPullParser used in non-namespace mode; `runCatching` wraps all parsing |
| Malicious EPUB with path-traversal in ZIP entries | Entry names are resolved relative to OPF base; leading `/` is stripped |
| Exfiltration via WebView (chapter HTML) | WebView has no JavaScript enabled; images are inlined as base64 data URIs |
| Persistent URI access beyond user intent | `takePersistableUriPermission` is guarded; only read permission is requested |
| SQL injection via book metadata | Room uses parameterized queries exclusively |

## Out of Scope

- Vulnerabilities in third-party dependencies — report those to the upstream project.
- Issues that require physical, unlocked access to the device.
- Vulnerabilities in Android OS itself.
