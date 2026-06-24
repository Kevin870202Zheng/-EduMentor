# Workspace — Sub-Agent Collaboration Directory

This directory is the shared workspace for governance-managed sub-agents.

## Directory Structure

- `ARTIFACT_INDEX.md` — Index of all deliverables produced by sub-agents.
**Every sub-agent must read this first** and append their own entry upon completion.
- `artifacts/` — Deliverable documents (design docs, specs, plans).
Naming convention: `{task-id}-{brief-description}.md`
- `references/` — External references collected during Phase 1 research.
- `PLAN.md` — Current plan tree (generated before dispatch).
- `TASK_CONTEXT.json` — Machine-readable task context (for dispatch).

## Rules for Sub-Agents

1. **READ** `ARTIFACT_INDEX.md` first to discover existing deliverables.
2. **WRITE** design documents to `artifacts/{task-id}-{name}.md`.
3. **WRITE** code to the appropriate source directories under the project root.
4. **APPEND** your entry to `ARTIFACT_INDEX.md` after completing work.
