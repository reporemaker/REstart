# Security

REstart runs as your home screen, so it sees which apps you open and holds your notes and
settings. Every decision about what it reads, stores and sends is made in this repository and
is my responsibility.

## Reporting a vulnerability

Please do not open a public issue for a security problem.

- Use GitHub's private vulnerability reporting: **Security → Report a vulnerability** on this repository, or
- write to **hallo@remaker.ch**. If you need encryption, say so in a first mail and you will get a key.

Tell me what you found, how to reproduce it and which version you tested. You will get an
answer within 7 days. When the fix is released, the advisory names you unless you prefer not
to be named.

## What REstart does not do

- No analytics, no ads, no crash uploads. After a crash the app shows the contact address as
  plain text; nothing is sent.
- The only network request the app makes on its own is the weather, to open-meteo.com, with the
  location rounded to about 1 km.
- Launcher data is excluded from cloud backup. Notes stay out of screenshots.
- Third-party widgets are rendered without loading their code into the launcher's process.
- Nothing typed into the drawer leaves the device.

## Supported versions

Only the latest release receives fixes.
