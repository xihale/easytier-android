# Repository Guidelines

## Commit

- Follow Conventional Commits: `<type>(<scope>): <subject>` (e.g. `feat(ui): ...`, `fix(vpn): ...`, `ci(release): ...`, `refactor(core): ...`, `docs: ...`).
- Keep the subject imperative, lowercase, without a trailing period, and under 70 characters; keep body lines under 100 characters.

## Release

- Every release tag (`v*`) MUST be an annotated tag with a non-empty release message (`git tag -a vX.Y.Z -m "..."`), never a lightweight tag or an empty annotation.
- Standard Release Message format:

  ```text
  vX.Y.Z: <简明概览标题>

  - <变更要点 1（新增 / 修复 / 优化）>
  - <变更要点 2>
  ```

- Pre-release checklist: ensure the working tree is clean, `./gradlew testDebugUnitTest` passes, then push both `main` and the annotated tag (`git push origin main vX.Y.Z`).
- `.github/workflows/release.yml` validates right after checkout that the `v*` ref is an annotated tag object (`git cat-file -t refs/tags/${TAG}` == `tag`) with a non-empty message (failing fast on violations), and publishes that message into the GitHub Release notes alongside the changelog link and LGPL-3.0 §4a notice.
