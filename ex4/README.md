# Fachprojekt Analyseaufgabe 4: Manipulation von Bytecode

Dieses Projekt besteht aus zwei Analysen (`CriticalMethodsRemover` für Aufgabe 4.1.1 und `TPLMethodsRemover`
für Aufgabe 4.1.2), die sich mit der Manipulation von Java Bytecode beschäftigen.

## Inhalt:
- [Benutzung der Analysen](#benutzung-über-intellij--sbt)
- [4.1.1: CriticalMethodsRemover](#411-criticalmethodsremover-bytecode-modifizieren)
  - [Umgesetzte Anforderungen](#umgesetzte-anforderungen)
  - [Optionen der Config-Datei](#optionen-der-config-datei)
  - [Durchgeführte Tests](#tests)
- [4.1.2: TPLMethodsRemover](#412-tplmethodsremover-bytecode-erzeugen)
  - [Optionen der Config-Datei](#optionen-der-config-datei-1)
  - [Durchgeführte Tests](#tests-1)
- [ScalaTest & sbt-scoverage](#scalatest--sbt-scoverage)

---

## Benutzung (über IntelliJ & sbt)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex4`).
2. Starte sbt mit `sbt`.
3. Führe das Programm mit einer vorher definierten JSON-Config-Datei aus, z.B.
   ```
   run -config=examples_4.1.1/exampleConfig.json
   run -config=examples_4.1.2/exampleConfig.json
   
   run -config=example_RealProject/4.1.1_config.json
   run -config=example_RealProject/4.1.2_config.json
   ```
   Die Analyse wird ausschließlich über die Config-Datei konfiguriert. Die Standard-Optionen der AnalysisApplication von
   OPAL werden daher ignoriert! *Optionen siehe im jeweiligen Abschnitt der Analyse.*
> **Achte bitte darauf, dass der Pfad zur Config-Datei <ins>keine Leerzeichen</ins> enthält.** Lege idealerweise die Config-Datei
> im selben Verzeichnis ab und stelle sicher, dass der Dateiname ebenfalls keine Leerzeichen enthält!
4. Weil dieses Projekt mehrere Main-Klassen enthält, muss noch ausgewählt werden, was ausgeführt werden soll.
   Wähle eines der Klassen aus (`CriticalMethodsRemover` (für 4.1.1) oder `TPLMethodsRemover` (für 4.1.2)), indem die
   jeweilige angezeigte Zahl eingegeben wird.
5. Die Analyse wird gestartet. Hierbei wird zuerst die Config eingelesen und einige Checks durchgeführt. Es wird auch
   überprüft, ob der Ausgabepfad für `outputClassFiles` (der Ausgabepfad für die generierten Class Files) leer ist.
   **Sollte der Ordner angegebener Ordner nicht leer sein, werden für die Analyse die Inhalte des Ordners
   <ins>gelöscht</ins>!** Die Analyse fragt in diesem Fall vorher nach, ob diese Aktion wirklich durchgeführt werden
   soll. **STELLE SICHER, DASS DER ANGEGEBENE PFAD KORREKT IST, UM UNGEWOLLTEN DATENVERLUST ZU VERMEIDEN!**
6. Die Analyse wird durchgeführt und der die Class Files im angegebenen Pfad ausgegeben.
   Bei der Analyse `CriticalMethodsRemover` wird ggf. auch eine json-Datei ausgegeben, sofern ein Pfad dafür angegeben
   wurde.

---

## 4.1.1: CriticalMethodsRemover (Bytecode modifizieren)

Analyse, die kritische Methodenaufrufe im Bytecode erkennt und ggf. entfernt. Die Änderungen können in der generierten
json-Datei überprüft werden.

> **Anmerkung:** Im Gegensatz zu Implementierung in ex2 wird hier kein Call-Graph verwendet, da es hier nicht als
> sinnvoll erachtet wurde. Ein Call-Graph wäre nur bei weiterführender Ausführbarkeitsanalyse erforderlich.
> Die Erkennung und Entfernung der Aufrufe erfolgt rein auf Bytecode-Ebene und ist durch eigene Unit-Tests validiert.

### Umgesetzte Anforderungen

* Konfigurierbare kritische Methoden (`criticalMethods`): Methoden können über eine JSON-Config spezifiziert werden.
* Ausgabe des Bytecodes der Methoden mit kritischen Aufrufen
* Entfernung der Aufrufe aus dem Bytecode (`INVOKE*`), ersetzt durch NOP um keine weiteren Inkonsistenzen im Bytecode zu
  verursachen
* Erlauben spezifischer kritischer Methodenaufrufe via `ignoreCalls` ("Whitelist")
* Speicherung der modifizierten `.class`-Dateien
* Verifikation der Änderungen (`bytecodeVerified`)
* Strukturierte JSON-Ausgabe (`AnalysisResult`) inklusive NOP-Patch-Log
* Automatische Unit-Tests mit `sbt test` decken vier verschiedene Testfälle ab
* Automatisierter Test, der das `.class`-File parst und prüft, ob NOPs korrekt gesetzt sind
* ~~Stack-Layout wird berücksichtigt~~

### Optionen der Config-Datei

#### Notwendig (muss immer in Config mit angegeben werden!)

- `projectJars`: Liste aus Strings. Die Strings enthalten die Pfade zu den jar-Dateien (bzw. der jar-Datei) des
  Projekts. Es wird von der Analyse überprüft, ob die Pfade existieren.

#### Optional (muss nicht in Config enthalten/festgelegt werden)
- `criticalMethods`: Liste von {"className": \<String\>, "methods": \<Liste von Strings\>}. Dient zur Angabe der
  kritischen/verbotenen Methoden **und sollte normalerweise immer mit angegeben werden!**
  Falls dies aber nicht getan wird, werden standardmäßig die Methdoen `getSecurityManger` und `setSecurityManager`
  von `java.lang.System` hinzugefügt.


- `ignoredCalls`: Liste von {"callerClass": \<String\>, "callerMethod": \<String\>,
  "targetClass": \<String\>, "targetMethod": \<String\>}. Kann als eine Art "Whitelist" angesehen werden, die in einzelnen
  Methode den Aufruf einer kritischen Methode erlaubt. `callerClass` und `callerMethod` ist der Name der Klasse und
  Methode, in der ein kritischer Methodenaufruf sein (könnte). `targetClass` und `targetMethod` ist der Name der Klasse
  und Methode der kritischen Methode, welche für `callerClass` und `callerMethod` erlaubt werden soll.


- `outputClassFiles`: String. Ausgabepfad für die modifizierten .class-Dateien. Muss ein (idealerweise leerer!) Ordner sein.
  Standardmäßig `"result"`, also ein Ordner mit dem Namen "result" im Verzeichnis, wo die Analyse
  ausgeführt wird. Ordner wird bei Bedarf erstellt, falls dieser noch nicht existiert. Falls der
  Ordner existiert und nicht leer ist, **werden die enthaltenen Daten vor der Analyse gelöscht**
  (wobei einem die Analyse einem davor noch warnt)!


- `outputJson`: String. Ausgabepfad einer json-Datei, die zusätzliche Informationen bezüglich der Analyse enthält.
  Dazu gehört z.B., in welcher Klasse welche Bytecode-Zeilen geändert wurden, oder ob mindestens ein kritischer
  Methodenaufruf ignoriert wurde. Wenn kein Pfad angegeben, wird auch keine json-Datei generiert.


- `libraryJars`: Liste aus Strings. Die Strings enthalten die Pfade zu den Bibliotheken/TPLs des Projekts.
  Die TPLs können direkte Abhängigkeiten des Projekts sein, als auch transitive Abhängigkeiten.
  Es kann ebenfalls die Java-Standardbibliothek (`rt.jar`) eingebunden werden.
- `completelyLoadLibraries`: Boolean. Flag, der angibt, ob für die Analyse die Bibliotheken, die über `libraryJars`
  übergeben werden, komplett in OPAL geladen werden sollen (`true`), oder nur als Interface geladen werden (`false`).
  Standardmäßig `false`.

> **Anmerkung:** Sowohl `libraryJars`, als auch `completelyLoadLibraries` haben vermutlich keinen Effekt auf das
> Ergebnis der Analyse, da hier kein Call-Graph (mehr) verwendet wird. Die Option, das einzustellen,
> wurde aber sicherheitshalber mit drin gelassen.

#### Config-Datei: Beispiel
```json
{
  "projectJars": [
    "examples_4.1.1/ExampleWithSecurityManager.jar"
  ],
  "criticalMethods": [
    {
      "className": "java.lang.System",
      "methods": ["getSecurityManager", "setSecurityManager"]
    }
  ],
  "ignoreCalls": [],
  "outputClassFiles": "results_4.1.1",
  "outputJson": "results_4.1.1/analysisResult.json"
}
```

---

### Output

**Beispiel Ergebnis-JSON:**

```json
[
  {
    "className": "Main",
    "methodName": "main",
    "removedCalls": [
      { "targetClass": "java.lang.System", "targetMethod": "setSecurityManager" }
    ],
    "status": "Modified and written to output/yget_nset/Main.class",
    "ignored": false,
    "bytecodeVerified": true,
    "nopReplacements": [
      [31, "INVOKESTATIC(java.lang.System{ void setSecurityManager(java.lang.SecurityManager) })"]
    ]
  }
]
```

**Beispiel Bytecode-Dump (JSON):**

```json
{
  "class": "Main",
  "method": "main",
  "bytecode": [
    { "pc": 0, "instruction": "ICONST_0" },
    
    { "pc": 31, "instruction": "NOP" },
    { "pc": 32, "instruction": "NOP" }
  ]
}
```

---

### Tests

Es wurde auf einem selbstgeschriebenen Beispielprojekt `ExampleWithSecurityManager.jar` getestet, ob die korrekten Instruktionen
erkannt wurden und ob der Bytecode erfolgreich modifiziert wird. Der Fokus lag hierbei auf die Methoden `getSecurityManager`
und `setSecurityManager` von `java.lang.System`. Es wurden 4 Fälle getestet:
- Fall 1: `getSecurityManager`-Aufruf erlaubt, `setSecurityManager` verboten und soll entfernt werden (`yget-nset`).
- Fall 2: `getSecurityManager`-Aufruf verboten und soll entfernt werden, `setSecurityManager` erlaubt (`nget-yset`).
- Fall 3: Sowohl `getSecurityManager` als auch `setSecurityManager` verboten und sollen entfernt werden (`nget-nset`).
- Fall 4: `getSecurityManager` und `setSecurityManager` kritische Methoden, Aufrufe werden aber über `ignoreCalls` der
  Config-Datei zugelassen (`yget-yset`).

---

## 4.1.2: TPLMethodsRemover (Bytecode erzeugen)
Diese Analyse sucht nach genutzten Methoden aus Third Party Libraries (TPLs) in einem Projekt (wie in ex3).
Der Unterschied ist aber, dass hier keine Usage Ratios berechnet werden, sondern ein **Dummy <ins>einer</ins> der
genutzten TPLs** erzeugt, der nur die genutzten class files und dessen genutzten Methoden enthält.
In diesem Dummy werden aber auch die Methoden-Körper entfernt (führen nichts aus und geben ggf.
nur null oder 0 zurück, je nach Ausgabetyp der Methode). Der Pfad, wo der Dummy ausgegeben werden soll, kann ebenfalls
festgelegt werden.

---

### Optionen der Config-Datei

#### Notwendig (muss immer in Config mit angegeben werden!)

- `projectJars`: Liste aus Strings. Die Strings enthalten die Pfade zu den jar-Dateien (bzw. der jar-Datei) des
                 Projekts. Es wird von der Analyse überprüft, ob die Pfade existieren.


- `libraryJars`: Liste aus Strings. Die Strings enthalten die Pfade zu den Bibliotheken/TPLs des Projekts. **Dazu
                 gehört auch die TPL, von der der Dummy generiert werden soll!** Die TPLs können direkte Abhängigkeiten
                 des Projekts sein, als auch transitive Abhängigkeiten. Die Java-Standardbibliothek (`rt.jar` kann
                 ebenfalls eingebunden werden). Je vollständiger die Liste, desto präziser ist der Call-Graph, aber auch
                 die notwendige Leistung für die Analyse steigt!


- `tplJar`: <ins>Ein</ins> String. Pfad zu der TPL, von der der Dummy generiert werden soll. **Tipp: Kopiere den
            <ins>exakt selben</ins> Pfad der TPL, der in libraryJars verwendet wurde, hier rein, damit erkannt wird,
            damit bei Lesen der Config keine Fehler auftreten!**

#### Optional (muss nicht in Config enthalten/festgelegt werden)

- `includeNonPublicMethods`: Boolean. Gibt an, ob der Dummy auch Methoden enthält, die nicht `public` sind (die z.B. 
                             indirekt über den Aufruf einer (in-)direkt genutzten `public` Methode der TPL aufgerufen 
                             wurden), oder eben nur die (in-)direkt genutzten `public` Methoden. Standardmäßig `true`
                             (Methoden, die nicht public sind, sind ebenfalls enthalten).

- `entryPointsFinder`: String "custom", "application", "applicationWithJre" oder "library". Bestimmt die Einstiegspunkte
                       für den Call-Graphen, um genutzte Methoden zu erkennen. Standardmäßig `"application"`
  - `"custom"`: Standardmäßig nichts als Einstiegspunkt festgelegt. Lege eigene Einstiegspunkte über Option
                'customEntryPoints' fest!
  - `"application"`: Beinhaltet alle main-Methoden des Projekts.
  - `"applicationWithJre`: Behaltet alle main-Methoden des Projekts, und auch die Einstiegspunkte der mitgelieferten
                           Java-Runtime-Environment (JRE), sofern im Projekt enthalten.
  - `"library"`: Für den Fall, dass das Projekt eine Bibliothek ist. Enthält alle von außen erreichbaren Methoden
                 (z.B. alle `public` Methoden des Projekts).


- `customEntryPoints`: Liste von {"className": \<String\>, "methods": \<Liste von Strings\>}. Ermöglicht es, (weitere)
                       Einstiegspunkte festzulegen. `"className"` ist der fully qualified name der Klasse, zu der die
                       Methodennamen gehören, und `"methods"` sind die zugehörigen Methoden<ins>namen</ins>. Rückgabetyp
                       und Parameter dürfen/können (leider) nicht mit angegeben werden. Standardmäßig eine leere Liste.


- `callGraphAlgorithm`: String "CHA", "RTA", "XTA" oder "1-1-CFA". Bestimmt den verwendeten Algorithmus für den
                        Call-Graph. Sortiert nach Präzision des Algorithmus. Standardmäßig `"RTA"`, weíl niedrigere
                        Leistungsanforderungen.


- `outputClassFiles`: String. Ausgabepfad für den Dummy der TPL. Muss ein (idealerweise leerer!) Ordner sein.
                      Standardmäßig `"result"`, also ein Ordner mit dem Namen "result" im Verzeichnis, wo die Analyse
                      ausgeführt wird. Ordner wird bei Bedarf erstellt, falls dieser noch nicht existiert. Falls der 
                      Ordner existiert und nicht leer ist, **werden die enthaltenen Daten vor der Analyse gelöscht**
                      (wobei einem die Analyse einem davor noch warnt)!

#### Config-Datei: Beispiel
```json
{
  "projectJars": [
    "examples_4.1.2/TestGuava.jar"
  ],
  "libraryJars": [
    "examples_4.1.2/guava-33.4.8-jre.jar"
  ],
  "tplJar": "examples_4.1.2/guava-33.4.8-jre.jar",
  "includeNonPublicMethods": true,
  "entryPointsFinder": "application",
  "customEntryPoints": [
    {
      "className": "com.something.SomeClass",
      "methods": [
        "someMethod"
      ]
    },
    {
      "className": "com.something.OtherClass",
      "methods": [
        "someMethod"
      ]
    }
  ],
  "callGraphAlgorithm": "XTA",
  "outputClassFiles": "results_4.1.2"
}
```
### Tests
Es wird getestet, ob das Objekt `create.FileIO` die json-Config-Datei korrekt einliest.

Außerdem wird auf einem selbstgeschriebenen Beispielprojekt und -bibliothek
(genannt `Test_ExampleProject` und `Test_ExampleLib`) getestet, ob die Class-Dateien an den erwarteten Orten gespeichert
werden und die korrekten Methoden enthält. Es werden zwei Fälle getestet:
- Fall 1: Nur öffentliche Methoden enthalten (direkter oder indirekter Aufruf)
- Fall 2: Sämtliche aufgerufene Methoden enthalten (auch nicht-öffentliche Methoden, die z.B. indirekt mit Aufruf einer
  anderen Methode mit aufgerufen werden).

---

## ScalaTest & sbt-scoverage
Dieses Projekt nutzt ScalaTest und enthält Tests, um die (grobe) Funktionsfähigkeit der Analysen zu testen.

### Ausführung (über IntelliJ & sbt)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex4`).
2. Führe im Terminal `sbt test` aus.
3. Tests werden ausgeführt. Nach Ausführung wird das Ergebnis der Tests ausgegeben. Es sollten alle Tests
   erfolgreich sein! :)

### Coverage
Ebenfalls wird die Test Coverage mithilfe von sbt-scoverage (einem Plugin für sbt) gemessen. Ausführung ist ähnlich zu
Tests, aber mit Befehl:
   ```
   sbt clean coverage test coverageReport
   ```
Dieser Befehl räumt erst das Projekt auf, dann wird der Test ausgeführt mit Messung der Coverage, und anschließend 
wird ein Coverage-Report erstellt.

Der Coverage-Report wird (unter anderem) als HTML-Format ausgegeben. Dieser kann sich dann beim
Öffnen der jeweiligen Datei im Browser graphisch angeguckt werden.