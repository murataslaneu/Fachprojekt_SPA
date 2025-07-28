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
  3. [TPLUsageAnalyzer (ex3)](#analyse-3-tplusageanalyzer-ex3)
  4. [CriticalMethodsRemover (ex4.1)](#analyse-4a-criticalmethodsremover-ex41)
  5. [TPLMethodsRemover (ex4.2)](#analyse-4b-tplmethodsremover-ex42)
  6. [DeadCodeDetector (ex5)](#analyse-5-deadcodedetector-ex5)
  7. [ArchitectureValidator (ex6)](#analyse-6-architecturevalidator-ex6)
  - [Konfiguration Call-Graphen](#konfiguration-call-graphen)
- [Tests](#tests)



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

Es ist immer nur die Übergabe eines einzelnen Parameters möglich. Die Parameter schließen sich von der Funktionsweise
her gegenseitig aus.

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
> wann die Analyse abgeschlossen wird bzw. was das Programm gemacht hat.



## JSON-Config + Ausgabe für die Analysen

Das Programm wird über eine einzelne JSON-Datei als Config gesteuert. In dieser kann man festlegen, welche jar-Dateien
geladen werden sollen, wo das Ergebnis ausgegeben werden soll, welche Analysen ausgeführt werden sollen
und die Analysen konfigurieren.

**Die allermeisten Optionen haben einen Default-Wert vorgegeben. Wird für die jeweilige Option `"DEFAULT"` eingegeben,
dann wird der Standardwert für diese Option verwendet. Ausgenommen davon sind `"projectJars"`, `"libraryJars"`, `"resultsOutputPath"`
und die jeweiligen `"execute"`-Optionen für einzelnen Analysen.**

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

> Es ist empfohlen, dass sich alle diese Optionen immer in der Config-Datei befinden, auch wenn man z.B. eine Analyse
> nicht ausführen möchte. Dafür sollten die `"execute"`-Flags verwendet werden.


---

### Grundoptionen

Die Grundoptionen werden für alle Analysen benötigt.

| Option                | Erwarteter Wert                                                               | Default-Wert | Weitere Informationen                                                                                                                      |
|-----------------------|-------------------------------------------------------------------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `"projectJars"`       | Liste von Strings (Dateipfade zu jar-Dateien)                                 | -            | jar-Dateien vom Projekt, das analysiert werden soll                                                                                        |
| `"libraryJars"`       | Liste von Strings (Dateipfade zu jar-Dateien)                                 | -            | jar-Dateien von den Bibliotheken, die das Projekt nutzt (für manche Analysen optional oder nicht benötigt, für andere wiederum notwendig.) |
| `"resultsOutputPath"` | String (Pfad zu Ordner, wo Ausgaben der Analyse hingeschrieben werden sollen) | -            | -                                                                                                                                          |

Die restlichen Optionen beziehen sich immer auf eine spezifische Analyse. Jede Analyse hat also eine eigene "Sub-Config".
Für jede Analyse ist außerdem immer eine Option `"execute"` vorhanden, die bestimmt, ob die Analyse ausgeführt werden soll oder nicht.


---

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


---

### Analyse 1: GodClassDetector (ex1)

Der GodClassDetector sucht mithilfe von Schwellenwerten für bestimmte Code-Parameter nach "God Classes"
(also Klassen, die viel Verantwortung übernehmen und damit schlecht zu warten sind). Beim Überschreiten von 3 der 4
Schwellenwerte wird eine Klasse als God Class betitelt.

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
| `"atfdThresh"` | Integer ≥ 0                        | 8            | Grenzwert für ATFD ("Access to Foreign Data"), höhere Werte sind schlechter. Klassen sollten *kleiner gleich* dem Grenzwert sein. |
| `"nofThresh"`  | Integer ≥ 0                        | 30           | Grenzwert für NOF ("Number of Fields"), höhere Werte sind schlechter. Klassen sollten *kleiner* sein als der Grenzwert.           |

#### Ausgabe
Die Analyse gibt in `1_GodClassDetector` eine json-Datei `results.json` aus, die die Ergebnisse der Analyse enthält.

Die json-Datei enthält die verwendete Config, und darunter alle im Projekt gefundenen God Classes.
- Bei jedem Eintrag ist der Fully Qualified Name der Klasse enthalten, aus welcher jar-Datei sie stammt, und welchen
  Wert die jeweiligen Code-Parameter haben.


---

### Analyse 2: CriticalMethodsDetector (ex2)

Der CriticalMethodsDetector sucht nach kritischen Methodenaufrufen
(also Aufrufe auf Methoden, die z.B. eventuell sicherheitsrelevant sein könnten).
Für die Analyse wird ein [Call-Graph](#konfiguration-call-graphen) verwendet.

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

| Option                     | Erwarteter Wert                                                                                                    | Default-Wert                                                                                    | Weitere Informationen                                                                                                                                                                                                                                                                                                                                             |
|----------------------------|--------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"execute"`                | Boolean (`true` oder `false`)                                                                                      | -                                                                                               | Bestimmt, ob der CriticalMethodsDetector ausgeführt werden soll oder nicht.                                                                                                                                                                                                                                                                                       |
| `"criticalMethods"`        | Liste von `{"className": <String>, "methods": <Liste von Strings> }`                                               | `[{"className": "java.lang.System", "methods": ["getSecurityManager", "setSecurityManager"] }]` | Gibt die kritischen Methoden an, gruppiert nach Klasse. Bei den Klassennamen ist der Fully Qualified Name notwendig, bei den Methoden nur der Methodenname (spezifizieren von z.B. Parameterliste leider nicht möglich).                                                                                                                                          |
| `"ignore"`                 | Liste von `{"callerClass": <String>, "callerMethod": <String>, "targetClass": <String>, "targetMethod": <String>}` | Leere Liste                                                                                     | Liste von Methoden, wo der Aufruf einer kritischen Methode gestattet wird. `"callerClass"` und `"callerMethod"` beziehen sich auf die aufrufende Klasse (Fully Qualified Name) und Methodenname, wo ein kritischer Aufruf erlaubt werden soll, und `"targetClass"` und `"targetMethod"` auf die kritische Methode der jeweiligen Klasse, die erlaubt werden soll. |
| `"callGraphAlgorithmName"` | String (`"CHA"`, `"RTA"`, `"XTA"`, `"CTA"` oder `"1-1-CFA"`)                                                       | `"RTA"`                                                                                         | Name des Call-Graph-Algorithmen, der für diese Analyse verwendet werden soll. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen).                                                                                                                                                                                                |
| `"entryPointsFinder"`      | String (`"custom"`,`"application"`,`"applicationWithJre"` oder `"library"`)                                        | `"application"`                                                                                 | Name des Entry Point Finders von OPAL, der für die Call-Graphen verwendet werden soll. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen).                                                                                                                                                                                       |
| `"customEntryPoints"`      | Liste von `{"className": <String>, "methods": <Liste von Strings> }`                                               | Leere Liste                                                                                     | Liste von Methoden, die als (zusätzliche) Einstiegspunkte für den Call-Graphen verwendet werden sollen. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen).                                                                                                                                                                      |

#### Ausgabe
Die Analyse gibt in `2_CriticalMethodsDetector` eine json-Datei `results.json` aus, die die Ergebnisse der Analyse enthält.

Die json-Datei enthält zum Einen die verwendete Config, und darunter alle im Projekt gefundenen kritischen Methodenaufrufe
inklusive der Gesamtzahl gefundener kritischer Methodenaufrufe.
- Bei jedem ist erkennbar, von wo die kritische Methode aufgerufen wurde und wie häufig ein Aufruf dieser
  Methode im Code enthalten ist.


---

### Analyse 3: TPLUsageAnalyzer (ex3)

Der TPLUsageAnalyzer ermittelt für jede gegebene library jar, wie hoch der Anteil genutzter Methoden ("Usage Ratio")
im Projekt ist. Für die Analyse wird ein [Call-Graph](#konfiguration-call-graphen) verwendet.

Die Analyse für den TPLUsageAnalyzer wird über `tplUsageAnalyzer` in der Json-Config
konfiguriert.

```json
"tplUsageAnalyzer" : {
  "execute" : false,
  "countAllMethods" : "DEFAULT",
  "callGraphAlgorithmName" : "DEFAULT",
  "entryPointsFinder" : "DEFAULT",
  "customEntryPoints" : "DEFAULT"
}
```
| Option                     | Erwarteter Wert                                                             | Default-Wert                                                     | Weitere Informationen                                                                                                                                                                        |
|----------------------------|-----------------------------------------------------------------------------|------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"execute"`                | Boolean (`true` oder `false`)                                               | -                                                                | Bestimmt, ob der TPLUsageAnalyzer ausgeführt werden soll oder nicht.                                                                                                                         |
| `"countAllMethods"`        | Boolean (`true` oder `false`)                                               | `false`                                                          | Flag, der angibt, ob sämtliche Methoden gezählt werden sollen (auch private Methoden, die evtl. indirekt aufgerufen wurden) (`true`), oder nur öffentliche Methoden (`false`).               |
| `"callGraphAlgorithmName"` | String (`"CHA"`, `"RTA"`, `"XTA"`, `"CTA"` oder `"1-1-CFA"`)                | Wert, der in criticalMethodsDetector eingegeben/eingesetzt wurde | Name des Call-Graph-Algorithmen, der für diese Analyse verwendet werden soll. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen).                           |
| `"entryPointsFinder"`      | String (`"custom"`,`"application"`,`"applicationWithJre"` oder `"library"`) | Wert, der in criticalMethodsDetector eingegeben/eingesetzt wurde | Name des Entry Point Finders von OPAL, der für die Call-Graphen verwendet werden soll. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen).                  |
| `"customEntryPoints"`      | Liste von `{"className": <String>, "methods": <Liste von Strings> }`        | Wert, der in criticalMethodsDetector eingegeben/eingesetzt wurde | Liste von Methoden, die als (zusätzliche) Einstiegspunkte für den Call-Graphen verwendet werden sollen. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen). |

#### Ausgabe
Es werden zwei Dateien im Ordner `3_TPLUsageAnalyzer` ausgegeben.
- Ein json-Report `results.json`, der die Ergebnisse der Analyse zusammenfasst.
  - Da drin ist enthalten, welches Projekt sich angeschaut wurde, den Nutzungsgrad aller geladenen library jars, welcher
    Call-Graph-Algorithmus verwendet wurde und die Laufzeiten für die einzelnen Analyseschritte.
- Eine Grafik `chart.png`, die den Nutzungsgrad jeder geladenen library jar noch einmal graphisch darstellt.


---

### Analyse 4a: CriticalMethodsRemover (ex4.1)

Der CriticalMethodsRemover sucht (ähnlich wie der CriticalMethodsDetector) nach kritischen Methodenaufrufen.
Bei dieser Analyse wird aber auch der Bytecode modifiziert, sodass die Aufrufe der kritischen Methoden aus dem Bytecode
entfernt werden (Invoke-Instruktionen werden durch NOP ersetzt).
Die kopierten, modifizieren .class-Dateien werden mit ausgegeben, unmodifizierte nicht.

Die Analyse für den CriticalMethodsRemover wird über `"criticalMethodsRemover"` in der Json-Config
konfiguriert.

```json
"criticalMethodsRemover" : {
  "execute" : false,
  "criticalMethods" : "DEFAULT",
  "ignore" : "DEFAULT"
}
```

| Option                  | Erwarteter Wert                                                                                                    | Default-Wert                                                     | Weitere Informationen                                                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------------|--------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"execute"`             | Boolean (`true` oder `false`)                                                                                      | -                                                                | Bestimmt, ob der CriticalMethodsRemover ausgeführt werden soll oder nicht.                                                                                                                                                                                                                                                                                                                                                   |
| `"criticalMethods"`     | Liste von `{"className": <String>, "methods": <Liste von Strings> }`                                               | Wert, der in criticalMethodsDetector eingegeben/eingesetzt wurde | Gibt die kritischen Methoden an, gruppiert nach Klasse. Bei den Klassennamen ist der Fully Qualified Name notwendig, bei den Methoden nur der Methodenname (spezifizieren von z.B. Parameterliste leider nicht möglich).                                                                                                                                                                                                     |
| `"ignore"`              | Liste von `{"callerClass": <String>, "callerMethod": <String>, "targetClass": <String>, "targetMethod": <String>}` | Wert, der in criticalMethodsDetector eingegeben/eingesetzt wurde | Liste von Methoden, wo der Aufruf einer kritischen Methode gestattet wird. `"callerClass"` und `"callerMethod"` beziehen sich auf die aufrufende Klasse (Fully Qualified Name) und Methodenname, wo ein kritischer Aufruf erlaubt werden soll, und `"targetClass"` und `"targetMethod"` auf die kritische Methode der jeweiligen Klasse, die erlaubt werden soll. Erlaubte/Ignorierte Methodenaufrufe werden nicht entfernt. |

#### Ausgabe
Ausgegeben wird in `4a_CriticalMethodsRemover`:
- Json-Report `results.json`, der alle modifizierten Methoden enthält mit:
  - Fully Qualified Name der Klasse
  - jar-Datei, woher die Klasse stammt
  - Entfernte Methodenaufrufe
  - Pfad zur .class-Datei
  - Ob die Methode ignoriert wurde (sollte in der Datei immer false sein)
  - Ob der Bytecode gültig ist (also von OPAL verifiziert)
  - Welche Instruktionen bei welchem PC (Program Counter) durch NOP ersetzt wurden.
- Textdatei `originalBytecode.txt`, der den ursprünglichen Bytecode jeder modifizierten Klasse enthält
  - Für jeden angegebenen Bytecode wird mit angegeben, von welcher Klasse und Methode dieser stammt,
  - und mithilfe von Kommentaren `// Replaced with NOP` gekennzeichnet, welche Instruktionen ersetzt wurden
- Ordner modifiedClasses, der die modifizierten .class-Dateien enthält
  - Ordnerstruktur wie in ursprünglichem Projekt
  - Nicht modifizierte Dateien sind nicht enthalten


---

### Analyse 4b: TPLMethodsRemover (ex4.2)

Der TPLMethodsRemover erstellt von einer library jar ein Dummy, der nur die vom Projekt genutzten Methoden enthält.
Die Methodenkörper der genutzten Methoden werden aber ebenfalls entfernt.
Für die Analyse wird ein [Call-Graph](#konfiguration-call-graphen) verwendet.

Die Analyse für den TPLMethodsRemover wird über `tplMethodsRemover` in der Json-Config
konfiguriert.

```json
"tplMethodsRemover" : {
  "execute" : false,
  "tplJar" : "DEFAULT",
  "includeNonPublicMethods" : "DEFAULT",
  "callGraphAlgorithmName" : "DEFAULT",
  "entryPointsFinder" : "DEFAULT",
  "customEntryPoints" : "DEFAULT"
}
```

| Option                      | Erwarteter Wert                                                                                | Default-Wert                                              | Weitere Informationen                                                                                                                                                                        |
|-----------------------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"execute"`                 | Boolean (`true` oder `false`)                                                                  | -                                                         | Bestimmt, ob der TPLMethodsRemover ausgeführt werden soll oder nicht.                                                                                                                        |
| `"tplJar"`                  | String (Kopie eines Pfades zu einer jar-Datei, die in `"libraryJars"` bereits angegeben wurde) | Zufällig ausgewählt aus `"libraryJars"`                   | Third Party Library Jar, von der der Dummy generiert werden soll.                                                                                                                            |
| `"includeNonPublicMethods"` | Boolean (`true` oder `false`)                                                                  | `true`                                                    | Wenn `true` werden sämtliche erreichbare Methoden im Dummy hinzugefügt, bei `false` nur die öffentlichen Methoden.                                                                           |
| `"callGraphAlgorithmName"`  | String (`"CHA"`, `"RTA"`, `"XTA"`, `"CTA"` oder `"1-1-CFA"`)                                   | Wert, der in tplUsageAnalyzer eingegeben/eingesetzt wurde | Name des Call-Graph-Algorithmen, der für diese Analyse verwendet werden soll. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen).                           |
| `"entryPointsFinder"`       | String (`"custom"`,`"application"`,`"applicationWithJre"` oder `"library"`)                    | Wert, der in tplUsageAnalyzer eingegeben/eingesetzt wurde | Name des Entry Point Finders von OPAL, der für die Call-Graphen verwendet werden soll. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen).                  |
| `"customEntryPoints"`       | Liste von `{"className": <String>, "methods": <Liste von Strings> }`                           | Wert, der in tplUsageAnalyzer eingegeben/eingesetzt wurde | Liste von Methoden, die als (zusätzliche) Einstiegspunkte für den Call-Graphen verwendet werden sollen. Mehr Informationen siehe beim Abschnitt [Call-Graphen](#konfiguration-call-graphen). |

#### Ausgabe
Ausgegeben wird in `4b_TPLMethodsRemover`:
- Json-Report `results.json`, der die verwendete Config und geschriebenen .class-Dateien enthält
  - Bei jeder geschriebenen .class Datei wird mit angegeben, wie viele Methoden von dieser Klasse verwendet wurden.
- Dummy der Third Party Library (angegeben bei `"tplJar"`) in `tplDummy`
  - Ordnerstruktur wie in ursprünglicher jar-Datei
  - Enthält nur die Klassen, von der mindestens eine Methode verwendet wird
  - Jede Klasse enthält nur die Methoden, die verwendet wurden
  - Der Methodenkörper für jede Methode wurde allerdings entfernt


---

### Analyse 5: DeadCodeDetector (ex5)
Der DeadCodeDetector analysiert den Bytecode eines Projekts und erkennt Instruktionen, die nie erreicht oder ausgeführt werden, also sogenannten „Dead Code“.
Die Analyse basiert auf abstrakter Interpretation.

Die Analyse für den DeadCodeDetector wird über `"deadCodeDetector"` in der Json-Config konfiguriert.

```json
"deadCodeDetector": {
  "execute": false,
  "completelyLoadLibraries": "DEFAULT",
  "domains": "DEFAULT"
}
```

| Option                      | Erwarteter Wert                                   | Default-Wert                  | Weitere Informationen                                                                    |
|-----------------------------|---------------------------------------------------|-------------------------------|------------------------------------------------------------------------------------------|
| `"execute"`                 | Boolean (`true` oder `false`)                     | -                             | Bestimmt, ob der DeadCodeDetector ausgeführt werden soll oder nicht.                     |
| `"completelyLoadLibraries"` | Boolean (`true` oder `false`)                     | `true`                        | Wenn `true`, werden Bibliotheken vollständig geladen, bei `false` nur als Interfaces.    |
| `"domains"`                 | Liste von Integern (zwischen 1 und 13, inklusive) | Alle Zahlen von 1-13, außer 9 | Auswahl der abstrakten Interpretations-Domains. Mehr Infos dazu bei [Domains](#domains). |

#### Domains

Jede Nummer, die bei `"domains"` angegeben wird, steht für eine jeweilige Domain, die von OPAL zur Verfügung gestellt wird:
1.  `org.opalj.ai.domain.l0.BaseDomain`
2.  `org.opalj.ai.domain.l0.PrimitiveTACAIDomain`
3.  `org.opalj.ai.domain.l1.DefaultDomain`
4.  `org.opalj.ai.domain.l1.DefaultDomainWithCFGAndDefUse`
5.  `org.opalj.ai.domain.l1.DefaultIntervalValuesDomain`
6.  `org.opalj.ai.domain.l1.DefaultReferenceValuesDomain`
7.  `org.opalj.ai.domain.l1.DefaultReferenceValuesDomainWithCFGAndDefUse`
8.  `org.opalj.ai.domain.l1.DefaultSetValuesDomain`
9.  `org.opalj.ai.domain.l2.DefaultDomain`
10. `org.opalj.ai.domain.l2.DefaultPerformInvocationsDomain`
11. `org.opalj.ai.domain.l2.DefaultPerformInvocationsDomainWithCFGAndDefUse`
12. `org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement`
13. `org.opalj.ai.fpcf.domain.PrimitiveTACAIDomainWithSignatureRefinement`

> Es ist auch erlaubt, bei `"domains"` den String `"ALL"` einzugeben. Dann werden alle Domains verwendet, ohne dass man
> selbst alle Domain-Nummern aufzählen muss.

> Bei `"DEFAULT"` werden alle Domains außer die Nummer 9 verwendet. Das liegt daran, dass diese besonders lange in der
> Ausführung braucht und ressourcenintensiv ist.

#### Ausgabe
Die Ausgaben der Analyse werden in `5_DeadCodeDetector` gespeichert.

Für jede einzelne ausgeführte Domain wird ein Report ausgegeben:
- Diese enthalten ein paar wenige Daten zur Config, wann die Analyse fertiggestellt wurde und wie lange die Analyse
  für die Berechnung brauchte.
- Außerdem sind alle Dead Instructions enthalten, gruppiert nach Methode.

Außerdem wird ein Report `multiDomainResult.json` ausgegeben, der die Ergebnisse der einzelnen
Analysen noch einmal zusammenfasst.
- Hier sind alle Dead Instructions zusammengefasst
- Bei jeder Dead Instruction wird auch dazu gespeichert, von welchen Domains diese jeweils gefunden wurde.


---

### Analyse 6: ArchitectureValidator (ex6)

Der ArchitectureValidator überprüft, ob ein Projekt eine zuvor definierte Architektur-Spezifikation einhält.
Dabei wird unter anderem analysiert, ob gewisse Klassen, Packages oder Jars auf andere zugreifen dürfen oder nicht.

Neben Methodenaufrufen und Feldzugriffen können auch andere Abhängigkeiten wie Vererbung, Interface-Implementierung oder Typverwendungen berücksichtigt werden (standardmäßig aktiviert).

Die Analyse für den ArchitectureValidator wird über `"architectureValidator"` in der Json-Config konfiguriert.
```json
"architectureValidator" : {
  "execute" : false,
  "onlyMethodAndFieldAccesses" : "DEFAULT",
  "defaultRule" : "DEFAULT",
  "rules" : "DEFAULT"
}
```

| Option                      | Erwarteter Wert                                                                                                                  | Default-Wert | Weitere Informationen                                                                                                                                                |
|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"execute"`                 | Boolean (`true` oder `false`)                                                                                                    | -            | Bestimmt, ob der ArchitectureValidator ausgeführt werden soll oder nicht.                                                                                            |
| `"onlyMethodFieldAccesses"` | Boolean (`true` oder `false`)                                                                                                    | `false`      | Wenn `true`, werden nur Methodenaufrufe und Feldzugriffe analysiert. Bei `false` werden auch weitere Abhängigkeiten betrachtet, z.B. Vererbung oder Nutzung des Typs. |
| `"defaultRule"`             | `"FORBIDDEN"` oder `"ALLOWED"`                                                                                                   | `ALLOWED`    | Legt fest, ob Zugriffe ohne spezifische Regel erlaubt oder verboten sein sollen.                                                                                     |
| `"rules"`                   | Liste von Regeln `{"from": <String>, "to": <String>, "type": <String>, "except": <Liste von Regeln (also rekursiv aufgebaut)> }` | Leere Liste  | Definiert weitere spezifische erlaubte oder verbotene Zugriffe, ggf. mit rekursiven Ausnahmen. Mehr Infos bei [Regeln](#regeln).                                     |

#### Regeln

Die Regeln sind rekursiv aufgebaut, es ist also möglich, Ausnahmen für Regeln beliebig tief zu verschachteln.

- Bei `"from"` und `"to"` kann jeweils eine Klasse, ein Package oder eine jar-Datei angegeben werden, wo dann eine Rege
der Nutzung von `"to"` in `"from"` festgelegt wird.
- Bei `"type"` kann man festlegen, ob eine Regel erlaubt `"ALLOWED"` oder verboten `"FORBIDDEN"` sein soll.
- Bei `"except"` kann wieder eine Liste von Regeln mit derselben Struktur angegeben werden, die Ausnahmen für diese
  Regel bilden sollen. **Diese Option hier ist optional und muss nicht unbedingt jedes Mal angegeben werden.**

**Beispiel-Regel:**
```json
{
  "from": "main.jar",
  "to": "helper.jar",
  "type": "FORBIDDEN",
  "except": [
    {
      "from": "com.example.main.SomeClass",
      "to": "helper.jar",
      "type": "ALLOWED"
    }
  ]
}
```
Diese Regel verbietet grundsätzlich den Zugriff von `main.jar` in `helper.jar`, erlaubt jedoch eine Ausnahme für `com.example.main.SomeClass`, dass `helper.jar` genutzt werden darf.

#### Ausgabe
Es wird in `6_ArchitectureValidator` ein Json-Report `architecture_report.json` gespeichert.
- In diesem Report sind einige Grundinformationen enthalten (z.B. welche Dateien analysiert wurden, wie lange die Berechnung gedauert hat, usw.)
- Anschließend folgen die gefundenen Violations ("Verstöße"): Für jede Violation wird angegeben, welche Klasse/Package/Jar auf welche andere
  Klasse/Package/Jar zugegriffen wurde und wie zugegriffen wurde.
- Am Ende der Datei werden noch die erzeugten Warnungen gespeichert: Diese weisen darauf hin, wenn etwas
  in der Config potenziell nicht stimmt bzw. in dieser Art nicht sinnvoll ist so zu definieren.


---

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


---

## Tests

Dieses Projekt wird ebenfalls mit ScalaTest getestet und die Test Coverage mithilfe von sbt-scoverage gemessen.
Die Tests aus den vorherigen Analysen, die noch nutzbar waren, wurden modifiziert, sodass diese auf der geänderten
Struktur dieses Projekts funktionieren. Außerdem sind die Tests und nun in der Lage, auch parallel ausgeführt werden
(was vorher nicht möglich war wegen geteiltem Zustand bei den Analyse-Objekten).

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