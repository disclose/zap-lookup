# Changelog

All notable changes to the Disclosure Contact Lookup ZAP add-on are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.1] - 2026-07-08

### Changed

- Renamed "OWASP ZAP" to "ZAP" throughout docs, matching the project's current branding.
- Replaced the JDK's `java.net.http.HttpClient` with ZAP's own `HttpSender`/`HttpMessage`
  API, so outbound lookup requests respect ZAP's configured proxy, session, and
  connection-timeout settings instead of bypassing them.
- Target ZAP version bumped to 2.17.0 (was 2.16.0), per marketplace reviewer feedback.

## [1.0.0] - 2026-07-06

### Added

- Right-click "Find disclosure contact" menu item on any HTTP message.
- Resolves the message host via the lookup.disclose.io attribution API and shows
  the owning organization plus ranked disclosure contacts in a message dialog.
- Host-only privacy contract: only the bare hostname is transmitted.
