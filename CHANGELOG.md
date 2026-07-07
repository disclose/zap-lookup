# Changelog

All notable changes to the Disclosure Contact Lookup ZAP add-on are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-07-06

### Added

- Right-click "Find disclosure contact" menu item on any HTTP message.
- Resolves the message host via the lookup.disclose.io attribution API and shows
  the owning organization plus ranked disclosure contacts in a message dialog.
- Host-only privacy contract: only the bare hostname is transmitted.
