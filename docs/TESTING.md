# Manuelle Test-Matrix

Szenarien, die eine geladene Welt, echte Spieler oder einen Server-Neustart
benötigen, können nicht als Unit-Tests laufen. Sie sind hier als manuelle
In-Game-Prüfliste dokumentiert.

| # | Szenario | Erwartet |
|---|---|---|
| 1 | Steve stirbt mit 32 Diamanten, Drop liegt am Boden, `/invre latest` | Steve +32, Drops verschwinden, keine Duplikate |
| 2 | Steve stirbt mit 32, Alex hebt alle 32 auf, Restore | Steve +32, Alex −32 |
| 3 | Steve stirbt mit 32, Alex nimmt 12, Bob 20, Restore | Steve +32, Alex −12, Bob −20; fremde Items unberührt |
| 4 | Steve stirbt mit 32, farmt danach 10 neue, Restore | Steve hat 10 neue **und** 32 alte |
| 5 | 5 der 32 werden verbraucht, Restore | Nur die vorhandenen 27 kommen zurück |
| 6 | Ein Teil der Death-Items liegt in einer Chest (geladener Chunk), Restore | Nur die markierten Stacks werden aus der Chest entfernt |
| 7 | Death-Items werden gesplittet (12/20) und verschoben | Beide Teile behalten die Death-ID, Restore holt beide |
| 8 | Server-Neustart, dann `/invre` | Backups sind noch vorhanden |
| 9 | Restore starten und Server hart killen, dann Neustart | Backup ist `FAILED`, Retry funktioniert ohne Duplikation |
| 10 | Zweimal schnell `/invre latest` | Zweiter Aufruf wird abgewiesen bzw. liefert kein Duplikat |
| 11 | Nicht-OP führt `/invre Steve latest` aus | Abgelehnt (Permission) |
| 12 | OP führt `/invre Steve latest` aus | Erlaubt |
| 13 | Steve ist offline, Admin versucht Restore | Abgelehnt, nichts wird entfernt, Status unverändert |
| 14 | Steves Inventar ist voll, Restore | Überzählige Items werden bei Steve gedroppt, nichts gelöscht |
| 15 | Toter Spieler mit aktivem `keepInventory` | Kein Backup, keine Markierung, Vanilla-Verhalten normal |
| 16 | Item mit Fluch der Vergänglichkeit beim Tod | Keine Markierung, kein Restore-Eintrag dafür |
| 17 | Tab-Completion als normaler Spieler | Nur `latest` + eigene Indizes, keine fremden Namen |
| 18 | Death-Item in Endertruhe gelegt, Restore | Wird gefunden und zurückgeholt |
