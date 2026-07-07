# Contributing

Thanks for helping make disclosure contacts easy to find from inside OWASP ZAP. This is a small, focused codebase — plain Java, one right-click action — and contributions are welcome.

## Dev setup

Requires a **JDK 17+**. The `org.zaproxy.add-on` Gradle plugin needs **Gradle 8.13+** (CI provisions it automatically — no local install required there):

```sh
gradle jarZapAddOn        # builds the .zap add-on under build/
```

Load the built `.zap` into ZAP with **File → Load Add-on File…**, or drop it into ZAP's `plugin` directory and restart. Then right-click any HTTP message and choose **"Find disclosure contact"**.

## Before you open a PR

- `gradle jarZapAddOn` builds cleanly. This is exactly what CI runs on every push and PR — see [`.github/workflows/build.yml`](.github/workflows/build.yml).
- Network calls stay **off the Swing EDT**: the lookup runs on a background daemon thread and the dialog is shown via `SwingUtilities.invokeLater`. Keep it that way — blocking the EDT freezes ZAP's UI.
- The add-on sends **only the host string** to lookup.disclose.io — never request headers, cookies, parameters, or bodies. Don't widen what leaves the user's machine.
- Keep it **headless-safe**: the extension must register in a headless ZAP without touching Swing at hook time.

## What goes where

- **Add-on bugs and features** → issues and PRs here.
- **"The contact or attribution for host X is wrong"** → that's a [lookup.disclose.io](https://lookup.disclose.io) resolution issue, not add-on code. lookup.disclose.io is the attribution engine and the system of record for results.
- **Security issues** → see [SECURITY.md](SECURITY.md) — please don't open a public issue.

## Releases

Maintainers cut releases; the `version` in [`build.gradle.kts`](build.gradle.kts) is the source of truth.
