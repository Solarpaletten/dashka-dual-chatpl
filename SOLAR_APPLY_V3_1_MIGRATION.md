# solar-apply.js v3.1 — Migration Notes

**Previous version:** v3 FINAL (770 lines)
**New version:** v3.1 (886 lines)
**Sprint:** 4C.9 — tooling improvement
**Files changed:** 1 (solar-apply.js only)

---

## What changed — in one paragraph

The git step (function `runGitCommit`) now (1) auto-detects your current branch
instead of hardcoded `main`, (2) splits commit and push into two separate
decisions instead of one, (3) lets you override the push target branch on the
fly, (4) warns loudly if you're about to push to a protected branch
(`main`/`master`) and requires typed confirmation, and (5) NEVER does
`git add .` — only stages files declared by the sprint manifest. Everything
else in your working tree (`.solar-backups/`, `sprint/`, tmp files, unrelated
edits) is left alone.

---

## Before vs after — flow comparison

### Before (v3)

```
📝 GIT — Push to GitHub?

   Changed files:
   ?? .solar-backups/
   ?? sprint/
   M  app/page.tsx
   M  lib/ai/config.ts

   Push to GitHub? [Y] yes [E] edit [N] skip > Y
   ↓
   git add .              ← captures EVERYTHING in working tree
   git commit -m "..."
   git push origin main   ← always pushes to main
```

**Problems:**
- `.solar-backups/` and `sprint/` get committed by accident
- Push goes to `main` regardless of current branch
- Architect had to type `[N] skip` every time, then commit/push manually

### After (v3.1)

```
📝 GIT — Commit & Push

   Working tree status (git status --porcelain):
   ?? .solar-backups/
   ?? sprint/
    M app/page.tsx
    M lib/ai/config.ts

   Will stage ONLY sprint files (2):
   + app/page.tsx
   + lib/ai/config.ts

   ⚠ Not staging (left in working tree):
   - .solar-backups/
   - sprint/
   (these are NOT included in this commit — handle separately)

   Auto commit message:
   "task4c7d: ~1 patched — config [SDP v3]"

   [Phase 1] Commit changes? [Y] yes [E] edit [N] no > Y
   ↓
   git add -- app/page.tsx lib/ai/config.ts   ← scoped staging
   git commit -m "..."
   ✅ Committed locally

   Detected branch:  fix/config-lazy-eval
   Push target:      origin/fix/config-lazy-eval

   [Phase 2] Push to origin/fix/config-lazy-eval? [Y] yes [O] override [N] no > Y
   ↓
   git push origin fix/config-lazy-eval   ← pushes to current branch
   ✅ Committed & pushed → origin/fix/config-lazy-eval

   Next step: open a PR from "fix/config-lazy-eval" → main
   gh pr create --base main --head fix/config-lazy-eval
```

---

## Three new safety guards

### 1. Sprint-scoped staging (NEVER `git add .`)

`runGitCommit` now reads `report.created` and `report.modified` (already
tracked by solar-apply earlier in the pipeline) and stages only those exact
paths via `git add -- <file1> <file2> ...`.

Everything else in `git status --porcelain` is shown but explicitly left
alone. If `.solar-backups/`, `sprint/`, or any unrelated edit appears in
your working tree, the script tells you it's being skipped.

If your sprint legitimately needs to commit something not in the manifest,
you handle it separately after this script returns.

### 2. Branch auto-detection

```js
currentBranch = execSync('git rev-parse --abbrev-ref HEAD').trim()
```

The current branch is detected at the start of the git step. All prompts,
push commands, and "next step" hints use the actual branch name.

### 3. Protected-branch typed confirmation

If `currentBranch === 'main'` or `'master'`, the script:

1. Shows three red `⚠️ WARNING` lines
2. Requires you to **type the branch name exactly** (not just press Y)
3. If anything else is typed, push is cancelled and commit remains local

This prevents the muscle-memory `Y` from pushing to production accidentally.

---

## Three new flow options

### Phase 1 — Commit only

`[Phase 1] Commit changes? [Y] yes [E] edit message [N] no`

You can commit locally and stop. Use case: local checkpoint before letting
Audit review the diff offline, before continuing in a new session, or before
preparing a PR.

### Phase 2 — Push or skip

`[Phase 2] Push to origin/<branch>? [Y] yes [O] override [N] no`

After commit succeeds, push decision is separate. Use case: review `git log`,
run more tests, hand off to another person who'll push.

### Phase 3 — Override target branch

If you choose `[O]` at Phase 2:

```
Target branch name (will create on remote if new): docs/ai-governance-v1
```

The local branch is unchanged. Push uses `<local>:<override>` refspec, so
GitHub creates the remote branch named what you typed.

Use case: you forgot to rename your local branch but want the PR to come
from a properly-named remote branch.

---

## Edge cases handled

| Case | Behaviour |
|------|-----------|
| Working tree clean | `Nothing to commit` — return immediately |
| Cannot detect branch (git error) | Red error message, return |
| Unrecognized commit answer | Treated as `N`, message printed |
| `git add` fails | Red error, return (no commit) |
| `git commit` fails | Red error, return (no push) |
| `git push` fails | Red error + shows manual command |
| Empty branch name in override | Cancels with hint |
| Pushing to protected branch | Typed-confirm required |
| Push to override branch | Uses `local:remote` refspec syntax |

---

## Backwards compatibility

- ✅ Same function signature: `runGitCommit(taskName, report)`
- ✅ Same `report.created` / `report.modified` shape consumed
- ✅ Same auto-message format
- ✅ Same `--auto` / `--dry` flags respected (AUTO mode now also passes through to new prompts)
- ✅ Call site at line 873 in main() unchanged
- ✅ All other pipeline steps (audit, apply, tsc, build, bundle, history) unchanged

**No changes to other functions.** Only `runGitCommit` was replaced. Header
comment was updated to v3.1 with CHANGES section.

---

## How to install

```bash
# Backup current version
cp solar-apply.js solar-apply.js.v3-backup

# Replace with v3.1
# (copy the new solar-apply.js from delivery into project root)

# Verify syntax
node --check solar-apply.js
# Should print nothing — exit 0 means valid

# Optional: dry-run a sprint to confirm new prompts work
# (use any existing sprint archive)
```

---

## How to verify it works on the next sprint

Next time you run `node solar-apply.js sprint-X.tar.gz`:

1. After build/bundle steps, you should see `📝 GIT — Commit & Push`
2. You should see `Will stage ONLY sprint files (N):` with explicit list
3. You should see two separate prompts (Phase 1 commit, Phase 2 push)
4. You should see `Detected branch: <your branch>` before push prompt
5. Push should go to your actual branch, not `main`

If any of those don't appear: something's wrong, send back the output.

---

## Rollback

```bash
cp solar-apply.js.v3-backup solar-apply.js
```

Or restore from the delivery package's original copy.

---

## Why this is direct file replacement, not full PR sprint

`solar-apply.js` is **tooling**, not deployed application code. It runs on
Leanid's local machine. Changes have:

- Zero impact on dashka.ai production
- Zero impact on /privacy, /terms, API routes
- Zero impact on Vercel build
- Zero impact on Google Play review

So formal PR + audit cycle for a local tool would be overhead without value.
Smoke test (16 checks passed) and Leanid manual review on next sprint use
provide enough verification.

If this changes someday — e.g., solar-apply.js gets versioned in repo and
shared across multiple developers — it graduates to full sprint cycle.

---

🛰️ solar-apply.js v3.1 — ready to drop in.
