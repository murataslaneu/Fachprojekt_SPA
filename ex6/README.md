# Fachprojekt Analyseaufgabe 6: Architecture Validation

Dieses Projekt analysiert den Java-Bytecode eines Projekt und überprüft, ob eine selbst festgelegte 
Architektur eingehalten wird. Man kann festlegen, welche Klassen/Packages/Jars mit anderen
Klassen/Packages/Jars interagieren dürfen.

Es werden Methodenaufrufe und Feldzugriffe betrachtet,
aber (einstellbar) auch weitere Abhängigkeiten wie z.B. Vererbung, Benutzung des Typs in Methoden,
und so weiter.

Es ist auch eine "Ground Truth" enthalten, die als Referenz dient, welche Ausgabe diese Analyse geben
sollte. Diese befindet sich im Ordner `ground_truth`.

## Inhalt

- [Anleitung: Benutzung der Analyse](#anleitung-benutzung-der-analyse)
- [Beispielszenarien](#beispielszenarien)
- [Terminal-Argumente](#terminal-argumente)
- [Config-Optionen](#config-optionen)
- [Architektur-Spezifikations-Json](#architektur-spezifikations-json)
  - [Regeln](#regeln)
- [Tests und Coverage](#tests-und-coverage)

## Anleitung: Benutzung der Analyse
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex6`).
2. Starte sbt mit `sbt`.
3. Führe das Programm mittels `run` aus. Das Programm bietet die Möglichkeit, entweder per Config-Datei
   (mittels `-config=<path/config.json>`) oder per Terminal-Optionen konfiguriert zu werden. Die Erklärung
   zu den beiden Optionen befinden sich in den jeweiligen Abschnitten. Folgende Befehle sollten alle dasselbe tun:
   ```
   run -config=ground_truth/config.json
   run "-cp=ground_truth/ExampleMain.jar;ground_truth/ExampleHelper.jar;ground_truth/ExampleDBAdapter.jar" -spec=ground_truth/spec.json -output=ground_truth/architecture-report.json
   run -cp=ground_truth/ExampleMain.jar -cp=ground_truth/ExampleHelper.jar -cp=ground_truth/ExampleDBAdapter.jar -spec=ground_truth/spec.json -output=ground_truth/architecture-report.json
   ```
4. Die Analyse wird ausgeführt, und das Ergebnis wird als JSON-Datei im angegebenen Pfad ausgegeben
   (oder standardmäßig in `architecture-report.json` im aktuellen Verzeichnis).
> **Anmerkung 1**: Wenn ein Pfad Leerzeichen enthält oder mehrere Pfade angegeben werden müssen,
> muss man das Argument mit Anführungszeichen (") packen, z.B. `"-cp=example Path/test.jar"`.

> **Anmerkung 2**: Separiere mehrere Pfade mit `;` in Windows und `:` in Unix/Linux/macOS.
> Wie das vorherige Beispiel zeigt, kann man aber stattdessen aber auch dasselbe Argument mehrmals angeben.

## Beispielszenarien

Für dieses Projekt stehen viele Beispiele und Beispielkonfigurierungen zur Verfügung, mit denen man die 
Analyse ausprobieren kann. Liste von Beispiel-Befehlen (wobei die ersten beiden Beispiele am interessantesten sind):
- `run -config=ground_truth/config.json` (Ausführung auf Ground Truth)
- `run -config=src/test/allDependenciesExample` (Ausführung auf Testprojekt,
  welches jede Abhängigkeit mindestens einmal enthält)
- `run -config=tests/allJarsForbidden/exampleConfig.json` (Konfiguration,
  wo sämtliche Abhängigkeiten verboten sind.)
- `run -config=tests/banlist/exampleConfig.json` (Konfiguration, wo grundsätzlich alles
  erlaubt wird und eine Abhängigkeit verboten wird. *Keine Violations hier erwartet.*)
- `run -config=tests/deepNestedException/exampleConfig.json` (Konfiguration, die starken
  Gebrauch von der Verschachtelung von Regeln/Ausnahmen macht.)
- `run -config=tests/ownJarsOnly/exampleConfig.json` (Konfiguration, wo nur Gebrauch selbst
  geschriebener jars erlaubt wird, alles andere wird verboten.)
- `run -config=tests/packageExceptions/exampleConfig.json` (Konfiguration,
  wo Package Zugriff auf anderes Package erlaubt wird, außer für ein Unterpackage.)
- `run -config=tests/pdfboxAllowed/exampleConfig.json` (Konfiguration,
  die pdfbox für die eigenen Projekte erlaubt und sonst alles verbietet.)
- `run -config=tests/simpleRecursive/exampleConfig.json` (Konfiguration, die packageExceptions um eine
  weitere Ebene mit einer Exception erweitert.)

## Terminal-Argumente

Wird keine Config-Datei mit angegeben, kann man die Parameter für die Analyse komplett über das
Terminal eingeben.

**Verfügbare Argumente/Optionen:**
- **`-config=<json file>`: Pfad zur Config-Datei, wenn man die Analyse über diese konfigurieren möchte.
  Wenn übergeben, werden <u>alle anderen Optionen</u> (inklusive den "notwendigen" Argumenten),
  die über das Terminal übergeben wurden, <u>ignoriert</u>!**
- `-help`: Flag, der alle verfügbaren Optionen im Terminal anzeigen lässt und das Programm beendet.
- `-cp=<jar file(s)>`: **Notwendiger** Pfad zur Jar-Datei/Jar-Dateien, die zum Projekt gehören. `-cp` kann auch mehrmals
  angegeben werden, um z.B. mehrere Pfade anzugeben.
- `-spec=<json file>`: **Notwendiger** Pfad zur Json-Datei, die die Architektur-Spezifikationen enthält. Was diese
  enthalten kann/muss wird im jeweiligen Abschnitt erklärt.
- `-libcp=<jar file(s)`: Optionaler Pfad zu einer Jar-Datei, die das Projekt als Bibliothek nutzt. *Könnte* den Report
  verbessern, indem Packages und Klassen zu vorher unbekannten Jar-Dateien zugeordnet werden können,
  **muss es aber nicht!**
- `-completelyLoadLibraries`: Flag, der OPAL alle Bibliotheken (mit -libcp) vollständig laden lässt,
  anstatt nur als Interfaces. *Hat <u>vermutlich</u> keinen Effekt auf die Analyse, außer diese zu verlangsamen...*
- `-outputJson=<json file>`: Optionaler Pfad, zu der der Json-Report für die Analyse ausgegeben werden soll.
  Standardmäßig wird der Analysereport in `architecture-report.json` im aktuellen Verzeichnis abgelegt, wenn nicht angegeben.
- `-onlyMethodAndFieldAccesses`: Flag, der die Analyse nur noch Methodenaufrufe und Feldzugriffe als Abhängigkeiten
  ansehen lässt. Standardmäßig werden auch z.B. Vererbung und weiteres betrachtet.

## Config-Optionen

Möchte man die Analyse über eine Config-Datei konfigurieren, kann man beim Starten der Analyse mittels
`-config=<json file>` eine Json-Datei angeben, die die Analyse konfiguriert.

**Verfügbare Optionen:**
- `"projectJars"`: **Notwendig**, Liste von Strings. Pfad(e) zur Jar-Datei / zu den Jar-Dateien, die zum
  Projekt gehören. *Ersetzt`-cp` im Terminal.*
- `"libraryJars"`: Optional, Liste von Strings. Pfad(e) zur Jar-Datei / zu den Jar-Dateien, die vom Projekt als
  Bibliothek genutzt werden. *Könnte* den Report verbessern, indem Packages und Klassen zu vorher
  unbekannten Jar-Dateien zugeordnet werden können, **muss es aber nicht!** *Ersetzt `-libcp` im Terminal.*
- `"specificationsFile"`: **Notwendig**, String. Pfad zur Json-Datei, die die Architektur-Spezifikation enthält. Was diese
  enthalten kann/muss wird im jeweiligen Abschnitt erklärt. *Ersetzt `-spec` im Terminal.*
- `"outputJson"`: Optional, String. Pfad, zu der der Json-Report für die Analyse ausgegeben werden soll.
  Standardmäßig wird der Analysereport in `architecture-report.json` im aktuellen Verzeichnis abgelegt, wenn nicht angegeben.
- `"completelyLoadLibraries"`: Optional, Boolean. Flag, der OPAL alle Bibliotheken vollständig laden lässt,
  anstatt nur als Interfaces. *Hat <u>vermutlich</u> keinen Effekt auf die Analyse, außer diese zu verlangsamen...* Standardmäßig `false`.
- `"onlyMethodAndFieldAccesses"`: Optional, Boolean. Flag, der die Analyse nur noch Methodenaufrufe und Feldzugriffe als Abhängigkeiten
  ansehen lässt. Standardmäßig werden auch z.B. Vererbung und weiteres betrachtet (Flag ist also auf `false` gesetzt).

## Architektur-Spezifikations-Json

Für jede Analyse muss zwingend eine Json-Datei angegeben werden, die die gewünschte Architektur für das
Projekt vorgibt und überprüft, ob diese im Projekt auch tatsächlich eingehalten wird. **Der Aufbau der Datei entspricht dem,
wie es auf dem Aufgabenblatt gefordert wird.**

> Abhängigkeiten zur eigenen Klasse werden im Vorhinein herausgefiltert für die Violations, da diese
> Abhängigkeiten absolut uninteressant sind...

Es müssen zwei "Grund-Optionen" angegeben werden **müssen**:
- `"defaultRule"`: Entweder `"FORBIDDEN"` oder `"ALLOWED"`. Bei `"FORBIDDEN"` wird standardmäßig keine einzige Abhängigkeit
  erlaubt, bei `"ALLOWED"` wird hingegen standardmäßig alles erlaubt.
- `"rules"`: Liste von **Regeln**.

### Regeln

Regeln geben weitere Vorgaben/Ausnahmen an, was erlaubt oder verboten sein soll. Diese folgen in der Json-Datei
immer demselben Schema:
```
{
    "from": "...",
    "to": "...",
    "type": "...",
    "except": [...]
}
```

- `"from"`: Klasse/Package/Jar-Datei, die auf etwas zugreift bzw. zugreifen könnte und dafür eine Regel
  festgelegt werden soll.
- `"to"`: Klasse/Package/Jar-Datei, auf die von irgendwo zugegriffen werden könnte und dafür eine Regel festgelegt werden sollte.
- `type`: `"FORBIDDEN"` oder `"ALLOWED"`. Gibt an, ob der Zugriff von `from` auf `to` verboten bzw. erlaubt werden soll.
- `except`: Liste weiterer **Regeln**, die diese Regel weiter spezifizieren/einschränken. Regeln können also beliebig
  tief ineinander verschachtelt werden.

> **Anmerkung:** Es ist theoretisch möglich, bei `except` beliebige Regeln hinzuzufügen. Dazu gehören auch
> Regeln, die gar nichts mit der ursprünglichen Regel zu tun haben. **Das wird allerdings nicht empfohlen** und
> resultiert auch in einer Warnung von der Analyse, da es die Lesbarkeit der Spezifikation stark beeinträchtigt.

## Tests und Coverage
Ebenfalls wird dieses Projekt mit ScalaTest getestet und die Test Coverage mithilfe von sbt-scoverage gemessen.

**Ausführung der Tests:**
```
sbt test
```
**Ausführung der Tests mit Messung der Test Coverage:**
```
sbt clean coverage test coverageReport
```

Der Coverage-Report wird (unter anderem) im HTML-Format ausgegeben. Dieser kann sich dann beim
Öffnen der jeweiligen Datei im Browser graphisch angeschaut werden.

Alternativ können auch über IntelliJ die Tests gestartet werden oder die Test Coverage gemessen werden.

> Messung der Test Coverage über IntelliJ: Bei den Projekt-Ordnern, mache Rechtsklick auf den Ordner
> `src/test` (oder einen beliebigen Unterordner), gehe zum Punkt `More Run/Debug`, und klicke dann auf 
> `Run ScalaTests in 'test'...' with Coverage`.
> Die Tests mit Messung der Test Coverage sollten dann ausgeführt werden.