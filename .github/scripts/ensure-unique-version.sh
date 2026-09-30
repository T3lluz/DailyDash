#!/usr/bin/env bash
# Bump versionCode/versionName when the current build was already released.
# Required for in-app updates (higher versionCode) and unique GitHub release tags.
#
# Concurrent master merges previously raced on `git push` (non-fast-forward),
# which failed Actions run #92. This script always rebases onto origin/master
# before bumping and retries the push so queued release jobs stay green.
set -euo pipefail

GRADLE_FILE="app/build.gradle.kts"
MAX_PUSH_ATTEMPTS=8

read_version() {
  VERSION_NAME=$(grep -E 'versionName\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
  VERSION_CODE=$(grep -E 'versionCode\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/[^0-9]//g')
}

next_name() {
  local base="$1" code="$2"
  if [[ "$base" =~ ^([0-9]+\.[0-9]+\.)([0-9]+)$ ]]; then
    echo "${BASH_REMATCH[1]}${code}"
  elif [[ "$base" =~ ^(.+-(alpha|beta|rc)\.[0-9]+\.)([0-9]+)$ ]]; then
    # 2.0.0-beta.1.0 counts its builds in the last number: .1.1, .1.2, …
    echo "${BASH_REMATCH[1]}$((BASH_REMATCH[3] + code - VERSION_CODE))"
  else
    echo "${base}-${code}"
  fi
}

apk_released() {
  local code="$1"
  gh api "repos/${GITHUB_REPOSITORY}/releases?per_page=100" \
    --jq "[.[].assets[].name | select(test(\"^DailyDash-.*-vc${code}\\\\.apk$\"; \"i\"))] | length"
}

tag_taken() {
  local name="$1"
  git rev-parse "refs/tags/v${name}" >/dev/null 2>&1 \
    || gh release view "v${name}" >/dev/null 2>&1
}

sync_master() {
  git fetch --tags --force origin
  git fetch origin master
  # Build/publish from the latest tip so queued jobs include merges that landed
  # while an earlier release job was still running.
  git reset --hard origin/master
}

compute_and_commit_bump() {
  read_version

  if ! tag_taken "$VERSION_NAME" && [ "$(apk_released "$VERSION_CODE")" -eq 0 ]; then
    echo "Using ${VERSION_NAME} (vc${VERSION_CODE})"
    return 1
  fi

  local base_name="$VERSION_NAME"
  local new_code=$((VERSION_CODE + 1))
  local new_name
  new_name="$(next_name "$base_name" "$new_code")"

  while tag_taken "$new_name" || [ "$(apk_released "$new_code")" -gt 0 ]; do
    new_code=$((new_code + 1))
    new_name="$(next_name "$base_name" "$new_code")"
  done

  sed -i -E "s/(versionCode\\s*=\\s*)${VERSION_CODE}/\\1${new_code}/" "$GRADLE_FILE"
  sed -i -E "s/(versionName\\s*=\\s*\")${VERSION_NAME}(\")/\\1${new_name}\\2/" "$GRADLE_FILE"

  git add "$GRADLE_FILE"
  git commit -m "chore: bump version to ${new_name} (vc${new_code}) [skip ci]"

  VERSION_NAME="$new_name"
  VERSION_CODE="$new_code"
  echo "Auto-bumped to ${VERSION_NAME} (vc${VERSION_CODE})"
  return 0
}

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

attempt=1
while true; do
  sync_master

  if ! compute_and_commit_bump; then
    break
  fi

  if git push origin HEAD:master; then
    break
  fi

  if [ "$attempt" -ge "$MAX_PUSH_ATTEMPTS" ]; then
    echo "Failed to push version bump after ${MAX_PUSH_ATTEMPTS} attempts" >&2
    exit 1
  fi

  echo "Push rejected (attempt ${attempt}/${MAX_PUSH_ATTEMPTS}); retrying from latest master…"
  attempt=$((attempt + 1))
  sleep $((attempt * 2))
done

{
  echo "version_name=${VERSION_NAME}"
  echo "version_code=${VERSION_CODE}"
  echo "commit_sha=$(git rev-parse HEAD)"
} >> "$GITHUB_OUTPUT"
