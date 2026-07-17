# Ultima Client-Anticheat (Fabric 1.21.11)

Eine rein **client-seitige** Anticheat-Mod: Sie beobachtet **andere Spieler** und warnt dich
bei verdächtigem Verhalten. Kein Server-Plugin, kein Banning – nur Anzeige und Warnung.

## Features

- **GUI-Overlay mit `Left Alt`** umschaltbar (Taste in den Minecraft-Steuerungsoptionen
  umbelegbar), drei Tabs:
  - **Verdächtige:** Spielername, erkannter Cheat-Verdacht, Score, Schweregrad (gelb/orange/rot),
    Zeitstempel, Bestätigungen anderer Mod-Nutzer („Mehrfach bestätigt"). **Klick auf eine
    Zeile** öffnet die Detail-Ansicht mit allen Modul-Scores und Bestätigungen – inklusive
    **„Spieler ignorieren"** (Freunde-Whitelist, persistent).
  - **Verlauf:** alle bisherigen Benachrichtigungen zum Nachlesen
  - **Einstellungen:** jedes Modul einzeln an/aus + Schwellen-Slider, dazu globale Schalter
    (Benachrichtigungen, Sound, Team-Meldungen, HUD) – persistent in `config/ultima_anticheat.json`
- **Mini-HUD** oben rechts: zeigt dauerhaft die bis zu drei auffälligsten Spieler über der
  Schwelle, ohne dass das GUI geöffnet sein muss (abschaltbar).
- **Lag-Erkennung:** springt die Server-Weltzeit (Lag/Catch-up), pausieren alle
  bewegungs-basierten Checks automatisch für ein paar Sekunden – der größte
  False-Positive-Killer. Spieler weiter als 64 Blöcke werden gar nicht geprüft.
- **Zweisprachig:** Deutsch und Englisch (`de_de`/`en_us`-Sprachdateien).
- **11 Erkennungsmodule** (je ein eigenes Package unter `detection/`):
  Killaura, Reach, Aimbot, AutoClicker/CPS, NoSlow, Speed, Fly/Jesus/Step, Scaffold,
  Hitbox, Timer, Anti-Knockback
- **Verdachtswert-System:** 0–100 pro Spieler+Modul, steigt bei Auffälligkeiten, fällt über
  Zeit ab (reduziert False Positives). Farbstaffelung: gelb = Verdacht, orange = wahrscheinlich,
  rot = sehr wahrscheinlich.
- **Benachrichtigungen:** Chat-Zeile (nur lokal) + Actionbar-Overlay + optionaler Sound,
  gesammelt in der Verlaufsliste. Deaktivierte Module lösen nichts aus und kosten keine Rechenzeit.
- **Team-Kanal:** Mod-Nutzer tauschen Verdachtsmeldungen über einen eigenen
  Custom-Payload-Kanal (`ultima_anticheat:report_*`) aus – unsichtbar für Spieler ohne Mod,
  getrennt vom öffentlichen Chat. Melden mehrere Nutzer denselben Spieler, wird der Eintrag
  als „Mehrfach bestätigt (von X Spielern)" hervorgehoben.

## Bauen

Voraussetzung: **Java 21** (JDK). Internetzugang für den ersten Build (lädt Minecraft,
Yarn-Mappings `1.21.11+build.6`, Fabric Loader 0.19.3, Fabric API).

```bash
./gradlew build
```

Die fertige Mod liegt danach unter `build/libs/ultima-anticheat-1.0.0.jar`.
Zum Testen im Entwicklungs-Client: `./gradlew runClient`. Unit-Tests laufen mit
`./gradlew test` (und automatisch bei jedem `build`).

**CI:** Jeder Push baut die Mod automatisch über GitHub Actions
(`.github/workflows/build-anticheat.yml` im Repo-Root); die fertige Jar hängt
als Artifact „ultima-anticheat" am Workflow-Lauf.

## Installation

`ultima-anticheat-1.0.0.jar` zusammen mit der **Fabric API** in den `mods/`-Ordner
(Fabric Loader ≥ 0.19.3, Minecraft 1.21.11).

## Wichtige Hinweise

- **Alles Heuristik:** Der Client sieht andere Spieler nur so, wie der Server sie sendet
  (interpolierte Positionen, keine Effekte/Pakete anderer). Die Module sind bewusst
  konservativ eingestellt; ein einzelner Flag ist ein Hinweis, kein Beweis. Latenz und
  Lags können Einzelmeldungen verursachen – deshalb fallen Verdachtswerte automatisch ab.
- **Team-Kanal braucht ein Relay:** Custom Payloads laufen technisch immer über den Server.
  Liegt diese Mod auch auf einem **Fabric-Server**, leitet sie Meldungen automatisch an
  andere Mod-Nutzer weiter (mit Rate-Limiting und fälschungssicherem Absender). Auf
  Vanilla-/fremden Servern bleibt der Kanal einfach inaktiv – die Mod erkennt das selbst
  (`canSend`) und sendet dann gar nicht.
- **Fair Play:** Die Mod greift nicht ins Spiel ein, verändert keine Spielmechanik und
  liest nur, was der Client ohnehin sieht.

## Projektstruktur

```
src/main/java/com/ultimasmp/anticheat/        Gemeinsam (Client+Server)
  UltimaAnticheatMod.java                     Entrypoint: registriert den Netzwerkkanal
  network/                                    Payloads + Server-Relay (Rate-Limiting)
src/client/java/com/ultimasmp/anticheat/client/
  UltimaAnticheatClient.java                  Entrypoint: Tick-Loop, Left-Alt-Toggle
  config/                                     Persistente Einstellungen (Gson)
  track/                                      Spieler-Beobachtung (Snapshots, Treffer-Zuordnung)
  detection/<modul>/                          Je ein Package pro Erkennungsmodul
  suspicion/                                  Verdachtswert-System (Anstieg/Abfall, Schweregrade)
  notify/                                     Benachrichtigungen + Verlauf
  share/                                      Team-Kanal (Senden/Empfangen, Spam-Schutz)
  gui/                                        Das GUI (AnticheatScreen)
```
