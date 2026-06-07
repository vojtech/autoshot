---
name: inline-ai-doc-encoder
description: Automatically generates language-agnostic, token-dense code comments/docstrings for public functions and interfaces to optimize AI semantic indexing.
---

# Inline AI Doc Encoder

Use this skill whenever a user asks to write documentation comments, generate docstrings, or prepare an interface's public surface area for external AI onboarding.

## Execution Directives
Analyze the target code block and inject a structured docstring block directly above the declaration. The output must strictly strip out conversational fluff and comply with the following structural layout:

1. **Semantic Blueprint Line:** The very first line must be a dense, keyword-heavy declaration of the code's exact purpose. Do not use filler introductions like "This method handles...".
2. **Execution Context & Constraints:** Explicitly define the state mutations, execution environment (e.g., threading/asynchrony constraints), lifecycle bounds, and memory side effects.
3. **Explicit Type Contract:** Document the explicit input boundaries and the predictable return shape or reactive stream types.
4. **Punctuation-Lean Usage Recipe:** Provide a hyper-focused, minimal code snippet showing the most idiomatic consumption pattern.

## Universal Target Pattern
```text
[Opening Comment Syntax]
 * [Action Core Verb] + [Target Component Relationship].
 *
 * - Environment: [e.g., IO Bound / UI Main / Deterministic Sandbox]
 * - State Impact: [e.g., Thread-safe / Mutates internal local cache / Idempotent execution]
 *
 * ### Minimal Usage
 * ```[language]
 * val response = component.execute(payload)
 * ```
 * @param [name] [Validation criteria, nullability boundaries, or functional configuration requirements]
 * @return [Concrete type shape, execution wrapper, or failure-safe result stream representation]
 * @throws [Exception/Error Type] [The structural failure condition that triggers this branch]
[Closing Comment Syntax]