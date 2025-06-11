import com.typesafe.config.{Config, ConfigFactory}
import modify.JsonIO
import modify.data.{AnalysisConfig, AnalysisResult}
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext
import org.opalj.br.instructions._
import org.opalj.br.NoExceptionHandlers
import org.opalj.bc.Assembler
import org.opalj.ba.toDA

import java.nio.file.{Files, Paths}
import org.opalj.br.instructions.Instruction
import org.opalj.br.Code
import org.opalj.br.Method

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}

// Application that implements exercise 4.1.1

/**
 * Application that searches for critical method calls (like in ex2) AND edits the bytecode to replaces these with
 * methods that return null. The program outputs new .class files where the edits are visible.
 */
object CriticalMethodsRemover extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Results string to print after analysis */
  private val analysisResults = new StringBuilder()

  /** Object holding the configuration for the analysis */
  private var config: Option[AnalysisConfig] = None

  /** Flag set during analysis to indicate if at least one found method call has been ignored. */
  private var ignoredAtLeastOneCall: Boolean = false

  override def title: String = "Critical methods remover"

  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    /** Internal method to retrieve the value from the given parameter */
    def getValue(arg: String): String = arg.substring(arg.indexOf("=") + 1).strip()

    val issues: ListBuffer[String] = ListBuffer()
    var configMissing = true

    parameters.foreach {
      case arg if arg.startsWith("-config=") =>
        val configPath = getValue(arg)
        try {
          configMissing = false
          config = Some(JsonIO.readConfig(configPath))
        }
        catch {
          case ex: Exception => issues += s"Config file at path $configPath could not be parsed correctly: $ex"
        }
      case unknown => issues += s"Unknown parameter: $unknown"
    }

    if (config.isEmpty && configMissing) {
      issues += "-config: Missing. Please provide a (correctly formatted) config file with -config=config.json"
    }

    issues
  }

  override def analysisSpecificParametersDescription: String = """
      | ========================= CUSTOM PARAMETERS =========================
      | [-config=<config.json> (REQUIRED. Configuration used for analysis. See template for schema.)]
      |
      | This analysis uses a custom config json to configure the project.
      | OTHER OPTIONS BESIDES -help ARE IGNORED. PLEASE CONFIGURE PROJECT
      | AND LIBRARY JARS VIA THE CONFIG JSON.
      | """.stripMargin

  override def setupProject(cpFiles: Iterable[File], libcpFiles: Iterable[File], completelyLoadLibraries: Boolean, configuredConfig: Config)(implicit initialLogContext: LogContext): Project[URL] = {
    val overridesMap: mutable.Map[String, Object] = mutable.Map(
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis" -> config.get.entryPointsFinder._1,
      "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis" -> config.get.entryPointsFinder._2
    )

    if (config.get.customEntryPoints.nonEmpty) {
      val customEntryPoints = config.get.customEntryPoints.flatMap { eps =>
        eps.methods.map { epMethod =>
          Map("declaringClass" -> eps.className, "name" -> epMethod).asJava
        }
      }.asJava
      overridesMap.put("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints", customEntryPoints)
    }

    val newConfig = ConfigFactory.parseMap(overridesMap.asJava).withFallback(configuredConfig).resolve()

    super.setupProject(config.get.projectJars, config.get.libraryJars, config.get.completelyLoadLibraries, newConfig)
  }

  override def analyze(
                        project: Project[URL],
                        parameters: Seq[String],
                        initProgressManagement: Int => ProgressManagement
                      ): BasicReport = {

    // Print loaded configuration for debugging
    println("Loaded the following config:")
    println(s"  - projectJars: ${config.get.projectJars}")
    println(s"  - libraryJars: ${config.get.libraryJars}")
    println(s"  - completelyLoadLibraries: ${config.get.completelyLoadLibraries}")
    println(s"  - criticalMethods: ${config.get.criticalMethods}")
    println(s"  - ignoreCalls: ${config.get.ignoreCalls}")
    println(s"  - entryPointsFinder: ${config.get.entryPointsFinder}")
    println(s"  - customEntryPoints: ${config.get.customEntryPoints}")
    println(s"  - callGraphAlgorithm: ${config.get.callGraphAlgorithm}")
    println(s"  - outputClassFiles: ${config.get.outputClassFiles}")
    println(s"  - outputJson: ${config.get.outputJson}")

    // Convert the critical methods list into a flat list of (className, methodName) tuples
    val criticalMethods: List[(String, String)] =
      config.get.criticalMethods.flatMap(sm =>
        sm.methods.map(methodName => (sm.className, methodName))
      )

    val outputDir = config.get.outputClassFiles
    val detectedCalls = scala.collection.mutable.Map.empty[Method, List[String]]

    // Analyze all methods in the project
    project.allProjectClassFiles.foreach { cf =>
      cf.methods.foreach { m =>
        val foundInvokes = findCriticalInvokes(m, criticalMethods)

        // If critical calls are found, proceed with modification
        if (foundInvokes.nonEmpty && m.body.isDefined) {
          println(s"Found critical call(s) in: ${cf.thisType.toJava}.${m.name}${m.descriptor.toJava}")
          printMethodBytecode(m)

          // Save found method names (invocations) for result reporting
          detectedCalls += (m -> foundInvokes.collect {
            case (_, instr: MethodInvocationInstruction) => instr.name
          }.toList)

          // Modify the method body to remove critical calls
          val oldCode = m.body.get
          val newInstructions = removeCriticalInvokes(oldCode, criticalMethods)

          val updatedMethod = m.copy(
            body = Some(oldCode.copy(instructions = newInstructions, exceptionHandlers = NoExceptionHandlers))
          )

          // Create new ClassFile with updated method
          val newClassFile = cf.copy(
            methods = cf.methods.map {
              case `m` => updatedMethod
              case other => other.copy()
            }
          )

          // Assemble new ClassFile and write to disk
          val classBytes: Array[Byte] = Assembler(toDA(newClassFile))
          val outputFile = Paths.get(outputDir, s"${cf.thisType.toJava.replace('.', '/')}.class")

          Files.createDirectories(outputFile.getParent)
          Files.write(outputFile, classBytes)
          println(s"Written modified class to: $outputFile")
        }
      }
    }

    // Build result list for JSON
    val resultList = detectedCalls.map {
      case (method, calls) =>
        AnalysisResult(
          className = method.classFile.thisType.toJava,
          methodName = method.name,
          removedCalls = calls
        )
    }.toList

    // Write analysis results to JSON if configured
    config.foreach { conf =>
      conf.outputJson.foreach { path =>
        modify.JsonIO.writeResult(resultList, path)
        println(s"Result JSON written to $path")
      }
    }

    BasicReport("Critical methods removed and class files updated.")
  }

  /**
   * Scans the bytecode of a method and finds all invocations to critical methods.
   *
   * @param method The method to be scanned.
   * @param criticalMethods A list of (className, methodName) tuples marking critical calls.
   * @return A sequence of (instructionIndex, instruction) pairs for matching critical calls.
   */
  private def findCriticalInvokes(
                                   method: Method,
                                   criticalMethods: List[(String, String)]
                                 ): Seq[(Int, Instruction)] = {

    val result: Option[Seq[(Int, Instruction)]] = method.body.map { code =>
      code.instructions.zipWithIndex.collect {
        case (instr: MethodInvocationInstruction, idx)
          if criticalMethods.contains((instr.declaringClass.toJava, instr.name)) =>
          (idx, instr)
      }
    }

    result.getOrElse(Seq.empty[(Int, Instruction)])
  }

  /**
   * Utility function to print the bytecode instructions of a method.
   *
   * @param method The method whose bytecode will be printed.
   */
  private def printMethodBytecode(method: Method): Unit = {
    println(s"Bytecode for method: ${method.name}${method.descriptor.toJava}")
    method.body.foreach { code =>
      code.instructions.zipWithIndex.foreach {
        case (instr, idx) =>
          println(f"$idx%03d: $instr")
      }
    }
  }

  /**
   * Removes critical method calls from a method's bytecode.
   *
   * @param code The original method code.
   * @param criticalMethods List of (className, methodName) identifying critical calls.
   * @return A new instruction array with critical method invocations removed.
   */
  private def removeCriticalInvokes(code: Code, criticalMethods: List[(String, String)]): Array[Instruction] = {
    val filtered = code.instructions.filterNot {
      case i: MethodInvocationInstruction =>
        criticalMethods.contains((i.declaringClass.toJava, i.name))
      case _ => false
    }.filter(_ != null) // avoid nulls for assembler
    filtered
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
