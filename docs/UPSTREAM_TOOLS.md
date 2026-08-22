# Upstream Morphe runtime tools

Extreme Tube's first stable build path is pinned to the following official upstream release assets:

- Morphe Desktop: `v1.13.0` / `morphe-desktop-1.13.0-all.jar`
- Morphe Patches: `v1.39.1` / `patches-1.39.1.mpp`

Both are fetched only from the official `MorpheApp` GitHub organization in CI. The workflow validates that the JAR is a readable Java archive and records SHA-256 checksums before uploading the short-lived runtime-tools artifact.

The proprietary YouTube APK is never committed or uploaded by this workflow.
