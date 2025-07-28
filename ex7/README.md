# Fachprojekt Analyseaufgabe 7: Analyse-Applikation

Dieses Projekt fasst alle bisherigen Analysen als eine große Analyse-Applikation zusammen,
sodass alle Analysen über ein einziges Programm und einer einzigen Json-Config konfiguriert
und nacheinander automatisiert ausgeführt werden können.

## Inhalt

- [Ausführung](#ausführung)
  - [Direkt über sbt](#direkt-über-sbt)
  - [Kompilieren und Ausführen über `java -jar`](#kompilieren-und-ausführen-über-java--jar)
- [CLI-Parameter (Starten über Terminal)](#cli-parameter-starten-über-terminal)
- [JSON-Config + Ausgabe für die Analysen](#json-config--ausgabe-für-die-analysen)
  -[Grundoptionen](#grundoptionen)
  - [Struktur der Analyse-Ausgabe](#struktur-der-analyse-ausgabe)
  1. [GodClassDetector (ex1)](#analyse-1-godclassdetector-ex1)
  2. [CriticalMethodsDetector (ex2)](#analyse-2-criticalmethodsdetector-ex2)
  3. TPLUsageAnalyzer (ex3)
  4. CriticalMethodsRemover (ex4.1)
  5. TPLMethodsRemover (ex4.2)
  6. DeadCodeDetector (ex5)
  7. ArchitectureValidator (ex6)
  - [Konfiguration Call-Graphen](#konfiguration-call-graphen)
- Tests

## Ausführung

### Direkt über sbt

1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex7`).
2. (Erstelle oder passe ggf. eine Json-Config an.)
3. Starte Programm über `sbt run`, beim Spezifizieren einer Config-Datei z.B. `sbt "run -config=config.json"`
4. Das Programm sollte ausgeführt werden. Die Ausgabe wird in dem Ordner ausgegeben, 
   der in der Config-Json spezifiziert wird, standardmäßig der Ordner *analysis* im aktuellen Verzeichnis.

> JVM-Optionen wie z.B. der zugewiesene RAM können über die *.jvmopts*-Datei angepasst werden.
> Das kann eventuell notwendig sein, wenn ein größeres Projekt analysiert wird.

### Kompilieren und Ausführen über `java -jar`

#### Kompilieren
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex7`).
2. Kompiliere das Projekt mittels `sbt clean assembly`.
3. Der Pfad zu erzeugten jar-Datei wird in der Konsole ausgegeben
   (sollte `target/scala.2.13/ex7-assembly-0.1.0.jar` sein).

#### Ausführen der jar-Datei
1. Öffne das Terminal im Ordner, wo sich die jar-Datei befindet.
2. (Erstelle oder passe ggf. eine Json-Config an.)
3. Starte das Programm über `java -jar ./ex7-assembly-0.1.0.jar` (Name evtl. abweichend, falls dieser geändert wurde).

> Argumente an die Analyse-Applikation werden nach dem Pfad zur jar-Datei geschrieben,
> z.B. `java -jar ./ex7-assembly-0.1.0.jar -config=config.json`.

> Argumente an die JVM werden nach dem "java" übergeben, z.B. `java -Xms512m -Xmx2g -jar ./ex7-assembly-0.1.0.jar`
> (-Xms setzt die *minimale* Heap-Größe (hier 512 MB), -Xmx die *maximale* Heap-Größe (hier 2 GB)).

## CLI-Parameter (Starten über Terminal)

Es ist immer nur die Übergabe eines einzelnen Parameters möglich, sie schließen sich also gegenseitig aus.

| Parameter            | Parameter-Wert                        | Anmerkungen                                                                                                                            |
|----------------------|---------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `-config=<Pfad>`     | Pfad: Muss zu einer Json-Datei führen | Dient zur Konfiguration, welche Config für die Analyse verwendet werden soll. Standardmäßig `config.json` im aktuellen Verzeichnis.    |
| `-help`              | -                                     | Zeigt Hilfe-Text zur Bedienung des Programms an (bezüglich der Konsolen-Parameter)                                                     |
| `-initializeConfig`  | -                                     | Erstellt eine Default-Config `config.json` im aktuellen Verzeichnis. Bricht Erstellung ab, wenn Datei mit dem Namen bereits existiert. |

Hat man das Programm als jar-Datei kompiliert, kann man auch der JVM Parameter übergeben, um z.B. den RAM anzupassen (siehe im [vorherigen Abschnitt](#kompilieren-und-ausführen-über-java--jar)).

> Es ist theoretisch auch möglich, das Programm ohne Übergabe von Parametern zu starten. Wenn keine `config.json` im aktuellen Verzeichnis vorhanden ist,
> wird diese dann automatisch erstellt und das Programm beendet. Ist die Config-Datei vorhanden, wird diese
> standardmäßig ausgeführt.

> Es ist daher auch möglich, das Programm direkt per Doppelklick im File Explorer zu starten.
> Das ist allerdings nicht empfehlenswert, da man keinen Konsolen-Log erhält und kein Feedback bekommt,
> wann die Analyse abgeschlossen wird.

## JSON-Config + Ausgabe für die Analysen

Das Programm wird über eine einzelne JSON-Datei als Config gesteuert. In dieser kann man festlegen, welche jar-Dateien
geladen werden sollen, wo das Ergebnis ausgegeben werden soll, welche Analysen ausgeführt werden sollen
und die Analysen konfigurieren.

Die allermeisten Optionen haben einen Default-Wert vorgegeben. Wird für die jeweilige Option `"DEFAULT"` eingegeben,
dann wird der Standardwert für diese Option verwendet. Ausgenommen davon sind `"projectJars"`, `"libraryJars"`, `"resultsOutputPath"`
und die jeweiligen `"execute"`-Optionen für einzelnen Analysen.

Die Default-Config für das Programm sieht folgendermaßen aus und bietet die folgenden Optionen:

```json
{
  "projectJars" : [ ],
  "libraryJars" : [ ],
  "resultsOutputPath" : "analysis",
  "godClassDetector" : {
    "execute" : false,
    "wmcThresh" : "DEFAULT",
    "tccThresh" : "DEFAULT",
    "atfdThresh" : "DEFAULT",
    "nofThresh" : "DEFAULT"
  },
  "criticalMethodsDetector" : {
    "execute" : false,
    "criticalMethods" : "DEFAULT",
    "ignore" : "DEFAULT",
    "callGraphAlgorithmName" : "DEFAULT",
    "entryPointsFinder" : "DEFAULT",
    "customEntryPoints" : "DEFAULT"
  },
  "tplUsageAnalyzer" : {
    "execute" : false,
    "countAllMethods" : "DEFAULT",
    "callGraphAlgorithmName" : "DEFAULT",
    "entryPointsFinder" : "DEFAULT",
    "customEntryPoints" : "DEFAULT"
  },
  "criticalMethodsRemover" : {
    "execute" : false,
    "criticalMethods" : "DEFAULT",
    "ignore" : "DEFAULT"
  },
  "tplMethodsRemover" : {
    "execute" : false,
    "tplJar" : "DEFAULT",
    "includeNonPublicMethods" : "DEFAULT",
    "callGraphAlgorithmName" : "DEFAULT",
    "entryPointsFinder" : "DEFAULT",
    "customEntryPoints" : "DEFAULT"
  },
  "deadCodeDetector" : {
    "execute" : false,
    "completelyLoadLibraries" : "DEFAULT",
    "domains" : "DEFAULT"
  },
  "architectureValidator" : {
    "execute" : false,
    "onlyMethodAndFieldAccesses" : "DEFAULT",
    "defaultRule" : "DEFAULT",
    "rules" : "DEFAULT"
  }
}
```

### Grundoptionen

Die Grundoptionen werden für alle Analysen benötigt.

| Option                | Erwarteter Wert                                                               | Default-Wert | Weitere Informationen                                                                 |
|-----------------------|-------------------------------------------------------------------------------|--------------|---------------------------------------------------------------------------------------|
| `"projectJars"`       | Liste von Strings (Dateipfade zu jar-Dateien)                                 | -            | jar-Dateien vom Projekt, das analysiert werden soll                                   |
| `"libraryJars"`       | Liste von Strings (Dateipfade zu jar-Dateien)                                 | -            | jar-Dateien von den Bibliotheken, die das Projekt nutzt (für viele Analysen optional) |
| `"resultsOutputPath"` | String (Pfad zu Ordner, wo Ausgaben der Analyse hingeschrieben werden sollen) | -            | -                                                                                     |

Die restlichen Optionen beziehen sich immer auf eine spezifische Analyse. Jede Analyse hat also eine eigene "Sub-Config".
Für jede Analyse ist außerdem immer eine Option `"execute"` vorhanden, die bestimmt, ob die Analyse ausgeführt werden soll oder nicht.

### Struktur der Analyse-Ausgabe

Mittels `"resultsOutputPath"` in der Config-Json (siehe [Grundoptionen](#grundoptionen)) kann festgelegt werden,
in welchem Ordner das Programm die Ergebnisse der ausgeführten Analysen ausgeben soll.
Im Ordner werden folgende Dateien abgelegt.
- `summary.json`: Gibt eine grobe Zusammenfassung darüber, welche Analysen wann ausgeführt wurden,
  wie lange die Laufzeiten waren und ob in der Analyse Fehler aufgetreten sind. Falls Fehler aufgetreten sind, kann
  in den Logs nachverfolgt werden, was schiefgegangen ist.
- `analysis.log`: Datei, die die Logs enthält, die während der Analyse in die Konsole geschrieben wurden. Nachrichten
  von OPAL werden nicht geloggt.
- Unterordner für die einzelnen Analysen: Jede Analyse erstellt nach Fertigstellung mindestens einen Json-Report,
  der die Ergebnisse der Analyse einmal zusammenfasst. Der Namen für die Ordner folgt dabei immer einer sehr ähnlichen
  Struktur: `1_GodClassDetector`, `2_CriticalMethodsDetector`, usw.

> ⚠️ **Wichtig**: Der Pfad/Ordner, der bei `"resultsOutputPath"` angegeben wird, sollte möglichst leer sein
> (oder keine Daten beinhalten, die man behalten möchte)!
> Dateien und Ordner können innerhalb des Ordners ungefragt gelöscht/überschrieben werden.
> Die Chance auf Datenverlust ist also hoch!

### Analyse 1: GodClassDetector (ex1)

Der GodClassDetector sucht mithilfe von Schwellenwerten für bestimmte Code-Parameter nach "God Classes"
(also Klassen, die viel Verantwortung übernehmen und damit schlecht zu warten sind).

Die Analyse für den GodClassDetector wird über `godClassDetector` in der Json-Config
konfiguriert.

```json
  "godClassDetector" : {
    "execute" : false,
    "wmcThresh" : "DEFAULT",
    "tccThresh" : "DEFAULT",
    "atfdThresh" : "DEFAULT",
    "nofThresh" : "DEFAULT"
}
```

| Option         | Erwarteter Wert                    | Default-Wert | Weitere Informationen                                                                                                             |
|----------------|------------------------------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `"execute"`    | Boolean (`true` oder `false`)      | -            | Bestimmt, ob der GodClassDetector ausgeführt werden soll oder nicht.                                                              |
| `"wmcThresh"`  | Integer ≥ 0                        | 100          | Grenzwert für WMC ("Weighted Methods per Class"), höhere Werte sind schlechter. Klassen sollten *kleiner* sein als der Grenzwert. |
| `"tccThresh"`  | Dezimalzahl zwischen 0.0 und 1.0   | 0.33         | Grenzwert für TCC ("Tight Class Cohesion"), niedrigere Werte sind schlechter. Klassen sollten *größer gleich* dem Grenzwert sein. |
| `"atfdThresh"` | Integer ≥ 0                        | 8            | Grenzwert für ATFD ("Access to Foreign Data", höhere Werte sind schlechter. Klassen sollten *kleiner gleich* dem Grenzwert sein.  |
| `"nofThresh"`  | Integer ≥ 0                        | 30           | Grenzwert für NOF ("Number of Fields"), höhere Werte sind schlechter. Klassen sollten *kleiner* sein als der Grenzwert.           |

### Analyse 2: CriticalMethodsDetector (ex2)

Der CriticalMethodsDetector sucht nach kritischen Methodenaufrufen
(also Aufrufe auf Methoden, die z.B. eventuell sicherheitsrelevant sein könnten). Für die Analyse wird ein [Call-Graph](#konfiguration-call-graphen) verwendet.

Die Analyse für den CriticalMethodsDetector wird über `criticalMethodsDetector` in der Json-Config
konfiguriert.

```json
"criticalMethodsDetector" : {
    "execute" : false,
    "criticalMethods" : "DEFAULT",
    "ignore" : "DEFAULT",
    "callGraphAlgorithmName" : "DEFAULT",
    "entryPointsFinder" : "DEFAULT",
    "customEntryPoints" : "DEFAULT"
}
```


| Option          | Erwarteter Wert                | Default-Wert | Weitere Informationen                                                       |
|-----------------|--------------------------------|--------------|-----------------------------------------------------------------------------|
| `"execute"`     | Boolean (`true` oder `false`)  | -            | Bestimmt, ob der CriticalMethodsDetector ausgeführt werden soll oder nicht. |
| `""`            |                                |              |                                                                             |
| `""`            |                                |              |                                                                             |
| `""`            |                                |              |                                                                             |
| `""`            |                                |              |                                                                             |

### Analyse 3: TPLUsageAnalyzer (ex3)

### Analyse 4a: CriticalMethodsRemover (ex4.1)

### Analyse 4b: TPLMethodsRemover (ex4.2)

### Analyse 5: DeadCodeDetector (ex5)
Der DeadCodeDetector analysiert den Bytecode eines Projekts und erkennt Instruktionen, die nie erreicht oder ausgeführt werden, also sogenannten „Dead Code“. Die Analyse basiert auf abstrakter Interpretation. Dabei kann interaktiv oder automatisch entschieden werden, welche Domain für die Analyse verwendet wird.

Die Analyse für den DeadCodeDetector wird über deadCodeDetector in der `Json-Config` konfiguriert.

```json
"deadCodeDetector": {
"execute": false,
"completelyLoadLibraries": "DEFAULT",
"domains": "DEFAULT"
}
```

| Option                      | Erwarteter Wert               | Default-Wert            | Weitere Informationen                                                             |
|-----------------------------|-------------------------------|-------------------------|-----------------------------------------------------------------------------------|
| `"execute"`                 | Boolean (`true` oder `false`) | -                       | Gibt an, ob die Dead-Code-Analyse ausgeführt werden soll.                         |
| `"completelyLoadLibraries"` | Boolean (`true` oder `false`) | `false`                 | Wenn `true`, werden Bibliotheken vollständig geladen, nicht nur als Interfaces.   |
| `"domains"`                 | Liste von Strings             | Zahlen von 1-13 außer 9 | Auswahl der abstrakten Interpretations-Domains (z.B. „TypeDomain“, „ValueDomain“) |

Die `domains`-Option erlaubt die Angabe, welche abstrakten Domains verwendet werden sollen. Alternativ kann die Auswahl der Domain auch interaktiv über die GUI erfolgen (empfohlen). Wird keine Domain angegeben, verwendet die Analyse automatisch die erste verfügbare.

> ⚠️ Hinweis: Auch diese Analyse kann über eine GUI bedient werden. Dort lassen sich Konfigurationen laden, Ergebnisse grafisch auswerten und verschiedene Domains vergleichen.

Die Ergebnisse der Analyse werden in einer JSON-Datei abgelegt. Die Datei enthält eine Auflistung der Methoden mit toten Instruktionen sowie eine graphische Zusammenfassung.

### Analyse 6: ArchitectureValidator (ex6)
Der ArchitectureValidator überprüft, ob ein Projekt eine zuvor definierte Architektur-Spezifikation einhält.
Dabei wird unter anderem analysiert, ob gewisse Klassen, Packages oder Jars auf andere zugreifen dürfen oder nicht.

Neben Methodenaufrufen und Feldzugriffen können auch andere Abhängigkeiten wie Vererbung, Interface-Implementierung oder Typverwendungen berücksichtigt werden (standardmäßig aktiviert).

Die Analyse für den ArchitectureValidator wird über architectureValidator in der Json-Config konfiguriert.
```json
"architectureValidator" : {
"execute" : false,
"onlyMethodAndFieldAccesses" : "DEFAULT",
"defaultRule" : "DEFAULT",
"rules" : "DEFAULT"
}
```

| Option                      | Erwarteter Wert                                  | Default-Wert | Weitere Informationen                                                            |
|-----------------------------|--------------------------------------------------|--------------|----------------------------------------------------------------------------------|
| `"execute"`                 | Boolean (`true` oder `false`)                    | -            | Bestimmt ob die Architekturvalidierung ausgeführt werden soll.                   |
| `"onlyMethodFieldAccesses"` | Boolean  (`true` oder `false`)                   | `false`      | Wenn `true`, werden nur Methodenaufrufe und Feldzugriffe analysiert.             |
| `"defaultRule"`             | `FORBIDDEN` oder `ALLOWED`                       | `FORBIDDEN`  | Legt fest, ob Zugriffe ohne spezifische Regel erlaubt oder verboten sein sollen. |
| `"rules"`                   | Liste von Regeln(`from`, `to`, `type`, `except`) | -            | Definiert erlaubte oder verbotene Zugriffe, ggf. mit rekursiven Ausnahmen.       |

Die Architektur-Spezifikation erfolgt über eine zusätzliche JSON-Datei (`specificationsFile`), deren Struktur der Aufgabenstellung aus Analyseaufgabe 6 entspricht.
Diese Datei wird automatisch berücksichtigt, wenn `"execute": true` gesetzt ist und `"defaultRule"` sowie `"rules"` definiert wurden.

> ⚠️ Hinweis: Der `resultsOutputPath`-Ordner wird auch hier verwendet, um den Analysebericht
> abzulegen. Der Bericht wird als `architecture-report.json` gespeichert. 

Beispielhafte Regel in der `spec.json` Datei:

```json
{
  "from": "main.jar",
  "to": "helper.jar",
  "type": "FORBIDDEN",
  "except": [
    {
      "from": "main.jar::MainClass",
      "to": "helper.jar::HelperClass",
      "type": "ALLOWED"
    }
  ]
}
```

Diese Regel verbietet grundsätzlich den Zugriff von `main.jar` auf `helper.jar`, erlaubt jedoch eine Ausnahme zwischen `MainClass` und `HelperClass`.

### Konfiguration Call-Graphen

Für Analysen, die einen Call-Graphen verwenden, werden immer dieselben 3 Optionen zur Verfügung gestellt.
Call-Graphen werden dafür verwendet, um erreichbare Methoden von Einstiegspunkten des Programms zu erkennen.

> Möchte man eine Analyse mit Call-Graphen verwenden, empfiehlt es sich insbesondere für größere Projekte, den RAM,
> der für die JVM zur Verfügung gestellt wird, zu erhöhen.

Die Optionen sind:
- `"callGraphAlgorithmName"`: Name des Call-Graph-Algorithmen der verwendet wird. Zur Verfügung stehen
  (aufsteigend sortiert nach Präzision): `"CHA"`, `"RTA"`, `"XTA"`, `"CTA"`, `"1-1-CFA"`. Je präziser der 
  Call-Graph-Algorithmus, desto weniger False-Positives liefert der Algorithmus (es werden also weniger Methoden falsch als erreichbar erkannt).
  - CHA ist im Allgemeinen nicht empfehlenswert, da dieser viel RAM benötigen kann und sehr ungenau ist. Meistens sogar langsamer als RTA.
  - RTA ist als Standardwahl gut geeignet, da dieser Algorithmus häufig am Schnellsten ist und die niedrigsten
    Leistungsanforderungen an den Computer hat.
  - 1-1-CFA ist der genauste Algorithmus, benötigt aber sehr viel Leistung und RAM. Außerdem kann es notwendig sein, die
    Java-Standardbibliothek (`rt.jar`) in den libraryJars der Config hinzuzufügen, damit bei der Generierung des Call-Graphen keine Fehler
    geworfen werden. Die Standardbibliothek kann man mit folgendem Tool aus seiner eigenen Java-Umgebung extrahiert werden: https://github.com/Storyyeller/jrt-extractor.
    (Hinweis: In Windows ist der Befehl `javac .\JRTExtractor.java ; java -ea JRTExtractor`)
- `entryPointsFinder`: Auswahl des Entry Point Finders von OPAL, der nach den Einstiegspunkten des Projekts sucht.
  Es sind 4 verschiedene Optionen verfügbar:
  - `"custom"`: Standardmäßig wird nichts als Einstiegspunkt betrachtet.
    Es sollten eigene Einstiegspunkte über `"customEntryPoints"` definiert werden!
  - `"application"`: Sucht nach main-Methoden im Projekt, über die das Projekt gestartet werden könnte.
  - `"applicationWithJre"`: Schließt zusätzlich zu `"application"` auch die Einstiegspunkte von der JRE
    (Java Runtime Environment) mit ein, sofern diese in der Jar enthalten ist.
  - `"library"`: Betrachtet das Projekt als Bibliothek. Es werden also (unter anderem) alle öffentlichen Methoden
    als Einstiegspunkte für den Call-Graphen betrachtet.
- `customEntryPoints`: Immer eine Liste von `{"className": <String>, "methods": <Liste von Strings>}`.
  Man gibt also eine Liste von Klassen ein, von denen mindestens eine Methode als Einstiegspunkt betrachtet werden soll.
  Über diese Option kann man eigene weitere Einstiegspunkte für das Projekt definieren. Das empfiehlt sich
  insbesondere, wenn man bei `entryPointsFinder` den Wert `"custom"` eingegeben hat. Man kann jedoch auch für jeden
  anderen Entry Points Finder weitere Einstiegspunkte definieren.



| Option          | Erwarteter Wert                | Default-Wert | Weitere Informationen |
|-----------------|--------------------------------|--------------|-----------------------|
| `"execute"`     | Boolean (`true` oder `false`)  | -            |                       |
| `""`            |                                |              |                       |
| `""`            |                                |              |                       |
| `""`            |                                |              |                       |
| `""`            |                                |              |                       |