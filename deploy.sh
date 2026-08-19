#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

if [ "$#" -gt 1 ] ||
  { [ "$#" -eq 1 ] && [[ ! "$1" =~ ^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$ ]]; }; then
  printf 'Usage: %s [HH:MM:SS]\n' "${BASH_SOURCE[0]##*/}" >&2
  exit 1
fi

release_tag_time="${1:-}"

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

require_clean_work_tree() {
  if ! git diff --quiet || ! git diff --cached --quiet; then
    printf 'Commit or discard tracked changes before deploying a release.\n' >&2
    exit 1
  fi
}

require_pushed_head() {
  local ahead_count
  local upstream

  if ! upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null)"; then
    printf 'Current branch has no upstream. Push it before deploying a release.\n' >&2
    exit 1
  fi

  ahead_count="$(git rev-list --count "$upstream..HEAD")"
  if [ "$ahead_count" -gt 0 ]; then
    printf 'HEAD has %s commit(s) not pushed to %s. Push before deploying a release.\n' \
      "$ahead_count" \
      "$upstream" >&2
    exit 1
  fi
}

release_version() {
  ./gradlew --quiet properties --property version |
    while IFS=':' read -r property value; do
      if [ "$property" = "version" ]; then
        printf '%s' "${value# }"
        break
      fi
    done
}

create_and_push_release_tag() {
  local head_commit
  local tagged_commit

  head_commit="$(git rev-parse HEAD)"
  if git rev-parse --verify --quiet "refs/tags/$release_tag" >/dev/null; then
    tagged_commit="$(git rev-list -n 1 "$release_tag")"
    if [ "$tagged_commit" != "$head_commit" ]; then
      printf 'Tag %s already points to %s, not HEAD %s.\n' "$release_tag" "$tagged_commit" "$head_commit" >&2
      exit 1
    fi
  else
    if [ -n "$release_tagger_date" ]; then
      GIT_COMMITTER_DATE="$release_tagger_date" \
        git tag --annotate "$release_tag" --message "Release $release_tag"
    else
      git tag --annotate "$release_tag" --message "Release $release_tag"
    fi
  fi

  printf 'Pushing release tag %s to %s...\n' "$release_tag" "$release_remote"
  git push "$release_remote" "refs/tags/$release_tag"
}

require_command git
require_clean_work_tree
require_pushed_head

release_remote="${RELEASE_REMOTE:-origin}"
git remote get-url "$release_remote" >/dev/null

release_version="$(release_version)"
if [ -z "$release_version" ]; then
  printf 'Unable to determine the Gradle project version.\n' >&2
  exit 1
fi
release_tag="v$release_version"

release_tagger_date=""
if [ -n "$release_tag_time" ]; then
  require_command date
  release_tagger_date="$(TZ=Asia/Seoul date '+%Y-%m-%d')T${release_tag_time}+0900"
fi

if [ -z "${SIGNING_KEY+x}" ]; then
  require_interactive_input "SIGNING_KEY"
  require_command gpg
  require_command gpgconf
  export GPG_TTY="$(tty)"
  gpgconf --kill gpg-agent
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

create_and_push_release_tag

printf 'Uploading the bundle to the Central Portal for manual release...\n'
./gradlew publishCentralBundle
