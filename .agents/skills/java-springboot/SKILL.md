---
name: java-springboot
description: Resolve Bemol API design questions about entity, use-case, persistence or HTTP responsibility.
---

# Bemol API design

Use the relevant domain, persistence or HTTP section of [the repository contract](../../../AGENTS.md) and adjacent implementation to resolve the responsibility in question. Routine Java edits do not need a design review.

Trace the affected state transition and its caller: identify the invariant, its owner and any transaction or tenant boundary needed to enforce it. Prefer a change within existing feature boundaries over introducing a new architectural layer.

For a design-only request, explain the recommended owner and concrete tradeoff without editing code. For authorized implementation, complete the affected path without expanding into a general refactor, dependency change or unrelated migration.
