# 🛰️ Sprint 4D.2 — Execution Instructions

**For:** Architect (Leanid)
**Sprint:** 4D.2 — Disable Auto Backup / Device Transfer for history privacy
**Branch:** `fix/history-backup-privacy`
**Files changed:** 3 (1 modified, 2 new)
**Production code modified:** 0 lines of Kotlin
**Risk profile:** very low (manifest + XML only)

---

## Background

Field test on 12 May 2026 revealed that translation history carries over
between devices on the same Google account: install of v0.4.6-mama on phone B
showed the 6 entries (14:10 timestamps) from phone A's v0.4.6-history-dedup.

Forensic verification confirmed: no pre-bundled data in APK. The carry-over
is caused by Android's default Auto Backup behavior. `AndroidManifest.xml`
had `android:allowBackup="true"` (default), which automatically backed up the
DataStore Preferences files (history + user prefs) to Google Drive, then
auto-restored them on any device install with the same Google account.

**This sprint disables Auto Backup and device-transfer for this app.**

Per Coordinator (Dashka) decision: **Option A — clean break.** No wipe of
existing on-device data. Old Drive backups expire passively (~60 days
inactivity).

---

## Prerequisites

- [ ] Local clone of `DashkaAndroid` repository up to date with `main`
- [ ] Working tree clean (`git status` shows nothing pending)
- [ ] `solar-apply.js` v3.1 at project root (the branch-aware version)
- [ ] Android Studio available for build verification

---

## Execution sequence

### Step 1 — Sync main

```bash
cd ~/path/to/DashkaAndroid
git checkout main
git pull origin main
```

### Step 2 — Create fix branch

```bash
git checkout -b fix/history-backup-privacy
```

### Step 3 — Place archive

Place `sprint-4d2.tar.gz` somewhere accessible (e.g., `~/Downloads/`).

### Step 4 — Run solar-apply.js

```bash
node solar-apply.js path/to/sprint-4d2.tar.gz
```

Expected solar-apply v3.1 output sequence:

1. **Pre-Deploy Audit**
   - 2 NEW (`backup_rules.xml`, `data_extraction_rules.xml`)
   - 1 PATCH (`AndroidManifest.xml`)
   - 0 IDENTICAL
2. **Prompt:** `Ready to deploy? [Y]` → `Y`
3. **Controlled Apply** — per-file diff review
   - For `AndroidManifest.xml`, expected diff is small (1 line removed, 2 lines added on the `<application>` element)
   - For the two new XML files, full content shown — review or `D` to see full
   - Apply each with `Y`. Backup auto-saved to `.solar-backups/sprint-4d2/`
4. **Dependency check** — skipped (no Gradle changes)
5. **TypeScript scan** — N/A (no .ts files)
6. **Build prompt:** `Run pnpm build?`
   - **For Android: skip `pnpm build`**. solar-apply was designed for Next.js. Choose `N`.
   - We'll verify with Gradle separately in Step 6.
7. **Bundle + History** — created automatically
8. **Git Commit prompt (v3.1 behavior):**
   - solar-apply v3.1 should now show: `Will stage ONLY sprint files (3)` with explicit list
   - `[Phase 1] Commit changes? [Y/E/N]` → `Y` (or `E` to edit message; default is acceptable)
   - `Detected branch: fix/history-backup-privacy`
   - `[Phase 2] Push to origin/fix/history-backup-privacy? [Y/O/N]` → `Y`

If solar-apply commit auto-message looks acceptable (matches the format below),
take it. Otherwise edit:

```
fix(privacy): disable Auto Backup and device-transfer for history

Android Auto Backup was silently restoring DataStore Preferences (translation
history + user prefs) across devices on the same Google account. Field-tested
on 12 May 2026: phone B with fresh v0.4.6-mama install inherited 6 history
entries from phone A's separate v0.4.6 install.

Three-layer defense:
- AndroidManifest: allowBackup=false (global)
- backup_rules.xml: explicit DataStore excludes (Android 6-11)
- data_extraction_rules.xml: cloud + device-transfer excludes (Android 12+)

No Kotlin or UI changes. Existing on-device history is preserved across
update. Drive backups expire passively (~60 days inactivity).

Sprint: 4D.2
Resolves: cross-device history leak observed in field test
Audited-by: pending
```

### Step 5 — Verify git state

After solar-apply finishes:

```bash
git status
```

Expected:
```
On branch fix/history-backup-privacy
nothing to commit, working tree clean
```

(Working tree clean because solar-apply v3.1 staged only the 3 sprint files
and committed them. `.solar-backups/`, `.solar-bundles/`, `.solar-history/`,
`sprint/` should be ignored by `.gitignore` from Sprint 4C.9-hygiene.)

```bash
git log --oneline -3
```

Should show the new commit on top.

### Step 6 — Local Gradle build verification

This is the build gate for Android (replacing solar-apply's pnpm build):

```bash
./gradlew assembleDebug
```

Expected: **BUILD SUCCESSFUL**. The manifest change is trivial and the two
new XML files are referenced correctly (`@xml/backup_rules` and
`@xml/data_extraction_rules`). Android's resource compiler will validate
schema.

If build fails with "resource not found" errors about backup rules → check
that the two XML files landed in `app/src/main/res/xml/` (not elsewhere).

### Step 7 — Install on physical device for smoke check

```bash
./gradlew installDebug
```

Or open in Android Studio and click ▶ Run.

Smoke check (NOT the full verification — that comes after merge):

- [ ] App opens normally
- [ ] Existing history (if you have any) is still visible (data preservation)
- [ ] You can make a new translation
- [ ] No crash on startup
- [ ] No `BackupAgent` errors in `adb logcat` (search "BackupManager")

### Step 8 — Push (if not already pushed by solar-apply)

If you chose `[N]` to skip push during solar-apply, push now:

```bash
git push origin fix/history-backup-privacy
```

### Step 9 — Open Pull Request

On GitHub:

**Title:**
```
fix(privacy): disable Auto Backup and device-transfer for history
```

**Description (paste this — based on .github/pull_request_template.md if Sprint 4C.8 has been merged on this repo, otherwise paste manually):**

```markdown
## Sprint
**Sprint ID:** 4D.2
**Type:** fix
**Branch:** fix/history-backup-privacy

## Summary
Disables Android Auto Backup and Android 12+ device-transfer for Dashka
Translate Lite. Translation history (DataStore Preferences) was being
auto-restored across devices on the same Google account. Field test on
12 May 2026 confirmed the issue: second device on the same account inherited
6 history entries from a different device's install.

Three-layer defense: allowBackup=false + legacy backup_rules + modern
data_extraction_rules.

## Changes
**New files:**
- app/src/main/res/xml/backup_rules.xml
- app/src/main/res/xml/data_extraction_rules.xml

**Modified files:**
- app/src/main/AndroidManifest.xml

**Deleted files:** none

## Forbidden zones confirmed untouched
- [x] app/src/main/java/* (no Kotlin changes)
- [x] app/src/main/res/values/* (no theme/strings/colors changes)
- [x] app/src/main/res/drawable/*, mipmap-*/* (no asset changes)
- [x] app/build.gradle.kts, build.gradle.kts (no Gradle changes)
- [x] gradle/libs.versions.toml (no dependency changes)
- [x] README.md (deliberately stale until separate sync sprint)

## Local verification done
- [x] `./gradlew assembleDebug` PASSES
- [x] App installs on physical device
- [x] App opens, existing history visible
- [x] No BackupAgent errors in logcat
- [ ] Full 5-scenario verification per metadata.json — to be done post-merge

## Preview / staging
- N/A (Android — no preview deployment; build artifact is the APK)

## Audit
> To be filled by Audit role.

- [ ] Structural — title, branch, manifest match
- [ ] Scope — only allowed paths changed
- [ ] Quality — manifest attributes correct, XML rules syntactically valid
- [ ] Behaviour — five scenarios from metadata.json covered by rules
- [ ] Documentation — diff explained; defense-in-depth approach clear

**Audit verdict:**
**Audited by:**

## Architect approval
- [ ] Audit returned GREEN
- [ ] Local build PASSES
- [ ] Smoke install on physical device PASSES
- [ ] Manifest matches actual diff
- [ ] Working tree clean

**Merge command planned:** `gh pr merge <N> --merge`

## Rollback plan
- **Pre-merge:** `git branch -D fix/history-backup-privacy`
- **Post-merge:** `git revert -m 1 <merge-sha>` (low risk — restores allowBackup=true)
- **Tool rollback:** `node solar-apply.js sprint-4d2 --rollback`

## Related
- Forensic report: in-session by Claude on 12 May 2026
- Field evidence: 3 photos provided by Leanid
- Depends on: Sprint 4D.1 audit (informational, no code dependency)
- Resolves: cross-device history leak via Android Auto Backup

🛰️ Sprint 4D.2.
```

### Step 10 — Hand off to Audit

Share PR URL with Kimi for audit. Audit reviews against the scenario checklist
in `metadata.json` `verification_checklist_per_dashka_request` and the per-PR
checklist in the description.

Expected verdict: `GREEN — ready to merge.` (Sprint is minimal and surgical.)

### Step 11 — Merge after GREEN

```bash
gh pr merge <NUMBER> --merge
```

### Step 12 — Build release APK with new version

Coordinator's call on version bump. Suggested:

- `versionCode = 21` (from 20)
- `versionName = "0.4.7-history-private"` (or whatever Dashka decides)

This is **out of scope for THIS sprint** — would be a separate version-bump
sprint or a chore commit. Sprint 4D.2 just lands the fix.

### Step 13 — Full verification (post-merge, post-version-bump APK build)

Run all 5 scenarios from `metadata.json` → `verification_checklist_per_dashka_request`:

- **VER-A** — Fresh install → empty history
- **VER-B** — 2 translations → 2 history entries
- **VER-C** — Uninstall + reinstall same device → empty history (was broken)
- **VER-D** — Install on second device same account → empty history (was broken)
- **VER-E** — Update existing install → existing history preserved

Especially VER-C and VER-D — these are the scenarios that were broken in
v0.4.6 and the whole point of this sprint.

---

## Rollback

If something goes wrong at any stage:

### Level 1 — before merge

```bash
node solar-apply.js sprint-4d2 --rollback   # restores AndroidManifest.xml backup
git checkout main
git branch -D fix/history-backup-privacy
git push origin --delete fix/history-backup-privacy  # if pushed
```

### Level 2 — after merge

```bash
git revert -m 1 <merge-sha>
git push origin main
```

Reverts the manifest change (allowBackup back to false won't undo any
Drive backup that didn't exist yet, so this is a true rollback).

---

## Forbidden actions during this sprint

- ❌ Do NOT modify Kotlin code
- ❌ Do NOT modify UI / Compose files
- ❌ Do NOT add "while we're here" features (swipe gestures, dual-pane, etc.)
- ❌ Do NOT bump versionCode/versionName in THIS sprint (separate chore)
- ❌ Do NOT change package name
- ❌ Do NOT change signing config
- ❌ Do NOT touch Gradle files
- ❌ Do NOT add wipe-on-upgrade logic (Option A explicitly avoids this)
- ❌ Do NOT update README in this sprint (separate sync sprint)

---

## Why no version bump in this sprint

This sprint ships the fix as a code change ready to be released. Version-bump
is a separate decision that depends on release cadence (does this go out as
0.4.7? bundled with other fixes? wait for Sprint 4D.3?). Keeping version bump
out of THIS sprint preserves the "one sprint = one concern" discipline.

When release is ready, a 1-line `chore(release): bump to 0.4.7-history-private`
commit handles it.

---

## Approval chain

```
✅ Coordinator (D):  scope approved, Option A locked
✅ Engineer (C):     package generated, self-application verified
⏳ Architect (L):    apply → build → push → PR
⏳ Audit (K):        PR audit → GREEN/BLOCKER
⏳ Architect (L):    merge after GREEN
⏳ Architect (L):    full 5-scenario verification (post-release)
```

🛰️ End instructions — Sprint 4D.2.
