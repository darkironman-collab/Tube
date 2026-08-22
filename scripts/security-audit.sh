#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'SECURITY AUDIT FAILED: %s\n' "$1" >&2
  exit 1
}

# Extreme Tube never stores proprietary APKs, signing keys, native libraries or compiled code in git.
for pattern in '*.apk' '*.apkm' '*.xapk' '*.apks' '*.aab' '*.dex' '*.so' '*.class' '*.jks' '*.keystore' '*.p12' '*.pfx'; do
  if find . -type f -name "$pattern" -not -path './.git/*' -print -quit | grep -q .; then
    fail "forbidden committed/generated file type detected: $pattern"
  fi
done

EXTENSION_ROOT='extensions/extension/src/main'
if [[ -d "$EXTENSION_ROOT" ]]; then
  # The custom extension does not need Android permissions. Any future permission is a deliberate review event.
  if grep -RInE '<uses-permission|android\.permission\.' "$EXTENSION_ROOT" --include='*.xml' --include='*.java' --include='*.kt' >/dev/null; then
    fail 'custom extension requests or references an Android permission'
  fi

  # The All Formats extension is intentionally offline. Network stacks/endpoints are forbidden in executable/schema source.
  # XML is excluded here because Android's required manifest namespace is itself an http:// URI.
  if grep -RInE \
    'java\.net\.|HttpURLConnection|URLConnection|okhttp|retrofit|WebSocket|android\.webkit|https?://|Authorization|CookieManager|android\.accounts|AccountManager' \
    "$EXTENSION_ROOT" --include='*.java' --include='*.kt' --include='*.proto' >/dev/null; then
    fail 'network, endpoint, cookie or account-access code detected in custom extension'
  fi

  # No dynamic executable loading, subprocesses, native loading or reflective class loading.
  if grep -RInE \
    'Runtime\.getRuntime\(\)\.exec|ProcessBuilder|DexClassLoader|PathClassLoader|System\.loadLibrary|System\.load\(|Class\.forName' \
    "$EXTENSION_ROOT" --include='*.java' --include='*.kt' >/dev/null; then
    fail 'dynamic code execution/loading primitive detected in custom extension'
  fi
fi

# Patch source itself must not add permissions or dynamic loading either.
PATCH_ROOT='patches/src/main'
if [[ -d "$PATCH_ROOT" ]]; then
  if grep -RInE '<uses-permission|android\.permission\.|DexClassLoader|PathClassLoader|Runtime\.getRuntime\(\)\.exec|ProcessBuilder' \
    "$PATCH_ROOT" --include='*.kt' --include='*.java' --include='*.xml' >/dev/null; then
    fail 'permission or executable-loading primitive detected in patch source'
  fi
fi

printf 'Security audit passed: no forbidden artifacts, permissions, network stack, account access, or dynamic-code primitives found.\n'
