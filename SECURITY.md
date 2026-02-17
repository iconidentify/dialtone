# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Dialtone, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, email security concerns to the maintainers via the contact information in the repository. Include:

- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We will acknowledge receipt within 48 hours and aim to provide a fix or mitigation plan within 7 days.

## Scope

This policy covers the Dialtone server codebase, including:
- AOL protocol handling
- Web management interface
- Authentication and session management
- Database operations

## Best Practices for Operators

- Never commit real API keys or secrets to version control
- Use environment variables or a secrets manager for production credentials
- Keep `development.mode=false` in production
- Keep `admin.enabled=false` unless you need the admin interface
- Rotate `jwt.secret` periodically
- Run behind a reverse proxy with TLS for the web interface
