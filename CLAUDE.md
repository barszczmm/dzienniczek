# CLAUDE.md

## Git

- Name branches after the feature or fix they contain: short, lowercase English words
  joined with hyphens, e.g. `pull-to-refresh`, `clickable-links`, `librus-token-refresh`.
  Do not use generated names like `ccr-…` or `claude/…`.
- One feature or fix per branch, started from `main`.

## GitHub Actions

- Never trigger the Build workflow (or any other) on your own, on any branch.
  Run it only when the user asks for it.
