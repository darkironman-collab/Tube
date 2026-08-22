# Security policy

Extreme Tube is intended to remain auditable and minimal. Security takes priority over convenience.

## Mandatory rules

1. Do not commit, bundle, mirror or release an original YouTube APK.
2. Do not add analytics, ad SDKs, telemetry SDKs, trackers or crash-reporting services that transmit data by default.
3. Do not add hidden remote configuration, remote-code execution, dynamic DEX/JAR/APK loading, shell-command downloaders or self-updaters that install code outside the normal reviewed release process.
4. Do not collect Google credentials, cookies, OAuth tokens or account secrets in Extreme Tube code.
5. Do not introduce unrelated Android permissions. A patch must document every permission it adds or changes.
6. Do not hard-code private API keys, passwords, signing keys or personal access tokens.
7. Dependencies and GitHub Actions must be reviewable and should be pinned to known versions where practical.
8. Quality entries must represent formats actually present in the video stream metadata; the UI must not advertise fake 4K/8K/HDR/codec support.
9. A source format being listed does not mean the device decoder can play it. Device capability and playback failure must be handled safely.
10. Build/release automation must never execute the input APK as native/app code. It is patch input only.

## APK trust

A patched APK inherits code from the original YouTube APK plus the selected patch bundles. Users should obtain their original APK from a source they trust and verify its package/signature before patching.

Extreme Tube source being clean does not mathematically guarantee every APK carrying the name is clean. Only builds whose inputs, patch set and signing provenance are known should be trusted.

## Reporting

For a suspected security issue, open a GitHub issue without posting credentials, tokens, private signing material or other secrets. For a vulnerability that would be dangerous to disclose publicly, contact the repository owner privately through GitHub before publishing exploit details.
