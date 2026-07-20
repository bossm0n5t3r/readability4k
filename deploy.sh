#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf 'Required command not found: %s\n' "$1" >&2
    exit 1
  }
}

require_interactive_input() {
  if [ ! -t 0 ]; then
    printf '%s must be set when running without an interactive terminal.\n' "$1" >&2
    exit 1
  fi
}

if [ -z "${SIGNING_KEY+x}" ]; then
  require_interactive_input "SIGNING_KEY"
  require_command gpg
  printf 'PGP signing key fingerprint: '
  read -r signing_key_fingerprint
  if [ -z "$signing_key_fingerprint" ]; then
    printf 'A PGP signing key fingerprint is required.\n' >&2
    exit 1
  fi
  SIGNING_KEY="$(gpg --batch --armor --export-secret-keys "$signing_key_fingerprint")"
  if [ -z "$SIGNING_KEY" ]; then
    printf 'Unable to export the PGP secret key.\n' >&2
    exit 1
  fi
  export SIGNING_KEY
fi

if [ -z "${SIGNING_PASSWORD+x}" ]; then
  require_interactive_input "SIGNING_PASSWORD"
  printf 'PGP signing key passphrase: '
  read -r -s SIGNING_PASSWORD
  printf '\n'
  export SIGNING_PASSWORD
fi

if [ -z "${CENTRAL_USERNAME+x}" ]; then
  require_interactive_input "CENTRAL_USERNAME"
  printf 'Central Portal user token username: '
  read -r CENTRAL_USERNAME
  if [ -z "$CENTRAL_USERNAME" ]; then
    printf 'A Central Portal user token username is required.\n' >&2
    exit 1
  fi
  export CENTRAL_USERNAME
fi

if [ -z "${CENTRAL_PASSWORD+x}" ]; then
  require_interactive_input "CENTRAL_PASSWORD"
  printf 'Central Portal user token password: '
  read -r -s CENTRAL_PASSWORD
  printf '\n'
  export CENTRAL_PASSWORD
fi

printf 'Preparing the signed Maven Central bundle...\n'
./gradlew prepareCentralBundle

printf 'Uploading the bundle to the Central Portal for manual release...\n'
./gradlew publishCentralBundle
