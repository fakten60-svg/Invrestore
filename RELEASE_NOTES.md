# InvRestore v1.0.0

Erster Release von **InvRestore** — serverseitige Death-Backups mit
Provenance-Tracking für Minecraft 26.1.2.

| | |
|---|---|
| Mod-Version | **1.0.0** |
| Minecraft | **26.1.2** (Java Edition, Dedicated Server) |
| Java | **25** |
| Fabric Loader | **≥ 0.19.5** |
| Fabric API | **0.155.3+26.1.2** |

## Features

- **Death-Backup vor dem Vanilla-Drop**: Mixin an HEAD von `ServerPlayer#die`
  sichert Hauptinventar, Hotbar, Rüstung und Offhand mit allen Data Components
- **Provenance**: jedes fallende Item trägt die Death-ID in
  `minecraft:custom_data` (`invrestore: { v: 1, death: <uuid> }`) — persistent
  und client-sicher
- **Vanilla bleibt Vanilla**: Drops, Pickup, Handel und Truhen funktionieren
  normal; andere Spieler dürfen Death-Items behalten, bis ein Restore läuft
- **Echter Restore statt Item-Spawn**: es werden nur Stacks zurückgegeben, die
  physisch existieren und exakt die Death-ID tragen — bei Boden-Items, in
  fremden Inventaren, Endertruhen und Container-BEs geladener Chunks
- **No-Duplication**: neue Items des Spielers bleiben unangetastet, verbrauchte
  Items werden nie neu erzeugt, verteilte Items werden exakt abgezogen
- **Split/Merge-sicher**: geteilte Stacks behalten die Death-ID; markierte Items
  stacken nicht mit normalen Items desselben Typs
- **Statusmaschine**: `AVAILABLE` / `RESTORING` / `RESTORED` / `PARTIAL` /
  `FAILED` mit Restore-Lock gegen Doppel-Restore
- **Persistence & Crash-Recovery**: Vanilla `SavedData`; unterbrochene Restores
  werden beim Neustart auf `FAILED` gesetzt und können sicher wiederholt werden
- **Command `/invre`** (einziger Command): Auflisten und Restaurieren eigener
  Backups; Admins (`invrestore.admin.others`) verwalten fremde Spieler — mit
  Tab-Completion und klickbarem `[RESTORE]`-Button
- **Permissions**: `invrestore.self` (alle), `invrestore.admin` /
  `invrestore.admin.others` (Standard: OP-Level 2), via Fabric Permission API
  überschreibbar
- **Konfiguration**: `config/invrestore.json` (`maxBackupsPerPlayer`,
  `enableAdminRestore`, `enableContainerTracking`, `debugLogging`)

## Wichtige Hinweise

- Bei aktivem `keepInventory` droppt Vanilla nichts — es entsteht **kein
  Backup** und es gibt nichts wiederherzustellen.
- Der Inhaber muss für einen Restore **online** sein; sonst wird der Restore
  abgelehnt, damit kein Item verloren geht.
- Markierte Items stapeln sich nicht mit normalen Items desselben Typs
  (bewusst, Grundlage des Duplikationsschutzes).

## Bekannte Einschränkungen

- Items in **entladenen Chunks** werden erst gefunden, wenn der Chunk wieder
  geladen ist; der Backup bleibt dann `PARTIAL` und ist erneut ausführbar.
- **Verbrauchte oder transformierte Items** (Crafting, Schmelzen, Benutzen)
  sind nicht wiederherstellbar — es wird nur zurückgegeben, was noch existiert.

## Installation

1. Fabric Loader ≥ 0.19.5 für Minecraft 26.1.2 installieren
2. Fabric API 0.155.3+26.1.2 in den `mods`-Ordner legen
3. `invrestore-1.0.0.jar` in den `mods`-Ordner legen
4. Server starten — ein Client-Mod ist **nicht** erforderlich
