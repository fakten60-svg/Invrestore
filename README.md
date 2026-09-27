# Inventory Restore (`invrestore`)

Eine **serverseitige** Fabric-Mod für **Minecraft Java Edition 26.1.2**. Sie erstellt
bei jedem Spielertod einen vollständigen Inventar-Snapshot, versieht die beim Tod
fallenden Items mit einer Herkunftsmarkierung (Provenance) und kann diese Items
später aus der ganzen Spielwelt zurückholen – **ohne Item-Duplikation** und ohne
fremde Items anzutasten.

- **Kein Client-Mod nötig.** Die Mod läuft ausschließlich auf dem Server
  (`"environment": "server"`).
- Normale Vanilla-Drops bleiben unverändert: Andere Spieler dürfen Death-Items
  aufheben, handeln, einlagern usw.
- Der Restore führt **vorhandene** Items zurück – er erzeugt niemals neue Items.

---

## Voraussetzungen

| Komponente          | Version                                   |
|---------------------|-------------------------------------------|
| Minecraft           | **26.1.2**                                |
| Java (Server)       | **25** oder neuer                          |
| Fabric Loader       | **0.19.5** oder neuer                      |
| Fabric API          | **0.155.3+26.1.2** (oder neuer für 26.1.2) |

> Hinweis: Ab Minecraft 26.1 ist das Spiel **nicht mehr obfuskiert**. Es werden
> die offiziellen Mojang-Namen verwendet; Yarn wird nicht mehr unterstützt.
> Deshalb nutzt das Projekt das neue Loom-Plugin `net.fabricmc.fabric-loom` und
> `implementation`/`compileOnly` statt `modImplementation`/`modCompileOnly`.

---

## Installation (Server)

1. Fabric Loader 0.19.5+ für Minecraft 26.1.2 installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) für 26.1.2 in den `mods`-Ordner legen.
3. Die gebaute `invrestore-1.0.0.jar` in den `mods`-Ordner legen.
4. Server starten. Beim ersten Start wird `config/invrestore.json` angelegt.

Auf dem Client ist **nichts** zu installieren – ein normaler Vanilla-/Fabric-Client
funktioniert.

---

## Build

Voraussetzung: **JDK 25** (`JAVA_HOME` gesetzt).

```bash
# Linux/macOS
./gradlew clean build

# Windows
gradlew.bat clean build
```

Das Ergebnis liegt unter:

```
build/libs/invrestore-1.0.0.jar     <- diese Datei auf den Server kopieren
build/libs/invrestore-1.0.0-sources.jar
```

Nur die Mod-JAR (`invrestore-1.0.0.jar`, ohne `-sources`) in den `mods`-Ordner legen.

Tests ausführen:

```bash
./gradlew test
```

### Toolchain des Projekts

- Gradle **9.7.1** (Wrapper enthalten), Fabric Loom **1.18.2**
- `tasks.withType(JavaCompile) { options.release = 25 }`
- Minecraft **26.1.2**, Fabric Loader **0.19.5**, Fabric API **0.155.3+26.1.2**

---

## Commands

Der einzige Command der Mod ist **`/invre`** (Inventory Restore).

| Befehl                          | Wirkung                                              | Berechtigung              |
|---------------------------------|------------------------------------------------------|---------------------------|
| `/invre`                        | Eigene Death-Backups auflisten                       | `invrestore.self`         |
| `/invre latest`                 | Neuestes wiederherstellbares Backup wiederherstellen | `invrestore.self`         |
| `/invre <index>`                | Bestimmtes Backup per Nummer wiederherstellen        | `invrestore.self`         |
| `/invre <death-id>`             | Bestimmtes Backup per UUID(-Präfix) wiederherstellen | `invrestore.self`         |
| `/invre <player>`               | Backups eines anderen Spielers auflisten             | `invrestore.admin`        |
| `/invre <player> latest`        | Neuestes Backup eines anderen Spielers wiederherstellen | `invrestore.admin.others` |
| `/invre <player> <index/id>`    | Bestimmtes Backup eines anderen Spielers wiederherstellen | `invrestore.admin.others` |

- Backups entstehen **ausschließlich** durch echte Spielertode. Es gibt bewusst
  **keinen** Command, der neue Backups anlegt.
- In der Auflistung erscheint bei wiederherstellbaren Backups ein anklickbarer
  `[RESTORE]`-Button (führt den jeweiligen `/invre`-Befehl aus).
- **Tab-Completion** ist vollständig: `/invre <TAB>` schlägt `latest`, eigene
  Indizes und – nur bei Admin-Berechtigung – Spielernamen vor; nach einem
  Spielernamen werden die Indizes dieses Spielers vorgeschlagen. Normale Spieler
  sehen **keine** fremden Spielernamen und können fremde Backups so nicht entdecken.
- `/invre` für Spieler ohne Inventar (Konsole) liefert einen Hinweis auf
  `/invre <player>`.

### Ausgabe-Beispiel

```
Death backups for Steve:
  Death #1 [AVAILABLE] 27.09.2026 18:32 minecraft:overworld 123 64 -421 items=47 [RESTORE]
  Death #2 [RESTORED]  26.09.2026 11:04 minecraft:the_nether -20 72 100 items=12
```

---

## Permissions

Es werden moderne Fabric-Permission-Nodes verwendet (`fabric-permission-api-v1`).
Wenn ein Permission-Plugin (z. B. LuckPerms) die Node beantwortet, gilt dessen
Entscheidung; andernfalls greift der Standard-Fallback.

| Node                        | Standard                                      |
|-----------------------------|-----------------------------------------------|
| `invrestore.self`           | **erlaubt** (jeder Spieler für sich selbst)   |
| `invrestore.admin`          | **OP-Level 2** (`COMMANDS_GAMEMASTER`)        |
| `invrestore.admin.others`   | **OP-Level 2** (`COMMANDS_GAMEMASTER`)        |

- Ein normaler Spieler kann **niemals** fremde Backups sehen oder wiederherstellen.
- Alle Berechtigungsprüfungen laufen **serverseitig**.

---

## Konfiguration (`config/invrestore.json`)

```json
{
  "maxBackupsPerPlayer": 20,
  "enableAdminRestore": true,
  "enableContainerTracking": true,
  "debugLogging": false
}
```

| Option                    | Bedeutung                                                                                         |
|---------------------------|---------------------------------------------------------------------------------------------------|
| `maxBackupsPerPlayer`     | Maximale Anzahl Backups pro Spieler. Beim Überschreiten werden zuerst **RESTORED**-Backups gelöscht, danach **FAILED**. **AVAILABLE**-Backups werden nicht gelöscht, weil sie noch wiederherstellbare Items halten. |
| `enableAdminRestore`      | Erlaubt OPs/Admins das Verwalten fremder Backups.                                                  |
| `enableContainerTracking` | Durchsucht bei einem Restore zusätzlich die Container in **geladenen** Chunks.                      |
| `debugLogging`            | Ausführliche Diagnose-Logs.                                                                        |

---

## Funktionsweise

### 1. Tod → Snapshot + Markierung

Ein Mixin springt an den **Anfang** von `ServerPlayer#die` – also **bevor**
Vanilla das Inventar fallen lässt. Zu diesem Zeitpunkt:

1. wird der komplette Inventarinhalt (Hauptinventar, Hotbar, Rüstung, Offhand)
   als Snapshot gespeichert (Item-Typ, Count, **alle** Data Components: Enchantments,
   Custom Name, Lore, Durability, Attribute, Custom Data, …);
2. wird jeder lebende Stack mit der **Death-ID** markiert (erst **nach** dem
   Kopieren, damit die Snapshot-Kopien selbst die Markierung nicht tragen);
3. lässt Vanilla die Items wie gewohnt auf den Boden fallen – jetzt mit
   Herkunftsmarkierung.

**Der Snapshot entspricht exakt dem, was Vanilla auch behandelt.** Die
Vanilla-Death-Pipeline wurde gegen den echten 26.1.2-Bytecode verifiziert
(`Player#dropEquipment`):

- Ist die Gamerule **`keepInventory` aktiv, droppt Vanilla nichts.** Die Mod
  erstellt dann **kein Backup** und markiert keine Items (es gibt nichts
  wiederherzustellen; das Inventar bleibt beim Spieler).
- Stacks mit dem Effekt `minecraft:prevent_equipment_drop`
  (**Fluch der Vergänglichkeit**) werden von Vanilla vor dem Drop zerstört.
  Sie sind im Snapshot enthalten, werden aber **nicht** markiert – Vanilla
  erzeugt für sie keine Drops, also darf es sie auch nicht geben.
- In allen anderen Fällen deckt der Snapshot genau `Inventory#dropAll` ab
  (Hauptinventar, Hotbar, Rüstung, Offhand).

### 2. Herkunft (Provenance)

Die Markierung wird im Vanilla-Component `minecraft:custom_data` unter dem Schlüssel
`invrestore` gespeichert:

```
custom_data: { invrestore: { v: 1, death: "<uuid>" } }
```

Warum `custom_data` und keine eigene Component? Eine reine Server-Mod kann **keine**
neue Data-Component registrieren – Vanilla-Clients kennen sie nicht und das
Inventar-Sync-Paket würde nicht decodieren. `custom_data` ist eine Vanilla-Component,
ist persistent und für den Client unschädlich.

Weil die Markierung Teil der Stack-Components ist, stapeln sich markierte Items
**nicht** mit normalen Items oder mit Items eines anderen Todes zusammen. Genau das
ist die gewünschte, konservative Garantie: Ein markierter Stack ist **eindeutig**
einem einzigen Death-Backup zugeordnet. Splitting (Teilen) erzeugt zwei markierte
Stacks mit derselben Death-ID; Merging zweier gleich markierter Stacks bleibt
ebenfalls korrekt.

### 3. Restore-Algorithmus

1. Backup laden, Status prüfen (**nur** aus `AVAILABLE`/`FAILED`/`PARTIAL`).
2. Restore-Lock pro Death-ID setzen (verhindert Doppel-Restore / Race Conditions).
3. Zielinhaber ermitteln (muss online sein – sonst verweigert, um Itemverlust zu vermeiden).
4. Alle Stacks mit passender Death-ID suchen:
   - Inventare/Rüstung/Offhand/Endertruhe aller Online-Spieler,
   - alle geladenen Item-Entities,
   - (optional) alle Container in geladenen Chunks.
5. Gefundene Stacks **aus ihren Positionen entfernen** (Slot leeren bzw. Entity entfernen).
6. Items dem ursprünglichen Spieler geben; **was nicht ins Inventar passt, wird
   beim Spieler gedroppt** – es geht nie verloren.
7. Provenance-Markierung entfernen, Status auf `RESTORED` (bzw. `PARTIAL`) setzen,
   persistieren.

### 4. Warum keine Duplikation entsteht

Der Kern ist einfach und beweisbar: **Ein Restore verschiebt nur physikalisch
existierende Stacks, die exakt die Death-ID tragen.** Er erzeugt niemals Items.

- Fremde/neue Items haben eine andere (oder keine) Markierung → sie werden nie angefasst.
- Dasselbe physische Item kann nicht doppelt angerechnet werden (es wird entfernt, bevor es übergeben wird).
- Bereits verbrauchte Death-Items existieren nicht mehr → sie werden **nicht** neu erzeugt.
- Ein vollständiges zweites Restore ist blockiert: `RESTORED` ist nicht wiederherstellbar.

### 5. Persistenz

Die Backups liegen in einem Vanilla `SavedData` (server-weit), verwaltet über
`MinecraftServer#getDataStorage()`. Dadurch überleben sie Server-Neustarts. Ein
durch einen Crash unterbrochener Restore (`RESTORING`) wird beim Start automatisch
zu `FAILED` und kann gefahrlos erneut ausgeführt werden.

---

## Projektstruktur

```
invrestore/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── src/main/java/com/invrestore/
│   ├── InvRestore.java                 (ModInitializer, Verdrahtung)
│   ├── command/
│   │   ├── InvRestoreCommand.java      (/invre, Tab-Completion, Chat-UI)
│   │   ├── InvPermissions.java         (Permission-Nodes)
│   │   └── Selector.java               (Argument-Auflösung, testbar)
│   ├── config/InvRestoreConfig.java
│   ├── data/
│   │   ├── BackupStatus.java
│   │   ├── DeathBackup.java            (Snapshot + Codec)
│   │   ├── InvRestoreSavedData.java    (Persistenz)
│   │   └── Provenance.java             (Markierung)
│   ├── mixin/ServerPlayerMixin.java    (Snapshot vor dem Tod)
│   └── server/
│       ├── DeathHandler.java           (Snapshot + Markierung)
│       ├── ItemScanner.java            (Auffinden der Death-Items)
│       ├── RestoreService.java         (Transfervorgang)
│       ├── SnapshotPolicy.java         (was Vanilla droppt - Snapshot-Regeln)
│       └── TrackedContainers.java      (Chunk-Load/-Unload-Tracking)
└── src/main/resources/
    ├── fabric.mod.json
    └── invrestore.mixins.json
```

---

## Bekannte technische Einschränkungen

- **keepInventory:** Bei aktiver Gamerule droppt Vanilla nichts → es entsteht
  **kein Backup** und es gibt nichts wiederherzustellen. Das ist beabsichtigt
  und entspricht dem Vanilla-Verhalten.
- **Nur geladene Chunks.** Item-Entities und Container können nur in aktuell
  geladenen Chunks gefunden werden. Death-Items in entladenen Chunks werden beim
  ersten Restore ggf. nicht erfasst; sie werden beim nächsten Restore (Status
  `PARTIAL`, erneut ausführbar) erfasst, sobald der Chunk geladen ist.
- **Container-Tracking** erfasst Container in geladenen Chunks. Es wird **kein**
  dauerhafter, weltweiter Scan betrieben; die Suche läuft nur bei einem Restore
  und wird über `enableContainerTracking` gesteuert.
- **Endertruhe wird nicht gesichert** (sie wird beim Tod nicht gedroppt), aber bei
  der Suche mit berücksichtigt – Death-Items, die dort hineingeraten sind, werden
  also zurückgeholt.
- **Restore-Ziel muss online sein.** Ist der Inhaber offline, wird der Restore mit
  klarer Meldung abgelehnt (Backup bleibt erhalten) – so geht garantiert kein Item verloren.
- **Verbrauchte Items** (Crafting, Schmelzen, Benutzen …) können nicht
  wiederhergestellt werden. Es wird nur zurückgegeben, was noch existiert.
- **Markierte Items stapeln sich nicht mit normalen Items** desselben Typs. Das ist
  beabsichtigt und die Grundlage dafür, dass Herkunft und Duplikationsschutz exakt bleiben.
- Ein Item-Stack wird als Ganzes zurückgeführt (durch die Markierung ist er
  eindeutig einem Death zugeordnet). Ein teilweise markierter Misch-Stack entsteht
  dadurch gar nicht erst.
- Minecraft verändert bei einer Vanilla-Transformation (z. B. Crafting) die
  Data Components; das Ergebnis-Item trägt die Markierung nicht mehr und gilt damit
  konservativ als „verbraucht".

---

## Release / Build-Artefakte

Ein Release-Build erzeugt automatisch alle Artefakte:

```bash
./gradlew clean build
```

| Artefakt | Ort |
|---|---|
| **Mod-JAR** (in den `mods`-Ordner) | `build/libs/invrestore-1.0.0.jar` |
| Sources-JAR (nur Mod-Quellen) | `build/libs/invrestore-1.0.0-sources.jar` |
| **Source-ZIP** (komplettes Projekt) | `build/distributions/invrestore-1.0.0-project-sources.zip` |

Das Source-ZIP wird vom Task `projectSourcesZip` erzeugt und enthält genau den
Projektinhalt (Quellen, Ressourcen, Build-Skripte, Gradle-Wrapper, Workflow,
Doku) – **ohne** Build-, IDE-, OS- und temporäre Dateien.

### Release-Automatisierung (GitHub Actions)

`.github/workflows/release.yml` baut bei jedem Push auf `main`/`feature/**`,
bei Pull Requests und manuell (`workflow_dispatch`):

1. Repository auschecken
2. **Java 25** (Temurin) einrichten
3. `./gradlew clean test` – Tests müssen grün sein
4. `./gradlew build` – Mod-JAR + Source-ZIP erzeugen
5. Command-Surface-Check: nur `/invre` darf registriert sein, keine Spur von
   den alten, falschen Command-Namen
6. Upload der Artefakte: **`invrestore-mc26.1.2-java25`** (Mod-JARs aus
   `build/libs/*.jar`) und **`invrestore-sources-zip`**
   (`build/distributions/invrestore-*-project-sources.zip`)

Ein Release gilt erst als erfolgreich, wenn **Build und Tests tatsächlich
durchgelaufen sind** – die Upload-Schritte laufen nach dem Build, schlagen
fehlende Artefakte hart fehl (`if-no-files-found: error`).

### Artefakt-Anforderungen

Die Release-JAR ist:

- für **Minecraft 26.1.2** gebaut (`minecraft_version=26.1.2`,
  `fabric.mod.json` → `"minecraft": "~26.1.2"`)
- mit **Java 25** kompiliert (`options.release = 25`, requires Java ≥ 25)
- Fabric Loader **≥ 0.19.5** + Fabric API **0.155.3+26.1.2**
- Mod-ID **`invrestore`**, Environment `server` (kein Client-Mod nötig)
- Command **`/invre`** (einziger registrierter Restore-Command)

---

## Tests

`./gradlew test` führt **21 Unit-Tests** aus (die Kernlogik ist bewusst frei von
Welt-/Server-Zustand gehalten, damit sie testbar ist):

- **SelectorTest** – Erkennung/Auflösung von `latest`, Index, UUID-Präfix,
  Reihenfolge „neuestes zuerst", Fehlerfälle.
- **BackupStatusTest** – erlaubte Restore-Zustände (`AVAILABLE`/`FAILED`/`PARTIAL`);
  `RESTORING`/`RESTORED` gesperrt.
- **InvRestoreSavedDataTest** – Sortierung, Eigentümer-Scoping, Pruning
  (RESTORED vor AVAILABLE, AVAILABLE wird nie gelöscht), Crash-Recovery.
- **SnapshotPolicyTest** – Snapshot-Regeln: gesamter Inventar-Container,
  Markierung nur wenn Vanilla auch droppt (keepInventory), Kopien vor der
  Markierung, gemeinsame Equipment-Slot-Liste von Snapshot und Scanner
  (abgeglichen mit der verifizierten Vanilla-26.1.2-Death-Pipeline).

### Manuelle Test-Matrix (im Spiel zu prüfen)

Da Item-Erzeugung und Weltzustand erst mit geladener Welt existieren, sind die
folgenden Szenarien als manuelle In-Game-Tests gedacht:

1. Steve stirbt mit 32 Dias → alle liegen am Boden → `/invre latest`: Steve +32, Welt 0.
2. Alex hebt alle 32 auf → Restore: Steve +32, Alex −32.
3. Alex hebt 12, Bob 20 → Restore: Steve +32, Alex −12, Bob −20.
4. Nach dem Tod farmt Steve 10 weitere Dias → Restore: Steve hat 10 neue **und** 32 alte.
5. Alex gibt Steve 5, Steve verbraucht 2 → Restore liefert nur noch 30 (keine 32 neu).
6. Ein Teil liegt in einer Chest (geladener Chunk) → wird zurückgeführt.
7. Server-Neustart → Backups bleiben vorhanden.
8. Normaler Spieler mit `/invre Steve latest` → abgelehnt; OP darf es.
9. Die alten, falschen Command-Namen existieren **nicht** mehr (unbekannter Command);
   nur `/invre` funktioniert.
10. `keepInventory true` → kein Backup, kein Markieren; nach `keepInventory false`
    funktioniert der normale Ablauf wieder.

---

## Lizenz

MIT (siehe `LICENSE`).
