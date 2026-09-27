<div align="center">

<img src="docs/logo.svg" alt="InvRestore Logo" width="96"/>

# InvRestore

**Death-Backups mit Provenance-Tracking für Minecraft 26.1.2 — Items wiederfinden statt neu erzeugen.**

[![CI](https://github.com/fakten60-svg/Invrestore/actions/workflows/release.yml/badge.svg)](https://github.com/fakten60-svg/Invrestore/actions/workflows/release.yml)
[![Release](https://github.com/fakten60-svg/Invrestore/actions/workflows/release-publish.yml/badge.svg)](https://github.com/fakten60-svg/Invrestore/actions/workflows/release-publish.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-34d399)
![Java](https://img.shields.io/badge/Java-25-f59e0b)
![Fabric](https://img.shields.io/badge/Fabric-Loader%200.19.5-blue)
![License](https://img.shields.io/badge/License-MIT-green)

</div>

---

**InvRestore** ist eine rein **serverseitige** Fabric-Mod: Bei jedem echten Spielertod
wird unmittelbar vor dem Vanilla-Drop ein vollständiger Inventar-Snapshot erzeugt
und jedes fallende Item mit einer eindeutigen **Death-ID** markiert (Provenance).
Später führt `/invre` genau diese Items zurück — egal ob sie auf dem Boden liegen,
ein anderer Spieler sie aufgehoben hat oder sie in einer Truhe landeten.

> Kernprinzip: **Restore erzeugt niemals Items.** Er verschiebt nur Stacks, die
> physisch existieren und exakt die passende Death-ID tragen. Dadurch ist
> Duplikation konstruktiv ausgeschlossen und neue Items des Spielers bleiben
> immer unangetastet.

---

## Features

| Feature | Beschreibung |
|---|---|
| 📸 Death-Snapshot | Volles Inventar (Hauptinventar, Hotbar, Rüstung, Offhand) mit allen Data Components |
| 🏷️ Provenance | Jede Death-ID in `minecraft:custom_data`, serverseitig persistent, client-sicher |
| 🎒 Echtes Finden | Death-Items werden dort zurückgeholt, wo sie wirklich sind — nicht aus dem Snapshot neu gespawnt |
| 🚫 No-Duplication | Nur physikalisch vorhandene, eindeutig zugeordnete Stacks werden verschoben |
| 🤝 Multi-Player | Aufgenommene Items werden bei Alex/Bob/Charlie erkannt und abgezogen |
| 📦 Container-Tracking | Truhen & Co. in geladenen Chunks via Chunk-Load/-Unload-Events |
| ✂️ Split & Merge | Geteilte Stacks behalten die Death-ID; Merging kann Herkunft nicht verwischen |
| 🔁 Statusmaschine | `AVAILABLE` → `RESTORING` → `RESTORED` / `PARTIAL` / `FAILED`, Doppel-Restore blockiert |
| 💾 Persistence | Vanilla `SavedData`, überlebt Neustarts; Crash-Recovery inklusive |
| 🔐 Permissions | `invrestore.self` / `invrestore.admin` / `invrestore.admin.others` (Fabric Permission API) |

---

<div align="center">
<img src="docs/banner.svg" alt="InvRestore Banner" width="480"/>
</div>

## Installation

1. Fabric Loader **≥ 0.19.5** für Minecraft **26.1.2** installieren
2. [Fabric API](https://modrinth.com/mod/fabric-api) **0.155.3+26.1.2** in den `mods`-Ordner
3. `invrestore-1.0.0.jar` (siehe [Release](../../releases)) in den `mods`-Ordner
4. Server starten — `config/invrestore.json` wird beim ersten Start angelegt

Auf dem Client ist **nichts** zu installieren.

## Voraussetzungen

| Komponente | Version |
|---|---|
| Minecraft | **26.1.2** |
| Java | **25** |
| Fabric Loader | **≥ 0.19.5** |
| Fabric API | **0.155.3+26.1.2** |

> Ab Minecraft 26.1 ist das Spiel unobfuscated — das Projekt nutzt das neue
> Loom-Plugin `net.fabricmc.fabric-loom` mit Mojang-Mappings ohne Remapping.

## Commands

Der einzige Command ist **`/invre`** (Inventory Restore).

| Befehl | Wirkung | Berechtigung |
|---|---|---|
| `/invre` | Eigene Backups auflisten | `invrestore.self` |
| `/invre latest` | Neuestes wiederherstellbares Backup restoren | `invrestore.self` |
| `/invre <index>` | Backup per Nummer restoren | `invrestore.self` |
| `/invre <death-id>` | Backup per UUID(-Präfix) restoren | `invrestore.self` |
| `/invre <player>` | Backups eines anderen Spielers auflisten | `invrestore.admin` |
| `/invre <player> latest` | Neustes fremdes Backup restoren | `invrestore.admin.others` |
| `/invre <player> <index\|id>` | Bestimmtes fremdes Backup restoren | `invrestore.admin.others` |

- Tab-Completion: `latest`, eigene Indizes; Spielernamen **nur** für Admins
- Klickbarer `[RESTORE]`-Button in der Backup-Liste (nur `/invre`-Commands)
- Kein Command erzeugt Backups — Backups entstehen ausschließlich bei echten Toden

## Permissions

| Node | Standard |
|---|---|
| `invrestore.self` | erlaubt (jeder für sich selbst) |
| `invrestore.admin` | OP-Level 2 (`COMMANDS_GAMEMASTER`) |
| `invrestore.admin.others` | OP-Level 2 (`COMMANDS_GAMEMASTER`) |

Per Fabric Permission API von Mods wie LuckPerms überschreibbar; alle Prüfungen
laufen serverseitig.

## Konfiguration

`config/invrestore.json`:

```json
{
  "maxBackupsPerPlayer": 20,
  "enableAdminRestore": true,
  "enableContainerTracking": true,
  "debugLogging": false
}
```

| Option | Bedeutung |
|---|---|
| `maxBackupsPerPlayer` | Backup-Limit pro Spieler; Pruning entfernt zuerst RESTORED, dann FAILED — AVAILABLE (wiederherstellbare Items) bleibt erhalten |
| `enableAdminRestore` | Admin-Zugriff auf fremde Backups |
| `enableContainerTracking` | Container geladener Chunks beim Restore durchsuchen |
| `debugLogging` | Ausführliche Diagnose-Logs |

## How it works

### 1. Tod → Snapshot + Markierung

Ein Mixin an **HEAD von `ServerPlayer#die`** sichert vor dem Vanilla-Drop den
kompletten Inventar-Container. Danach werden die Live-Stacks mit der Death-ID
markiert — erst **nach** dem Kopieren, damit die Snapshot-Einträge die Markierung
nicht selbst tragen. Vanilla droppt die Items wie gewohnt, jetzt mit Provenance.

Der Snapshot deckt exakt ab, was Vanilla auch behandelt (gegen den echten
26.1.2-Bytecode verifiziert, `Player#dropEquipment`):

- **`keepInventory` aktiv** → Vanilla droppt nichts → **kein Backup, keine Markierung**
- **Fluch der Vergänglichkeit** (`prevent_equipment_drop`) → von Vanilla zerstört →
  im Snapshot, aber nie markiert
- sonst → Hauptinventar, Hotbar, Rüstung, Offhand (= `Inventory#dropAll`)

### 2. Provenance / Death-ID

```
custom_data: { invrestore: { v: 1, death: "<uuid>" } }
```

Eine Server-Mod kann keine eigene Data Component registrieren (Vanilla-Clients
könnten das Sync-Paket nicht dekodieren) — deshalb der persistente Vanilla-
`custom_data`-Component. Weil die Markierung Teil der Stack-Components ist,
stacken markierte Items nicht mit normalen Items desselben Typs: gewollt und
Grundlage des Duplikationsschutzes. Splits erben die Death-ID; Merging bleibt
korrekt.

### 3. Restore-System

1. Status prüfen — Start nur aus `AVAILABLE` / `FAILED` / `PARTIAL`
2. Restore-Lock pro Death-ID (kein paralleler Doppel-Restore)
3. Inhaber muss online sein (sonst Abbruch, nichts wird angefasst)
4. Suche: Inventare + Endertruhen aller Online-Spieler, alle Item-Entities,
   Container geladener Chunks
5. Gefundene Stacks aus ihren Quellen entfernen, dann übergeben
6. Volles Inventar? Rest wird beim Spieler gedroppt — nie gelöscht
7. Status persistieren

### 4. No-Duplication

> Ein Restore verschiebt nur Stacks, die existieren und exakt die Death-ID tragen.

- Fremde/neue Items: andere oder keine Markierung → nie angefasst
- Verbrauchte Items: existieren nicht mehr → werden nie neu erzeugt
- Dasselbe physische Item: wird entfernt, bevor es übergeben wird → nie doppelt
- `RESTORED` blockiert ein zweites Restore; `PARTIAL` erlaubt sicheres Nachfassen

### 5. Neue Items nach dem Tod

Steve stirbt mit 32 Diamanten und farmt danach 10 neue. Beim Restore bekommt er
die 32 **zusätzlich** — die 10 neuen bleiben unberührt. Die Mod rechnet nie
„Bestand − Snapshot", sondern folgt ausschließlich der Provenance.

### 6. Andere Spieler

Alex hebt 12, Bob 20 von Steves 32 auf. Restore: Steve +32, Alex −12, Bob −20.
Die Items werden genau dort entfernt, wo sie gerade sind — Alex' eigene Items
bleiben komplett unangetastet.

### 7. Container-Tracking

Chunk-Load/-Unload-Events halten einen Index geladener Chunks; der Restore
durchsucht nur deren Block-Entity-Container. Kein Welt-Scan, kein Tick-Overhead.

### 8. PARTIAL-Status

Sind nicht alle Items auffindbar (entladene Chunks, verbraucht,zerstört),
erhält der Backup `PARTIAL`: die gefundenen Items werden zurückgegeben,
der Rest gilt als verloren — es wird nichts künstlich nachgespawnt. Ein
späterer zweiter Versuch kann neu geladene Chunks noch erfassen.

### 9. Persistence & Crash-Recovery

Backups liegen in Vanilla `SavedData` (`data/invrestore_backups.dat`) und
überleben Neustarts. Wurde ein Restore durch einen Crash unterbrochen
(`RESTORING`), wird beim nächsten Start automatisch `FAILED` gesetzt —
ein sauberer Retry ist möglich, ohne Duplikation.

## Bekannte Einschränkungen

- **Nur geladene Chunks** — Items in entladenen Chunks werden erst bei späteren
  Restore-Versuchen gefunden (`PARTIAL` bleibt retry-bar)
- **keepInventory** — kein Backup (Vanilla droppt nichts; beabsichtigt)
- **Verbrauchte/transformierte Items** — Crafting, Schmelzen u. a. erzeugen neue
  Stacks ohne Provenance; das Original gilt konservativ als verbraucht
- **Kein gemischtes Stacken** — markierte Items stapeln nicht mit unmarkierten
  desselben Typs (Sicherheit schlägt Bequemlichkeit)
- **Restore-Ziel muss online sein** — offline wird abgelehnt, damit nichts verloren geht

## Build

```bash
./gradlew clean build    # Linux/macOS
gradlew.bat clean build  # Windows
```

| Artefakt | Ort |
|---|---|
| Mod-JAR | `build/libs/invrestore-1.0.0.jar` |
| Sources-JAR | `build/libs/invrestore-1.0.0-sources.jar` |
| Source-ZIP (Projekt) | `build/distributions/invrestore-1.0.0-project-sources.zip` |

Nur die Mod-JAR (ohne `-sources`) gehört in den `mods`-Ordner.

## Entwicklung

```bash
./gradlew test   # 24 Unit-Tests
```

| Test-Suite | Abdeckung |
|---|---|
| `SelectorTest` | `latest`/Index/UUID-Präfix-Auflösung, Edge Cases |
| `BackupStatusTest` | erlaubte Übergänge, lenienter Codec |
| `InvRestoreSavedDataTest` | Sortierung, Scoping, Pruning, Crash-Recovery |
| `SnapshotPolicyTest` | Snapshot = Vanilla-Drop-Verhalten, Reihenfolge Kopieren→Markieren |

Die Kernlogik (Selector, Statusmaschine, Persistenz, Snapshot-Regeln) ist bewusst
weltfrei gehalten und direkt testbar. Szenarien, die eine echte Welt brauchen
(Pickup durch andere Spieler, Chests, Neustart), sind als manuelle Matrix in
[docs/TESTING.md](docs/TESTING.md) dokumentiert.

## Release

Releases werden **durch Git-Tags** ausgelöst:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Der Workflow `release-publish.yml` baut dann auf Java 25, führt die Tests aus,
prüft die Artefakte und erstellt einen **echten GitHub Release** mit:
`invrestore-<version>.jar`, `invrestore-<version>-sources.jar` und dem
Source-ZIP. Die Version kommt aus `gradle.properties` — nichts wird erfunden.
Zusätzlich läuft `release.yml` bei jedem Push/PR als CI mit Build + Tests +
Artefakt-Upload.

## Lizenz

[MIT](LICENSE)
