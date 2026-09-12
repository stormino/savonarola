# Savonarola

An LLM-based autonomous moderator for Telegram groups.

Savonarola judges messages against a written rulebook, reports its reasoning to a private
admin chat, and — only once admins have raised its operating mode — carries out mutes on
its own. The moderated group never sees the deliberation, only the announcement of a
penalty actually applied.

The full design lives in [SPEC.md](SPEC.md) (Italian). The guiding idea: the group is the
world, the admin chat is the courtroom, and the bot is a judge whose sentences an admin
signs off until it has earned the right to sign its own.

---

## Requirements

- A Telegram group and a separate private chat for admins
- An API key for an LLM provider — [Groq](https://console.groq.com/keys) by default
- Docker, or JDK 21 and Maven to run from source

## 1. Create the bot

1. Talk to [@BotFather](https://t.me/BotFather) and `/newbot`. Keep the token.
2. **Turn privacy mode off**: `/setprivacy` → select the bot → **Disable**. With privacy
   mode on, the bot only receives commands and cannot see the conversation it is meant to
   judge. This is the single most common setup mistake.
3. Add the bot to the group being moderated, and **promote it to administrator** with the
   *Restrict members* permission. Without it, mutes fail.
4. Add the bot to the admin chat too — that is where it reports and takes commands.

## 2. Find the chat IDs

Send a message in each chat, then open:

```
https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates
```

Take `message.chat.id` from the results. Supergroup ids are negative and look like
`-1001234567890`.

## 3. Configure

```bash
cp .env.example .env
```

| Variable | Required | What it is |
|---|---|---|
| `SAV_BOT_TOKEN` | yes | Token from @BotFather |
| `SAV_MAIN_CHAT_ID` | yes | The group being moderated |
| `SAV_ADMIN_CHAT_ID` | yes | The private admin chat |
| `GROQ_API_KEY` | yes | https://console.groq.com/keys (or `OPENROUTER_API_KEY` if you switch provider) |
| `SAV_LLM_PROVIDER` | no | `groq` (default) or `openrouter` |
| `SAV_PRIMARY_MODEL` | no | Judgment model. Free catalogues change without notice — check yours is still live. |
| `SAV_FALLBACK_MODEL` | no | Used when the primary is rate-limited or down |
| `SAV_PROFILE_MODEL` | no | Cheap model for nightly profiling |
| `SAV_DATA_DIR` | no | Where the H2 database file lives (`/data` in Docker) |

Everything else is in [`application.yml`](src/main/resources/application.yml): thresholds,
the escalation ladder, retention, and the profiling schedule.

## 4. Run

```bash
docker compose up -d
docker compose logs -f
```

Or from source:

```bash
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

On first start the five rules from SPEC section 2.3 are seeded, and the bot begins
judging in `LOG_ONLY`. You should see verdicts appear in the admin chat within a few
messages.

> The database under `/data` holds the message store, the moderation record, the
> escalation ladder and every trained example. Losing that volume resets all of it.

---

## Rolling it out

The operating mode is the whole safety model. Start at the bottom and move up only when
the verdicts read right.

| Mode | Behaviour |
|---|---|
| `LOG_ONLY` | Judges everything, acts on nothing — not even on admin command. Verdicts go to the admin chat and violations are recorded so accuracy can be measured before the bot is given real power. **Start here.** |
| `ON_DEMAND_ACTION` | Above the confidence threshold, proposes a penalty with a ready `/execute` command and waits for an admin. |
| `LIVE_ACTION` | Above the confidence threshold, acts on its own. Still stops at the top of the escalation ladder. |

A ban is never an automatic action in any mode.

Spend real time in `LOG_ONLY`. Read the verdicts, and use `/train` on the ones it got
wrong in either direction — that is what the mode is for. `/stats` shows how many
decisions it has taken and per which rule.

When you move up, `/mode ON_DEMAND_ACTION` takes effect immediately and persists across
restarts; `application.yml` is only the starting point.

## Admin commands

Accepted only in the admin chat, and only from an administrator **of the moderated
group** — membership of the admin chat grants nothing. Authority is re-checked against
Telegram on every command, never read from config.

| Command | Purpose |
|---|---|
| `/execute <decisionId>` | Apply the proposed penalty as-is |
| `/execute <decisionId> duration=<Nm\|Nh\|Nd>` | Apply it with a different duration, recorded as a modification |
| `/execute <decisionId> dismiss` | Discard the decision, no action |
| `/train <rule_id> <positive\|negative> <link>` | Add a labelled example to a rule. Never retroactive. |
| `/rulebook update [link\|text]` | Compile the rulebook into rules. Reply to the message or file holding it, or pass a message link. Nothing activates yet. |
| `/rulebook approve\|reject <proposalId>` | Activate or discard a compiled rulebook |
| `/rulebook pending` | List proposals awaiting review |
| `/rule list\|enable\|disable <rule_id>` | List rules, or turn one on or off. Disabling keeps the rule and its examples. |
| `/dynamic <@a\|id> <@b\|id> <description>` | Record a known dynamic between two members. The nightly job never overwrites it. |
| `/mode [LOG_ONLY\|ON_DEMAND_ACTION\|LIVE_ACTION]` | Show or change the operating mode |
| `/threshold [0.0-1.0]` | Show or change the confidence threshold |
| `/stats [today\|week\|month\|all]` | Volume, actions, LLM spend and health. On demand only — nothing is ever posted on a schedule. |

Changes to the mode, the threshold and any rule are announced in the admin chat with who
made them.

## Profiling

A nightly job (`savonarola.profile.cron`, 04:00 by default) summarises how each active
member typically writes, and how often one member is hostile toward another. Those
summaries are what let the judge tell established banter from someone being targeted.

Profiles are cold on a fresh install: until the first run, judgments work from the recent
conversation window alone and the extended-history lookup never fires. `/dynamic` is the
way to seed what the bot has no history to infer yet — a long-standing joking rivalry, for
instance, that would otherwise read as hostility.

Each user and each pair costs one call on the cheap model, against the same free-tier
budget as live judging, so the run is capped (`max-users-per-run`, `max-pairs-per-run`)
and works busiest-first.

---

## Versioning and releases

[Semantic versioning](https://semver.org). The git tag is the single source of truth for
what a build is called; the pom stays on a `-SNAPSHOT` and CI stamps the version from the
tag.

```bash
git tag v0.2.0 && git push origin v0.2.0
```

That builds, runs the full suite, and — only if it passes — publishes
`ghcr.io/stormino/savonarola` tagged `0.2.0`, `0.2`, and `latest`, plus a GitHub release
with the jar attached. A tag containing a hyphen (`v0.3.0-rc1`) is treated as a
prerelease and does not move `latest`.

Pin the tag in production rather than tracking `latest`:

```yaml
image: ghcr.io/stormino/savonarola:0.2.0
```

The running instance logs its version at startup.

## Development

```bash
mvn test                                          # full suite
mvn -Dtest=ExecuteCommandTest test                # one class
mvn -Dtest=EscalationServiceTest#climbsOneRungPerPriorExecution test
```

CI runs the suite and an image build on every branch. There is no `@SpringBootTest`:
starting the context registers a long-polling bot that would try to reach Telegram, so
tests are plain JUnit 5 and Mockito.

[CLAUDE.md](CLAUDE.md) documents the architecture and the invariants worth not breaking.

## Not implemented yet

Nothing from SPEC is outstanding. Two things are deliberately out of scope: edited
messages are never re-judged, and photo captions are not processed. Section 17 still lists
open thresholds, which are tuning decisions for after the first `LOG_ONLY` run.
