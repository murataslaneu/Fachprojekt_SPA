# Fachprojekt Analyseaufgabe 3: Third Party Library Usage Analyzer

Dieses Projekt analysiert statisch die Nutzung von Third Party Libraries (TPLs) (sowohl direkte als auch transitive
Abhängigkeiten) in einem gegebenen Softwareprojekt. Dabei wird ermittelt, wie viele Methoden der eingebundenen
Bibliotheken tatsächlich verwendet werden. Die Analyse ist flexibel konfigurierbar und unterstützt verschiedene
Call-Graph-Algorithmen.

## Benutzung (über IntelliJ oder sbt)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex3`).
2. Starte sbt mit `sbt`.
3. Führe das Programm mit einer Konfigurationsdatei aus, z.B.:
   ```
   run -config=config.json
   run -config=some_custom_config.json -visual
   ```
4. Ergebnisse werden im Terminal abgegeben. Sofern in der Config angegeben, wird eine json-Datei erstellt, die die
     Ergebnisse speichert. Mit `-visual` wird automatisch eine grafische Darstellung erzeugt.

## Konfiguration
Die Konfiguration erfolgt über eine JSON-Datei. Hierbei werden folgende Angaben gemacht:
- `projectJars` (**notwendig**): Liste mit dem Pfad zur Projekt-JAR-Datei (bzw. Dateien,
     falls das Projekt aus mehreren jar-Dateien besteht).
- `tplJars` (**notwendig**): Liste mit Pfaden zu den Third-Party-Library-JARs, die analysiert werden sollen.
- `callGraphAlgorithm` (optional): Name des Algorithmus (z.B. `CHA`, `RTA`, `XTA`, `1-1-CFA`).
     Wenn nicht angegeben, wird `RTA` verwendet.
- `outputJson` (optional): Pfad bzw. Name der Ausgabedatei. Wenn nicht angegeben, wird keine Datei erzegut.
- `isLibraryProject` (optional): Boolean Flag, der angibt, ob das überreichte Projekt als normales Programm behandelt
     werden soll (Call-Graph nutzt Main-Methoden als Einstiegspunkt) oder als Bibliothek (alle öffentlichen Methoden als
     Einstiegspunkt). Standardmäßig `false`, Projekt wird als normales Programm analysiert.
- `countAllMethods` (optional): Boolean Flag, der angibt, ob *alle* Methoden gezählt werden sollen (also nicht nur public
     Methoden, sondern auch z.B. private, die immer noch indirekt aufgerufen werden könnten),  
     sowohl in der Gesamtzahl an Methoden in der Library, als auch der Anzahl genutzter Methoden. Standardmäßig `false`,
     nur direkt oder indirekt aufgerufene public Methoden werden gezählt.

### Beispiel
```json
{
  "projectJars": [
    "TestGuava.jar"
  ],
  "tplJars": [
    "lib/gson-2.13.1.jar",
    "lib/guava-33.4.8-jre.jar",
    "lib/junit-4.13.2.jar",
    "lib/logback-classic-1.5.18.jar",
    "lib/pdfbox-3.0.5.jar",
    "lib/scala-library-2.13.17-M1.jar"
  ],
  "callGraphAlgorithm": "rta",
  "countAllMethods": false,
  "isLibraryProject": false,
  "outputJson": "result.json"
}
```

### Konfiguration der Java Virtual Machine (JVM)
- Im Projekt ist eine Datei `.jvmopts` mitgegeben, über die die Einstellungen für die JVM angepasst werden können.
- Insbesondere für das Projekt relevant sind hierbei:
- `-Xmx<RAM-Größe>`: Gibt die maximale Größe des Heaps an, den die JVM zur Verfügung stellen darf. In der Datei wurde
     standardmäßig `10g` eingegeben, also können bis zu 10 Gigabyte RAM für die Analyse genutzt werden.
- `-Xms<RAM-Größe>`: Gibt die minimale Größe des Heaps an, den die JVM zur Verfügung stellt (dieser Platz wird also im
     Vorhinein reserviert). In der Datei wurde standardmäßig `4g` eingegeben, es stehen also mindestens 4 Gigabyte RAM
     im Heap zur Verfügung.
- `XX:<Garbage-Collector>`: Dient zur Angabe des genutzten Garbage-Collectors. Da hier mit großen Datenmengen (wegen dem
     Call-Graphen) gearbeitet wird, wird standardmäßig der G1 Garbage Collector verwendet (`-XX:+UseG1GC`), welcher für
     Heaps >4GB optimiert ist und schneller läuft als andere Collectoren.

## Optionen
- `-help`: Zeigt verfügbare Optionen im Terminal und beendet das Programm.
- `-config=<Dateiname>`: JSON-Konfigurationsdatei mit Projekt- und TPL-Informationen.
- `-visual`: Zeigt die grafische Visualisierung der Ergebnisse nach der Analyse.

> **Hinweis**: Der Pfad zur json-Datei für die Config sollte möglichst im selben Verzeichnis liegen.
> Das liegt daran, dass die AnalysisApplication von OPAL, die in diesem Projekt genutzt wird,
> schlecht mit Leerzeichen in den Optionen umgeht, was sich leider auch nicht so leicht von außen beheben lässt.

> Innerhalb der Config-json können jedoch beliebige Pfade angegeben werden!

## Ausgabemöglichkeiten der Analyse
- Textuelle Ausgabe über Konsole (geschieht automatisch nach jeder erfolgreichen Analyse)
- Ausgabe als json-Datei (muss in der config-json angegeben werden)
- Grafische Darstellung der Coverage mit XChart (bei Übergabe der Option `-visual`)