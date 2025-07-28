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
  - [Struktur der Analyse-Ausgabe]()
  1. GodClassDetector (ex1)
  2. CriticalMethodsDetector (ex2)
  3. TPLUsageAnalyzer (ex3)
  4. CriticalMethodsRemover (ex4.1)
  5. TPLMethodsRemover (ex4.2)
  6. DeadCodeDetector (ex5)
  7. ArchitectureValidator (ex6)
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

| Option         | Erwarteter Wert                  | Default-Wert | Weitere Informationen                                                                                                             |
|----------------|----------------------------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `"execute"`    | Boolean (`true` oder `false`)    | -            | Bestimmt, ob der GodClassDetector ausgeführt werden soll oder nicht.                                                              |
| `"wmcThresh"`  | Integer >= 0                     | 100          | Grenzwert für WMC ("Weighted Methods per Class"), höhere Werte sind schlechter. Klassen sollten *kleiner* sein als der Grenzwert. |
| `"tccThresh"`  | Dezimalzahl zwischen 0.0 und 1.0 | 0.33         | Grenzwert für TCC ("Tight Class Cohesion"), niedrigere Werte sind schlechter. Klassen sollten *größer gleich* dem Grenzwert sein. |
| `"atfdThresh"` | Integer >= 0                     | 8            | Grenzwert für ATFD ("Access to Foreign Data", höhere Werte sind schlechter. Klassen sollten *kleiner gleich* dem Grenzwert sein.  |
| `"nofThresh"`  | Integer >= 0                     | 30           | Grenzwert für NOF ("Number of Fields"), höhere Werte sind schlechter. Klassen sollten *kleiner* sein als der Grenzwert.           |

### Analyse 2: CriticalMethodsDetector (ex2)




| Option | Erwarteter Wert | Default-Wert | Weitere Informationen |
|--------|-----------------|--------------|-----------------------|
| `""`   |                 |              |                       |
| `""`   |                 |              |                       |
| `""`   |                 |              |                       |
| `""`   |                 |              |                       |
| `""`   |                 |              |                       |