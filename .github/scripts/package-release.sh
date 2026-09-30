#!/usr/bin/env bash
# Rename the release APK to the in-app update contract and emit release metadata.
# Contract: DailyDash-{versionName}-vc{versionCode}.apk
#
# Release notes priority:
# 1) workflow_dispatch INPUT_NOTES
# 2) non-noise git commit subjects since the previous v* tag (direct master pushes)
# 3) GitHub generate-notes PR/commit bullets
# 4) triggering push commit subject (TRIGGER_COMMIT_MESSAGE)
# 5) last-resort generic line
set -euo pipefail

GRADLE_FILE="app/build.gradle.kts"
APK_SRC="app/build/outputs/apk/release/app-release.apk"

if [ -n "${VERSION_NAME:-}" ] && [ -n "${VERSION_CODE:-}" ]; then
  : # provided by the bump step
else
  VERSION_NAME=$(grep -E 'versionName\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
  VERSION_CODE=$(grep -E 'versionCode\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/[^0-9]//g')
fi

TAG_NAME="v${VERSION_NAME}"
RELEASE_NAME="DailyDash ${VERSION_NAME}"
COMMIT_SHA="${COMMIT_SHA:-$(git rev-parse HEAD)}"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

clean_title() {
  local title="$1"
  title="${title#QA: }"
  title="$(printf '%s' "$title" | sed -E 's/^\[([^]]+)\][[:space:]]*//; s/[[:space:]]+/ /g; s/^[[:space:]]+//; s/[[:space:]]+$//')"
  if [ -z "$title" ]; then
    title="Update"
  fi
  printf '%s' "$title"
}

is_noise_title() {
  local title="$1"
  local lower="${title,,}"
  if [[ "$lower" == chore:\ bump\ version* ]]; then return 0; fi
  if [[ "$lower" == *"[skip ci]"* ]]; then return 0; fi
  if [[ "$lower" == merge\ pull\ request* || "$lower" == merge\ branch* ]]; then return 0; fi
  if [[ "$lower" == bumped\ version* || "$lower" == update\ version* ]]; then return 0; fi
  if [[ "$lower" == wip || "$lower" == wip:* || "$lower" == fix\ stuff || "$lower" == temp ]]; then return 0; fi
  return 1
}

add_bullet() {
  local title="$1"
  local url="${2:-}"
  title="$(clean_title "$title")"
  if is_noise_title "$title"; then
    return
  fi
  if [ -n "$url" ]; then
    bullets+=("- [${title}](${url})")
  else
    bullets+=("- ${title}")
  fi
}

dedupe_bullets() {
  if [ "${#bullets[@]}" -eq 0 ]; then
    return
  fi
  local deduped=()
  local b seen d
  for b in "${bullets[@]}"; do
    seen=0
    for d in "${deduped[@]+"${deduped[@]}"}"; do
      if [ "$d" = "$b" ]; then seen=1; break; fi
    done
    if [ "$seen" -eq 0 ]; then
      deduped+=("$b")
    fi
  done
  bullets=("${deduped[@]}")
}

# Newest v* tag that is an ancestor of COMMIT_SHA (excludes TAG_NAME and any
# newer tags that are not behind this release tip).
previous_release_tag() {
  local t
  while IFS= read -r t; do
    [ -z "$t" ] && continue
    [ "$t" = "$TAG_NAME" ] && continue
    if git merge-base --is-ancestor "$t" "$COMMIT_SHA" 2>/dev/null; then
      printf '%s' "$t"
      return 0
    fi
  done < <(git tag -l 'v*' --sort=-v:refname)
  return 1
}

collect_git_log_bullets() {
  local range="$1"
  local line
  # tformat adds a trailing newline so `read` does not drop the last subject.
  while IFS= read -r line || [ -n "$line" ]; do
    [ -z "$line" ] && continue
    add_bullet "$line"
  done < <(git log --no-merges --pretty=tformat:'%s' "$range" 2>/dev/null | head -20 || true)
}

# Parse GitHub "generate-notes" markdown lines into bullets.
collect_generate_notes_bullets() {
  local raw="$1"
  local line title url rest
  while IFS= read -r line; do
    # "* Title by @user in https://github.com/.../(pull|commit)/..."
    if [[ "$line" == '* '* ]]; then
      rest="${line#'* '}"
      if [[ "$rest" == *' by @'*' in https://github.com/'* ]]; then
        url="${rest##* in }"
        title="${rest% by @*}"
        case "$url" in
          https://github.com/*/pull/*|https://github.com/*/commit/*)
            add_bullet "$title" "$url"
            ;;
        esac
      fi
      continue
    fi
    # "- [Title](https://github.com/.../(pull|commit)/...)"
    if [[ "$line" == '- ['*']('*')' || "$line" == '* ['*']('*')' ]]; then
      rest="${line#*\[}"
      title="${rest%%]*}"
      url="${rest#*(}"
      url="${url%)*}"
      case "$url" in
        https://github.com/*/pull/*|https://github.com/*/commit/*)
          add_bullet "$title" "$url"
          ;;
      esac
    fi
  done <<< "$raw"
}

build_release_notes() {
  if [ -n "${INPUT_NOTES:-}" ]; then
    printf '%s\n' "$INPUT_NOTES"
    return
  fi

  local prev=""
  prev="$(previous_release_tag || true)"

  bullets=()

  # Prefer commit subjects since the previous release tag. This is what lands
  # on master for DailyDash (often without a PR), and matches AGENTS.md.
  if [ -n "$prev" ]; then
    echo "Release notes range: ${prev}..${COMMIT_SHA}" >&2
    collect_git_log_bullets "${prev}..${COMMIT_SHA}"
  else
    echo "No previous v* tag behind ${COMMIT_SHA}; using generate-notes / trigger only" >&2
  fi

  # Augment with GitHub's generate-notes (useful for merged PRs).
  local api_args=(
    -f "tag_name=${TAG_NAME}"
    -f "target_commitish=${COMMIT_SHA}"
  )
  if [ -n "$prev" ]; then
    api_args+=(-f "previous_tag_name=${prev}")
  fi

  local raw=""
  if raw="$(gh api "repos/${REPO}/releases/generate-notes" "${api_args[@]}" --jq .body 2>/dev/null)"; then
    collect_generate_notes_bullets "$raw"
  fi

  # Triggering push subject (first line) — covers bump-only / history edge cases.
  if [ "${#bullets[@]}" -eq 0 ] && [ -n "${TRIGGER_COMMIT_MESSAGE:-}" ]; then
    local trigger
    trigger="$(printf '%s\n' "$TRIGGER_COMMIT_MESSAGE" | head -1)"
    add_bullet "$trigger"
  fi

  dedupe_bullets

  {
    echo "<!-- dailydash-version: ${VERSION_NAME} vc${VERSION_CODE} -->"
    echo "## What's new"
    if [ "${#bullets[@]}" -gt 0 ]; then
      printf '%s\n' "${bullets[@]}"
    else
      echo "- Stability and polish improvements"
    fi
    echo
    echo "[View release on GitHub](https://github.com/${REPO}/releases/tag/${TAG_NAME})"
  }
}

# Notes need tags + enough history to walk prev_tag..HEAD. Actions may still
# be shallow if fetch-depth was constrained; unshallow/deepen as a safety net.
git fetch --tags --force origin >/dev/null 2>&1 || true
if [ "$(git rev-parse --is-shallow-repository 2>/dev/null || echo false)" = "true" ]; then
  git fetch --unshallow origin >/dev/null 2>&1 \
    || git fetch --deepen=200 origin >/dev/null 2>&1 \
    || true
fi
git fetch origin master >/dev/null 2>&1 || true

NOTES="$(build_release_notes)"

if [ ! -f "$APK_SRC" ]; then
  echo "Release APK not found at $APK_SRC" >&2
  find app/build/outputs/apk -type f -name '*.apk' 2>/dev/null || true
  exit 1
fi

# A pre-release (2.0.0-beta.1.0) names its APK by the numbers alone: 1.x clients only
# recognise digits and dots there, and read the full name from the tag instead.
FILE_VERSION="${VERSION_NAME%%-*}"
APK_NAME="DailyDash-${FILE_VERSION}-vc${VERSION_CODE}.apk"
cp "$APK_SRC" "$APK_NAME"

# The Baseline Profile as dex metadata, one per ART profile format (0: Android 9-11,
# 1: Android 12+). The in-app updater installs the matching one beside the APK so ART
# compiles the app at install instead of running it cold until the overnight dexopt.
DM_FILES=()
for n in 0 1; do
  dm_src="app/build/outputs/apk/release/baselineProfiles/${n}/app-release.dm"
  if [ -f "$dm_src" ]; then
    dm_name="DailyDash-${FILE_VERSION}-vc${VERSION_CODE}-p${n}.dm"
    cp "$dm_src" "$dm_name"
    DM_FILES+=("$dm_name")
  fi
done

{
  echo "tag=${TAG_NAME}"
  echo "name=${RELEASE_NAME}"
  echo "apk=${APK_NAME}"
  echo "files<<EOF"
  echo "$APK_NAME"
  for dm in "${DM_FILES[@]+"${DM_FILES[@]}"}"; do echo "$dm"; done
  echo "EOF"
  echo "version_name=${VERSION_NAME}"
  echo "version_code=${VERSION_CODE}"
  echo "notes<<EOF"
  echo "$NOTES"
  echo "EOF"
} >> "$GITHUB_OUTPUT"

echo "Packaged ${APK_NAME}"
echo "----- notes -----"
echo "$NOTES"
