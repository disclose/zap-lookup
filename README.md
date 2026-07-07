<div align="center">

# Disclosure Contact Lookup

### Right-click a host in OWASP ZAP and get its security-disclosure contact — from [lookup.disclose.io](https://lookup.disclose.io).

<p>
<a href="LICENSE"><img src="https://img.shields.io/github/license/disclose/zap-lookup?color=5B3AB6&label=license" alt="license"></a>
<a href="https://github.com/disclose/zap-lookup/actions/workflows/build.yml"><img src="https://github.com/disclose/zap-lookup/actions/workflows/build.yml/badge.svg" alt="CI build"></a>
<a href="https://lookup.disclose.io"><img src="https://img.shields.io/badge/lookup-lookup.disclose.io-5B3AB6" alt="lookup lookup.disclose.io"></a>
<a href="https://github.com/disclose/zap-lookup/issues"><img src="https://img.shields.io/badge/PRs-welcome-5B3AB6" alt="PRs welcome"></a>
</p>

*Part of the **[disclose.io Project](https://disclose.io)** — the open, vendor-neutral infrastructure for vulnerability disclosure. [Browse the ecosystem →](https://github.com/disclose)*

</div>

---

# Disclosure Contact Lookup — OWASP ZAP add-on

An [OWASP ZAP](https://www.zaproxy.org/) add-on that finds the **security-disclosure contact** for any host you're testing, using the free [lookup.disclose.io](https://lookup.disclose.io) attribution API.

Right-click a request, choose **"Find disclosure contact"**, and the add-on resolves the host to its owning organization and a ranked list of disclosure channels — `security.txt`, VDP / bug-bounty programs, PSIRT directories, and convention emails — so you know exactly where to report what you found.

This is the open-source-proxy sibling of the disclose.io [Burp Suite](https://github.com/disclose/burp-lookup) and Caido extensions.

> **Status: alpha (first release).** CI-built and inspection-verified — it compiles, the extension auto-registers in the manifest, and it's headless- and EDT-safe — but a full runtime load in a live ZAP is still pending. Please report any load or UX issues.

---

## What it does

- Adds a **"Find disclosure contact"** right-click menu item on any HTTP message (History, Sites tree, etc.).
- Calls `POST https://lookup.disclose.io/api/lookup` with the host and shows the result in a message dialog:
  - **Attribution** — owning organization, parent company, jurisdiction.
  - **Ranked contacts** — verified channels first, then by confidence (type, value, verified/confidence).

## Privacy

The add-on sends **only the host string** (e.g. `github.com`) to lookup.disclose.io. It never transmits the request line, headers, cookies, parameters, or body of your selected HTTP message. The API is free, CORS-open, and requires no authentication.

A short in-memory cache (5 min TTL) and a 15-second request timeout keep it from spamming the API and from hanging the UI when offline.

The lookup call is made **directly** to lookup.disclose.io (via `java.net.http`), not through ZAP's HTTP sender — so it never appears in your ZAP History or Sites tree and generates no scan traffic. Trade-off: it uses the JVM's default network settings rather than ZAP's configured upstream/corporate proxy, so in locked-down egress environments the add-on needs direct outbound access to `lookup.disclose.io`.

---

## Build

Requires a JDK 17+ and [Gradle](https://gradle.org/install/) 8.x (CI provides Gradle automatically — no local install needed there):

```sh
gradle jarZapAddOn
```

The `.zap` add-on file is written under `build/` (CI globs for it and uploads it as the `zap-lookup-addon` artifact on every push).

## Install in ZAP

1. Build the `.zap` (above), or download it from the latest [GitHub Actions build](../../actions).
2. In ZAP: **File → Load Add-on File…** and select the built `.zap`, OR drop the `.zap` into ZAP's `plugin` directory and restart.
3. Right-click any request to use it.

## Usage

1. Anywhere you have an HTTP request — the History tab, the Sites tree — **right-click**.
2. Choose **"Find disclosure contact"**.
3. A dialog shows the attribution and ranked disclosure contacts for the host.

---

## Development

```
src/main/java/io/disclose/zaplookup/
  ExtensionZapLookup.java     # ExtensionAdaptor entry point (hook)
  LookupPopupMenuItem.java    # PopupMenuItemHttpMessageContainer — the right-click item
  LookupClient.java           # java.net.http.HttpClient call + cache + timeout
  LookupResult.java           # typed view of the API response (Gson)
  LookupException.java        # UI-safe lookup failure
```

The network call runs on a background daemon thread (never the Swing EDT); the dialog is shown back on the EDT via `SwingUtilities.invokeLater`.

CI (`.github/workflows/build.yml`) builds the `.zap` on every push and uploads it as an artifact.

## License

[MIT](LICENSE) © disclose.io
