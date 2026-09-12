# savonarola

A LLM-based Telegram autonomous moderator.

The design lives in [SPEC.md](SPEC.md). In short: the bot judges messages in a group
against a written rulebook, reports its reasoning to a private admin chat, and — only
once admins trust it enough to raise the operating mode — carries out mutes on its own.
The main chat never sees the deliberation, only the announcement of a penalty.

## Running it

Requires JDK 21 and Maven.

```bash
export SAV_BOT_TOKEN=...          # from @BotFather
export SAV_MAIN_CHAT_ID=...       # the group being moderated
export SAV_ADMIN_CHAT_ID=...      # the private admin chat
export OPENROUTER_API_KEY=...

mvn spring-boot:run
```

The bot must be an administrator of the main group with permission to restrict members,
and privacy mode must be **off** (`/setprivacy` in @BotFather) or it will only receive
commands rather than the conversation it is supposed to judge.

State is kept in a local H2 file under `data/`. The five rules from SPEC section 2.3 are
seeded on first start; existing rules are never overwritten.

## Operating modes

Set `savonarola.operating-mode` in `application.yml`:

| Mode | Behaviour |
|---|---|
| `LOG_ONLY` | Judges everything, acts on nothing — not even on admin command. Verdicts are reported to the admin chat and violations are persisted so accuracy can be measured before the bot is given real power. **Start here.** |
| `ON_DEMAND_ACTION` | Above the confidence threshold, proposes a penalty with a ready `/execute` command and waits for an admin. |
| `LIVE_ACTION` | Above the confidence threshold, acts on its own. Still stops at the top of the escalation ladder. |

A ban is never an automatic action in any mode.

## Admin commands

Only accepted in the admin chat, and only from someone who is an administrator of the
**main** group — membership of the admin chat grants nothing. Authority is re-checked
against Telegram, never read from config.

| Command | Purpose |
|---|---|
| `/execute <decisionId>` | Apply the proposed penalty as-is |
| `/execute <decisionId> duration=<Nm\|Nh\|Nd>` | Apply it with a different duration, recorded as a modification |
| `/execute <decisionId> dismiss` | Discard the decision, no action |
| `/train <rule_id> <positive\|negative> <link>` | Add a labelled example to a rule. Never retroactive. |
| `/dynamic <@a\|id> <@b\|id> <description>` | Record a known dynamic between two members. The batch job never overwrites it. |

## Profiling

A nightly job (`savonarola.profile.cron`, 04:00 by default) summarises how each active
member typically writes and how often one member is hostile toward another. Those
summaries are what let the judge tell established banter from someone being targeted.

Profiles are cold on a fresh install: until the first run, judgments work from the recent
conversation window alone and the extended-history lookup never fires. `/dynamic` is the
way to seed what the bot has no history to infer yet.

Each user and each pair costs one call on the cheap model, against the same free-tier
budget as live judging, so the run is capped (`max-users-per-run`, `max-pairs-per-run`)
and works busiest-first.

## Not implemented yet

- `/regolamento aggiorna` — LLM-assisted compilation of the rulebook with admin approval (SPEC 2.1)
- `/stats` — usage and accuracy reporting (SPEC 13)
- Commands to toggle rules, mode, and thresholds at runtime (SPEC 12)

## Tests

```bash
mvn test
```
