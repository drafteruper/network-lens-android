# Security policy

This is an experimental 0.1.x application, not a hardened production identity system. The current development branch receives fixes.

Use GitHub's private vulnerability reporting under the repository Security tab when enabled. If unavailable, open a minimal public issue asking for a private contact channel, with no exploit details, personal information, scans, logs, attendee lists or credentials. Never post live data or secrets in a public issue.

Database files are in app-private Android storage; there is no additional database encryption. Device backups are disabled. Camera frames are transient. Do not enable cloud diagnostics that capture attendee data. Review any added dependency's manifest permissions and data practices before shipping it.
