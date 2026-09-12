# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Telegram moderation bot for an Italian tennis-fan group, built on Spring Boot 3.4 /
Java 21, judging messages with an LLM. Groq is the default provider; OpenRouter is
configured alongside it.

**`SPEC.md` is the source of truth.** It is written in Italian and is more detailed than
the code. Before changing behaviour, find the relevant section and follow it; when the
spec and the code disagree, the spec wins unless the code comment explains why it
departs. Section 17 lists what is deliberately still open — don't invent answers to
those, ask. When a decision does resolve one, or when the code has to depart from the
spec, **edit `SPEC.md` in the same PR**: it is a living document, not a historical
record, and the two must not drift.

## Principles

**Simplicity is the mantra.** Prefer it, and enforce it in review. When two designs both
work, ship the smaller one. Don't add a layer, an abstraction, or a config knob before
something actually needs it.

**Clean code, clean architecture, pragmatic programming.** Concretely here: keep the
seams that already exist (`LlmClient`, `LlmJudge`, `ProfileProvider`, `AdminCommand`),
keep policy out of the pipeline and prompt assembly out of the router, and let names
carry the meaning so prose doesn't have to.

**Don't over-comment.** Comment *why*, never *what*. A comment restating the line above
it is noise; one recording a decision, a constraint, or a trap earns its place.

## Commands

```bash
mvn test                                          # full suite
mvn -Dtest=ExecuteCommandTest test                # one class
mvn -Dtest=EscalationServiceTest#climbsOneRungPerPriorExecution test   # one method
mvn compile
mvn spring-boot:run
```

```bash
docker compose up -d      # the deployed path; SAV_DATA_DIR points at the mounted volume
```

Running the app needs `SAV_BOT_TOKEN`, `SAV_MAIN_CHAT_ID`, `SAV_ADMIN_CHAT_ID`, and
`OPENROUTER_API_KEY` in the environment. State is a local H2 file under `data/`; deleting
it resets everything including seeded rules.

There is no `@SpringBootTest`: starting the context registers a long-polling bot that
tries to reach Telegram. Tests are plain JUnit 5 + Mockito, with `TestProperties` as the
fixture for the config record.

## The governing metaphor

The spec frames the main group as the world and the admin chat as a courtroom. That is
not decoration — it is the architecture:

- **The main chat never sees deliberation.** No commands, no logs, no reasoning. The only
  thing the bot ever posts there is the announcement of a penalty actually carried out.
  `ActionExecutor` is the only component permitted to write to the main chat; everything
  else goes through `AdminNotifier` to the admin chat. Keep it that way.
- **Admins are judges and police both**, so a single admin confirming is sufficient for
  any action, including rulebook approval. No quorum.

## Flow

`SavonarolaBot` (long-polling consumer) → persist to message store → drop if sender is an
admin → `ModerationPipeline` (`@Async`) → assemble `JudgmentInput` → `LlmJudge` → raw
`Judgment` → `EscalationService` proposes an `Action` → `DecisionRouter` applies policy.

`ModerationPipeline` decides *what the model sees*. `DecisionRouter` decides *what
happens next*. Keep policy out of the pipeline and prompt assembly out of the router.

### Invariants that are easy to break

- **Admins are never sanctionable.** Checked dynamically against `getChatAdministrators`
  (`AdminRegistry`, 5-minute cache), never against a configured list, and checked *before*
  any LLM call. `ExecuteCommand` re-checks at execution time in case of promotion since.
- **A ban is never automatic in any mode.** The escalation ladder tops out at
  `ADMIN_REVIEW`, which is not an action — it is the bot declining to go further.
- **Ladder position is derived, never stored**: `EscalationService` counts `EXECUTED`
  decisions inside the decay window, so a clean streak resets a user with no counter to
  maintain. Any new `DecisionStatus` must not accidentally count toward it — `LOGGED`,
  `PENDING` and `DISMISSED` deliberately do not.
- **One LLM call per message, covering all active rules at once** (SPEC 4.4) — cheaper,
  and the model sees the whole picture. Do not fan out per rule.
- **`reasoning` is always populated**, even when `violated: false`. It is what an admin
  reads to decide, so it is written in Italian while the rest of the prompt is English.

### Operating mode is the whole safety model

`LOG_ONLY` → `ON_DEMAND_ACTION` → `LIVE_ACTION`, gating in `DecisionRouter`.
`LOG_ONLY` means *no action is possible, not even manually* — `/execute` refuses outright
there. It still persists `LOGGED` decisions for violations, because the point of that mode
is measuring accuracy before the bot is given real power.

Below the confidence threshold the bot flags without proposing a penalty: it does not
propose a sentence it isn't confident in.

**`SettingsService` is the authority on the mode and the threshold, not the config
record.** `/mode` and `/threshold` persist overrides that survive restarts, so
`props.operatingMode()` is the declared starting point and answers the wrong question at
runtime. Read both through `SettingsService`.

## Traps

- **Admin messages are sent with `parseMode=HTML`.** Every interpolated value — member
  text, model-written reasoning, rule ids, exception messages — must go through
  `Html.escape`. An unescaped `<` makes Telegram reject the send, so the verdict admins
  are waiting on vanishes silently. There is a regression test for this.
- **`ModerationPipeline.process` is `@Async void`**, so anything thrown there is
  swallowed. Log and return instead of throwing.
- **LLM output is untrusted.** `ModelChainJudge` strips markdown fences, tolerates
  missing fields, rejects a claimed violation with no rule id, and falls back across the
  configured models in order. Free model catalogues change without notice and are rate
  limited per model, so never hard-code a model and keep the `LlmClient` / `LlmJudge`
  seams intact.
- **Adding a provider is configuration, not code** — while it speaks OpenAI's
  `/chat/completions`. `OpenAiCompatibleClient` covers Groq, OpenRouter and Mistral; one
  that does not (Gemini) needs its own `LlmClient`. Only the active provider is ever
  contacted; there is no routing between them yet.
- **The message store exists because the Bot API cannot fetch an arbitrary message by
  id.** If the bot did not see a message go past, it does not have it — `/train` on an
  old link legitimately fails, and that is reported, not worked around.
- **Some `/stats` figures are in-memory and say so.** Token spend and the extended-query
  rate are counters reset by a restart, reported as "since startup"; volume and actions
  come from persisted rows and honour the requested period. Don't quietly mix the two.
- **Profiles are cold until the batch job has run.** `ProfileService` reads only what
  `ProfileBatchJob` wrote overnight, so on a fresh install `profileFor` returns null and
  `negativeInteractionCount` returns 0 — the judge works from the context window alone and
  the extended-history lookup never fires. That is expected, not a bug. Pair signals also
  decay: one the batch has not refreshed inside the detection window is ignored.

## Conventions

- **Language split (SPEC 16):** code, comments, config keys, rule ids, and LLM prompts in
  English. Anything a group member or admin reads — bot replies, `reasoning`, rule
  examples — in Italian.
- **Adding an admin command:** implement `AdminCommand`, annotate `@Component`, and
  `CommandDispatcher` discovers it. Commands are accepted only in the admin chat and only
  from an administrator *of the main group*; membership of the admin chat grants nothing.
  Throw `IllegalArgumentException` for bad input and the dispatcher replies with `usage()`.
- **`RuleSeeder` never overwrites an existing rule**, so a restart cannot undo curation
  done through `/train`. Preserve that if you touch it.

## Releasing

The git tag is the version: `v1.4.0` publishes `1.4.0`. The pom stays on a `-SNAPSHOT`
and CI stamps it from the tag, so never hand-edit `<version>` to cut a release. A tag
containing a hyphen is a prerelease and does not move `latest`.

## Working agreements

- **Be extremely concise.** No preamble, no recap of what you just did, no listing
  options you won't take. This matters most where a human has to act on the text:
  questions to the user, PR bodies, and the bot's own admin-facing output — an admin
  reading a verdict is deciding something, not browsing.
- **Always write tests, and make them mean something.** Unit, integration, UI-automated
  or performance — whichever fits what changed. A test that restates the implementation
  or asserts a mock was called proves nothing; test the behaviour that would actually
  break, including the path where it fails.
- **Run `mvn test` before every commit.** Never commit on a red or unrun suite.
- **Ask before adding a Maven dependency.** The footprint stays deliberate; no new
  libraries slipped in as a side effect of a feature.
- **Flag spec deviations in the PR body**, not only in a code comment. If the code
  departs from `SPEC.md`, resolves a contradiction in it, or answers one of the section
  17 open questions, say so where it can be reviewed — and make the matching spec edit in
  the same PR.
