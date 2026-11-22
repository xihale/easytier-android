# Repository Guidelines

## Release

- Every release tag (`v*`) MUST be an annotated tag with a release message (`git tag -a vX.Y.Z -m "..."`), never a lightweight tag without a message.
- `.github/workflows/release.yml` extracts the tag annotation (falling back to commit subjects if empty) into the GitHub Release notes alongside the LGPL-3.0 §4a notice.
