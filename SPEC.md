# SAV Moderation Bot — Documento di Specifica

**Stato**: bozza di design, pre-implementazione
**Stack previsto**: Java / Spring Boot, OpenRouter, Telegram Bot API

---

## 1. Visione e filosofia

Il gruppo SAV è cresciuto al punto da rendere necessaria una moderazione più strutturata, basata su un regolamento scritto e condiviso invece che sul giudizio caso per caso. Il bot nasce per alleggerire il carico degli admin, non per sostituirli.

**Analogia guida**: la chat principale SAV è il mondo reale, dove le persone interagiscono e dove possono avvenire infrazioni. La chat admin ("Admin schiaffers") è il tribunale: lo spazio dove si delibera. Gli admin umani sono contemporaneamente giudici e polizia giudiziaria — decidono e fanno anche eseguire. Il bot è un **giudice AI**: istruisce le decisioni, propone pene motivate, e — solo quando la fiducia nel suo giudizio lo giustifica — può anche eseguire da solo, alleggerendo giudici umani oberati di lavoro. Nel mondo reale (la chat principale) arriva **solo l'esecuzione della pena**, mai il processo deliberativo: nessun comando, nessun log, nessuna traccia del bot "al lavoro" — solo l'annuncio quando una condanna viene eseguita.

Principio cardine dal regolamento: il confronto acceso e il dissenso tecnico sono esplicitamente benvenuti. Il bot deve distinguere tra critica dura legittima e comportamento lesivo — mai penalizzare la vivacità della discussione in sé.

---

## 2. Il regolamento come fonte di verità

Il regolamento esiste oggi come testo in linguaggio naturale (in italiano). Diventa la fonte di verità per un **RuleSet strutturato**, che il bot usa per giudicare.

### 2.1 Aggiornamento del regolamento

- Comando admin diretto nel bot (testo o file allegato) — nessuna dipendenza da repository esterni per l'MVP.
- La conversione testo → RuleSet è **LLM-assisted**: un modello scompone il testo in regole discrete (id, descrizione, severità, esempi).
- **Nessuna attivazione automatica**: il bot posta il RuleSet proposto sul canale admin; un admin deve confermare prima che diventi il RuleSet attivo. Un errore di parsing qui si propagherebbe a tutte le decisioni successive, quindi la review umana è obbligatoria.

### 2.2 RuleSet — struttura dati

```yaml
id: <english_snake_case>
severity: LOW | MEDIUM | HIGH
requiresHistory: true | false   # true se il giudizio richiede il message store esteso
definition: "<inglese, definizione precisa del confine>"
examples:
  - text: "<italiano>"
    label: positive | negative     # positive = viola la regola, negative = non viola
    addedBy: <admin_telegram_id>
    addedAt: <timestamp>
    sourceMessageId: <opzionale>
enabled: true | false
```

### 2.3 Regole correnti (formalizzate dal regolamento SAV)

| id | severity | requiresHistory | note |
|---|---|---|---|
| `direct_insult` | HIGH | no | confine relativamente netto, poca ambiguità |
| `passive_aggressive_pattern` | MEDIUM | sì | richiede profilo mittente/destinatario per distinguere stile abituale da presa di mira |
| `intimidation_pattern` | HIGH | sì | per definizione non giudicabile da un singolo messaggio — richiede pattern nel tempo verso la stessa persona |
| `mockery_of_opinions` | MEDIUM | no | tenuta distinta da `passive_aggressive_pattern` su richiesta esplicita, nonostante la sovrapposizione concettuale |
| `troll_hit_and_run` | HIGH | sì | firma comportamentale: bassa partecipazione storica + intervento aggressivo mirato su un episodio |

`no_politics` esclusa per ora (non applicabile in pratica).

Definizioni ed esempi dettagliati per ciascuna regola: vedi sezione 2.2 per il formato; il contenuto pieno verrà popolato/arricchito progressivamente via training (sezione 8).

---

## 3. Modalità operative

```yaml
operatingMode: LOG_ONLY | ON_DEMAND_ACTION | LIVE_ACTION
```

| Modalità | Comportamento |
|---|---|
| `LOG_ONLY` | Dry run. Ogni decisione (violata o no) loggata su Admin schiaffers. Nessuna azione possibile, nemmeno manuale: `/execute` viene rifiutato. Le violazioni vengono comunque persistite con status `logged` — senza una traccia, la modalità che esiste per misurare l'accuratezza non lascerebbe nulla da misurare. Serve a validare l'accuratezza prima di dare potere reale al bot. |
| `ON_DEMAND_ACTION` | Sopra soglia di confidenza, il bot prepara la decisione (regola, motivazione, azione suggerita) e la posta su Admin schiaffers con un comando pronto (`/execute`). Un admin conferma, modifica o scarta. |
| `LIVE_ACTION` | Stesso giudizio, ma l'azione parte autonomamente sopra soglia, senza attesa. |

Sotto soglia di confidenza, in tutte le modalità (tranne `LOG_ONLY`, dove tutto è comunque solo loggato): flag informativo su Admin schiaffers, senza azione suggerita pronta — il bot non propone una condanna quando non è abbastanza sicuro.

---

## 4. Pipeline di giudizio

### 4.1 Flusso per messaggio

```
Messaggio in arrivo
  → salvato nel message store (sezione 6)
  → sender è admin (via getChatAdministrators)? SÌ → nessun giudizio, pipeline termina qui
  → check leggero: negativeInteractionCount(sender, target) sopra soglia (media/bassa)?
      SÌ → recupero storico esteso sender→target dal message store
      NO → solo context window immediato (ultimi 15 messaggi)
  → chiamata LLM di giudizio (sezione 4.2), su TUTTE le regole attive in una sola chiamata
  → output: decision object (sezione 5)
  → routing in base a operatingMode e confidence (sezione 3)
```

### 4.2 Chiamata di giudizio — input assemblati

```yaml
judgmentInput:
  ruleSet: [regole attive, con definition + examples]
  targetMessage: { senderId, text, timestamp, replyToMessageId }
  contextWindow: [ultimi 15 messaggi della chat, con sender e timestamp]
  senderProfile: { typicalTone, knownDynamics: [...] }
  targetProfile: <se identificabile un destinatario specifico>
  extendedHistory: <solo se negativeInteractionCount supera soglia>
```

### 4.3 System prompt — bozza concettuale (inglese)

```
You are a moderation assistant for an Italian tennis-fan Telegram group.
You will be given the group's rulebook, a recent conversation window, and
the message to judge. Messages are in Italian; respond in the language
of your output fields only (structured, see below).

RULES: interpret literally — a message violates a rule only if it clearly
matches its definition. Do not infer intent beyond what the text and
context support. Passionate, harsh, or blunt disagreement about tennis
opinions is explicitly ALLOWED and must never be flagged on its own.

CONTEXT: use the sender's typical tone profile and any known dynamic with
the recipient to interpret ambiguous tone. Established banter/rivalry
patterns are NOT violations even if the words alone would look harsh out
of context. However, a clear violation is a violation regardless of the
sender's usual style — the profile disambiguates tone, it does not excuse
crossing a line.

OUTPUT: respond only with the structured decision object below. If
uncertain, prefer lower confidence over a forced binary call.
```

### 4.4 Principi di design della chiamata

- **Una chiamata per messaggio, non una per regola**: più economico e più coerente (il modello vede il quadro intero).
- **`reasoning` sempre presente**, anche quando `violated: false` — finisce nel log e nel messaggio `/execute`, deve essere comprensibile a un admin che decide.

---

## 5. Decision object

```yaml
decision:
  id: <uuid>
  ruleId: <string>
  confidence: <float 0-1>
  reasoning: "<string>"
  suggestedAction: { type: mute, durationMinutes: <int>, rung: <int> }
  actualAction: { type: mute, durationMinutes: <int> }   # presente solo se diverso dal suggerito
  status: logged | pending | executed | dismissed
  resolvedBy: <admin_telegram_id>   # presente solo per pending → executed/dismissed
```

`suggestedAction` è calcolata in base alla posizione dell'utente sulla escalation ladder (sezione 7) al momento del giudizio.

`logged` è lo status delle decisioni registrate in `LOG_ONLY`: non sono mai azionabili e non contano mai ai fini della posizione sulla ladder (sezione 9), che è derivata dalle sole decisioni `executed`.

---

## 6. Message store

Componente trasversale, condiviso da tre consumatori:

```yaml
messageStore:
  retentionDays: <da definire, indicativamente 60-90>
  indexedBy: [chatId, messageId]
  fields: [senderId, text, timestamp, replyToMessageId]
  consumers:
    - patternDetection    # query estesa sender→target per intimidation_pattern e troll_hit_and_run
    - trainCommand         # risoluzione di message link per /train
    - contextWindow        # ultimi N messaggi per il giudizio live
```

Necessario perché l'API bot di Telegram non offre un modo di recuperare un messaggio arbitrario dato solo il suo id — il bot deve aver già visto passare il messaggio e tenerlo in uno store proprio.

---

## 7. Profili utente e coppia

### 7.1 Generazione — ibrida

- **Batch periodico** (es. notturno): per ogni utente attivo, sintesi leggera del tono tipico; per ogni coppia che interagisce spesso, contatore di interazioni negative negli ultimi 30 giorni + eventuale nota sintetica. Ogni utente e ogni coppia costano una chiamata LLM sullo stesso budget del giudizio live (sezione 14), quindi il run è limitato per numero di utenti e di coppie e procede dai più attivi: profili parziali sono accettabili, un giudice a secco no.
- **Context live**: la finestra degli ultimi 15 messaggi, sempre inclusa nella chiamata di giudizio, cattura variazioni recenti non ancora riflesse nel profilo batch.

### 7.2 Struttura

```yaml
userProfile:
  userId: <string>
  typicalTone: "<sintesi breve>"
  knownDynamics:
    - withUser: <string>
      pattern: "<sintesi breve>"
      source: admin_annotated | bot_inferred
  lastUpdated: <timestamp>

pairSignal:
  senderId: <string>
  targetId: <string>
  negativeInteractionCount: <int>   # finestra: ultimi 30 giorni
  note: "<opzionale>"
```

Il `pairSignal` è **direzionale**: il trigger della sezione 7.4 è `negativeInteractionCount(sender, target)`, e chi prende di mira chi è tutto il segnale. Un signal che il batch non ha aggiornato entro la finestra di rilevazione viene ignorato, così una coppia non resta segnalata dopo aver smesso di interagire.

### 7.3 Annotazione admin

Gli admin possono correggere o seedare manualmente `knownDynamics` con `/dynamic` (es. "X e Y hanno una rivalità scherzosa di lunga data") — utile soprattutto in fase di avvio, prima che il bot abbia abbastanza storico per inferirlo da solo.

Un'annotazione admin non viene mai sovrascritta dalle inferenze del batch: la sezione esiste proprio perché all'inizio il bot sbaglia queste letture.

### 7.4 Query estesa — trigger

```
negativeInteractionCount(sender, target) sopra soglia (media/bassa, per iniziare — 
  accettabile perché in LOG_ONLY il costo di un falso positivo è solo una segnalazione letta invano)
  → query estesa storico sender→target + giudizio LLM approfondito sul pattern
```

---

## 8. Training incrementale

```
/train <rule_id> <positive|negative> <message_link>
```

- Usato **solo** nella chat Admin schiaffers.
- Il bot estrae `chatId` + `messageId` dal link, cerca il messaggio nel message store locale.
- Se trovato: aggiunge `{text, label, addedBy, addedAt, sourceMessageId}` agli `examples` della regola indicata.
- Se non trovato (fuori retention): il bot segnala che non può recuperarlo.
- **Nessuna azione retroattiva**: marcare un messaggio come esempio positivo non genera mai un'azione di moderazione su quel messaggio. Il training arricchisce il RuleSet per il futuro, punto.
- Per l'MVP gli esempi vivono direttamente dentro il RuleSet (non in un dataset separato) — migrabile in futuro se il volume cresce molto.

---

## 9. Escalation ladder

```yaml
escalation:
  scope: <da decidere — per ora non definito: globale per utente vs per regola>
  ladder: [5m, 30m, 2h, 24h, admin_review]
  decayAfterDays: 30   # una striscia pulita di N giorni resetta la posizione sulla ladder
```

- `admin_review` è il tetto della ladder: non è un'azione automatica, è il punto in cui il bot smette di agire da solo e passa la decisione a un admin.
- **Il ban non è mai un'azione automatica del bot** in nessuna modalità — resta sempre una decisione umana, coerente col regolamento ("il ban è l'ultima risorsa").

---

## 10. Comando `/execute`

Disponibile sulla chat Admin schiaffers in `ON_DEMAND_ACTION` e in `LIVE_ACTION`. **Rifiutato in `LOG_ONLY`**, dove nessuna azione è possibile nemmeno manualmente (sezione 3).

Serve anche in `LIVE_ACTION` perché le decisioni che raggiungono il tetto della ladder (`admin_review`, sezione 9) restano `pending` anche in quella modalità: senza `/execute` sarebbero irraggiungibili per sempre.

```
/execute <decisionId>                        → esegue l'azione suggerita così com'è
/execute <decisionId> duration=<Nm|Nh|Nd>    → esegue con durata modificata
/execute <decisionId> dismiss                → scarta, nessuna azione
```

Al tetto della ladder il bot non propone una durata, per definizione: lì `duration=` è obbligatorio e il comando senza durata viene rifiutato. La durata massima accettata è 30 giorni, coerente con "il ban non è mai un'azione automatica del bot" (sezione 9).

La durata modificata viene registrata come `actualAction` nel decision object (sezione 5) — segnale utile in futuro per capire quanto spesso gli admin correggono le proposte del bot, e quindi quanto la ladder configurata è tarata bene.

---

## 11. Azione in chat principale (discrezione)

Nella chat principale il bot **non lascia mai traccia del processo deliberativo** — nessun comando, nessun log, nessuna segnalazione. L'unico output visibile è l'annuncio di un'azione effettivamente eseguita (in `ON_DEMAND_ACTION` dopo conferma, o in `LIVE_ACTION` in autonomia).

```yaml
actionAnnouncement:
  enabled: true
  postIn: main_group
  template: "Utente {user} mutato per {duration}."
  replyToOffendingMessage: true
```

`{user}`: da decidere se menzione diretta (@username) o solo nome senza tag — non ancora deciso, default proposto: nome senza tag (meno invasivo), reversibile in config.

---

## 12. Comandi admin — riepilogo

Tutti disponibili solo sulla chat Admin schiaffers, permessi verificati dinamicamente contro la lista amministratori reale del gruppo SAV (`getChatAdministrators`) — nessuna lista admin hardcoded in config.

| Comando | Scopo |
|---|---|
| `/regolamento aggiorna` (testo/file) | Trigger compilazione LLM-assisted del RuleSet, con review/approve |
| `/train <rule_id> <positive\|negative> <link>` | Aggiunge un esempio a una regola |
| `/dynamic <@a\|id> <@b\|id> <descrizione>` | Seeda o corregge una dinamica nota tra due utenti (sezione 7.3) |
| `/execute <decisionId> [duration=...\|dismiss]` | Esegue/modifica/scarta una decisione in `ON_DEMAND_ACTION` |
| `/stats [today\|week\|month\|all]` | Statistiche on-demand (sezione 13) |
| *(da definire)* | Toggle enabled/disabled per singola regola, cambio `operatingMode`, cambio soglia di confidenza |

Nessuna approvazione a maggioranza richiesta per l'MVP: un singolo admin che conferma è sufficiente per qualunque azione, incluse le approvazioni del RuleSet.

---

## 13. `/stats` — dettaglio

Puramente on-demand, nessun riepilogo automatico periodico.

```yaml
statsCommand:
  scope: admin_only
  periods: [today, week, month, all]
  sections:
    volume:
      - messaggi processati
      - decisioni prese, per regola
      - % messaggi che hanno triggerato la query estesa
    actions:
      - mute eseguiti, per gradino della ladder
      - decisioni ON_DEMAND_ACTION: confermate / scartate / modificate
    llmUsage:
      - token consumati, per modello e per tipo di chiamata (giudizio / profilo / compilazione regolamento)
    health:
      - errori consecutivi, fallback attivati, downtime
```

---

## 14. Infrastruttura LLM — OpenRouter

- Client LLM astratto dietro un'interfaccia unica (es. `LlmJudge`), non legato a un provider/modello specifico in codice — necessario perché il catalogo dei modelli gratuiti OpenRouter cambia senza preavviso.
- **Modello primario** (giudizio): un modello con buon ragionamento generale e supporto multilingue solido (candidati validi al momento della scrittura: Thinking Machines Inkling, NVIDIA Nemotron 3 Super — da rivalutare al momento dell'implementazione, il catalogo cambia rapidamente).
- **Modello secondario** (fallback): stesso criterio, usato in caso di rate limit/indisponibilità del primario.
- **Modello leggero** (sintesi profilo periodico): compito meno critico, va bene un modello più economico/piccolo.
- **Vincolo noto**: i modelli gratuiti hanno tipicamente rate limit nell'ordine di ~20 richieste/minuto e ~200/giorno *per modello* — rilevante per il dimensionamento (giudizio + query estesa + profilo periodico, sommati, su un gruppo attivo).

---

## 15. Health monitoring

```yaml
health:
  channel: admin_schiaffers
  tag: "⚠️ SYSTEM"
  consecutiveFailureThreshold: 3
```

Segnali monitorati: connettività/latenza OpenRouter, errori consecutivi sulla chiamata di giudizio, eventuale segnale di quota esaurita, heartbeat del processo bot. Notifiche taggate distintamente dai log di moderazione, sullo stesso canale Admin schiaffers.

---

## 16. Vincoli trasversali

- **Lingua**: codice, comandi, chiavi di configurazione, id delle regole → inglese. Testo del regolamento, esempi di messaggi, contenuto reale processato → italiano.
- **Multi-admin**: permessi sempre verificati dinamicamente contro `getChatAdministrators`, non contro una lista configurata a mano.
- **Discrezione**: la chat principale non deve mai rivelare che un sistema di moderazione automatizzato sta osservando, salvo il momento dell'esecuzione di una pena.
- **Admin non sanzionabili**: i messaggi inviati da un admin (verificato dinamicamente via `getChatAdministrators`) sono esclusi a monte dalla pipeline di giudizio — nessuna valutazione, nessuna decisione, nessuna azione possibile su di loro, in nessuna modalità operativa. Il check avviene prima di qualunque chiamata LLM (sezione 4.1).

---

## 17. Aperto / da definire

- Schema dati completo e relazioni tra message store, profili, decision log (solo abbozzato qui, sezioni 5-7).
- Logica esatta della compilazione LLM-assisted del regolamento: comportamento in caso di output ambiguo o parsing incerto.
- Scope della escalation ladder: globale per utente vs per regola (esplicitamente rimandato).
- `{user}` in `actionAnnouncement`: menzione o solo nome.
- Soglia esatta di `negativeInteractionCount` per il trigger della query estesa.
- Soglia esatta di `confidenceThreshold`.
- Comandi per toggle regole/modalità/soglie (elencati come necessari, non ancora specificati nel dettaglio).
- Scaffolding tecnico (Spring Boot, dipendenze, struttura progetto) — volutamente rimandato a valle di questo documento.
