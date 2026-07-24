---
name: gh-update-branch-pr
description: Use when asked to update, rewrite, refresh, or synchronize the title or description of the GitHub pull request associated with the current Git branch.
---

# Update Branch PR

## Overview

Update the current branch's existing GitHub PR title and body from the PR's actual diff. Follow the repository PR template and require an explicit issue-association decision before writing.

## Workflow

1. **Preflight.** Run `gh --version` and `gh auth status`. Resolve the repository root, current non-detached branch, and its PR with `gh pr view --json number,url,state,title,body,baseRefName,headRefName`. Stop if the directory is not a Git repository, the PR does not exist, the PR is not open, or its head does not match the current branch. Never create a PR, commit, switch branches, or push.

2. **Read repository guidance and template.** Inspect applicable `AGENTS.md` and contribution guidance. Search case-insensitively for PR templates in the repository root, `docs/`, `.github/`, and their `PULL_REQUEST_TEMPLATE/` directories. Prefer the template already reflected by the current PR body; otherwise use the only template or the template whose purpose clearly matches the diff. If multiple templates remain plausible, ask the user to choose. If no template exists, preserve the current body structure or use `Summary`, `Related Issues`, `Changes`, and `Test Plan`.

3. **Analyze the PR, not unpushed work.** Read `gh pr diff --name-only`, `gh pr diff --patch`, and `gh pr view --json commits,files`. Use the base branch, changed behavior, commits, and repository conventions to draft a concise title. Do not infer behavior from the branch name alone. Preserve accurate manual content from the existing body.

4. **Resolve issue association before any write.** The user's current request must explicitly say whether to associate an issue.
   - If omitted, ask: `是否关联 issue？` and wait.
   - If no, write `None` in the template's related-issue field.
   - If yes with a URL, verify it with `gh issue view <url> --json number,title,state,url` and require it to belong to the PR repository.
   - If yes without a URL, search that repository's open and closed issues with `gh issue list --state all --search ... --json number,title,state,url`, using branch tokens, commit subjects, changed modules, and diff concepts. Automatically choose only one unambiguous semantic match. Otherwise show the best candidates with URLs and ask the user to select; never invent or create an issue.
   - Use `Related to #<number>` by default. Use `Fixes`, `Closes`, or `Resolves` only when the user explicitly requests automatic closure and the PR fully resolves the issue.

5. **Fill the complete template.** Preserve its headings, order, and checklist. Replace instructional comments and placeholders with evidence-backed content. Mark a test or checklist item complete only when supported by commands actually run or facts verified during the task; otherwise leave it unchecked and explain `Not tested` when the template requires it. Do not add claims unsupported by the diff or verification output.

6. **Update once and verify.** Put the exact body in a temporary Markdown file, then run:

```bash
gh pr edit <pr-url> --title '<title>' --body-file <body-file>
gh pr view <pr-url> --json number,url,title,body,baseRefName,headRefName
```

Compare the returned title and body with the intended values. Report the PR URL, final title, issue association, and verification result. Remove only the temporary body file created for this operation.

## Quick Reference

| Situation | Action |
| --- | --- |
| No current-branch PR | Stop; do not create one |
| Issue decision missing | Ask and wait before editing |
| Multiple plausible templates or issues | Ask the user to choose |
| No test evidence | Leave unchecked; state not tested |
| Association requested without closure | Use `Related to #N` |

## Common Mistakes

- Do not overwrite the body with a generic format when a repository template exists.
- Do not describe local unpushed changes as part of the remote PR.
- Do not treat matching keywords as enough evidence to select an issue.
- Do not use a closing keyword merely because the branch is a bug fix.
