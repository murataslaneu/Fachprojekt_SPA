import com.typesafe.config.{Config, ConfigFactory}
import modify.JsonIO
import modify.data.{AnalysisConfig, AnalysisResult, IgnoredCall, RemovedCall}
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

  /** Object holding the configuration for the analysis */
  private var config: Option[AnalysisConfig] = None

  private val resultsBuffer = ListBuffer.empty[AnalysisResult]

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

  /** Main analysis logic */
  override def analyze(
                        project: Project[URL],
                        parameters: Seq[String],
                        initProgressManagement: Int => ProgressManagement
                      ): BasicReport = {

    // Print loaded configuration for debugging
    println("==================== Loaded Configuration ====================")
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
    println("===============================================================\n")

    // Convert the critical methods list into a flat list of (className, methodName) tuples
    val criticalMethods: List[(String, String)] =
      config.get.criticalMethods.flatMap(sm =>
        sm.methods.map(methodName => (sm.className, methodName))
      )

    val outputDir = config.get.outputClassFiles

    // Analyze all methods in the project
    project.allProjectClassFiles.foreach { cf =>
      cf.methods.foreach { m =>
        val foundInvokes = findCriticalInvokes(m, criticalMethods)

        // If critical calls are found, proceed with modification
        if (foundInvokes.nonEmpty && m.body.isDefined) {
          //println(s"Found critical call(s) in: ${cf.thisType.toJava}.${m.name}${m.descriptor.toJava}")
          println(s"\n>>> Found critical call(s) in method: ${cf.thisType.toJava}.${m.name}${m.descriptor.toJava}")
          println(f"--- Bytecode for: ${m.name}${m.descriptor.toJava} ---")

          // Modify the method body to remove critical calls
          val oldCode = m.body.get
          val newInstructions = replaceCriticalInvokesWithNOP(
            oldCode,
            criticalMethods,
            config.get.ignoreCalls,
            cf.thisType.toJava,
            m.name
          )

          println("--- Modified bytecode ---")
          printCodeInstructions(newInstructions)

          // Create updated method and class
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

          // After class writing
          println(s"[OK] Modified class written to: $outputFile")

          // Track removed calls
          val removed = foundInvokes.collect {
            case (_, instr: MethodInvocationInstruction)
              if !config.get.ignoreCalls.exists(ic =>
                ic.callerClass == cf.thisType.toJava &&
                  ic.callerMethod == m.name &&
                  ic.targetClass == instr.declaringClass.toJava &&
                  ic.targetMethod == instr.name
              ) =>
              RemovedCall(instr.declaringClass.toJava, instr.name)
          }.toList

          // Determine if any matched calls were ignored
          val wasIgnored = foundInvokes.exists {
            case (_, instr: MethodInvocationInstruction) =>
              config.get.ignoreCalls.exists(ic =>
                ic.callerClass == cf.thisType.toJava &&
                  ic.callerMethod == m.name &&
                  ic.targetClass == instr.declaringClass.toJava &&
                  ic.targetMethod == instr.name
              )
          }

          // Verify if bytecode is clean from removed calls
          val bytecodeVerified = if (removed.isEmpty && wasIgnored) {
            true
          } else {
            !removed.exists { call =>
              updatedMethod.body.exists { code =>
                code.instructions.exists {
                  case i: MethodInvocationInstruction =>
                    i.declaringClass.toJava == call.targetClass &&
                      i.name == call.targetMethod
                  case _ => false
                }
              }
            }
          }

          // Collect analysis result
          val result = AnalysisResult(
            className = cf.thisType.toJava,
            methodName = m.name,
            removedCalls = removed,
            status = s"Modified and written to $outputDir/${cf.thisType.toJava.replace('.', '/')}.class",
            ignored = wasIgnored,
            bytecodeVerified = bytecodeVerified
          )

          resultsBuffer += result
        }
      }
    }

    // Write analysis results to JSON if configured
    val resultList = resultsBuffer.toList

    config.foreach { conf =>
      conf.outputJson.foreach { path =>
        modify.JsonIO.writeResult(resultList, path)

        // After JSON writing
        println(s"[OK] Result JSON written to: $path")
      }
    }

    resultsBuffer.foreach { result =>
      // After verification
      println(s"[OK] ${result.className}.${result.methodName} -> bytecodeVerified = ${result.bytecodeVerified}")
    }

    BasicReport(">>> Bytecode modification complete. Critical methods removed.")
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
   * Removes all critical method invocations from the bytecode if not ignored.
   *
   * @param code The original bytecode of the method.
   * @param criticalMethods A list of (className, methodName) tuples representing critical method calls.
   * @param ignoreCalls A list of IgnoredCall objects representing whitelisted calls that should not be removed.
   * @param className The name of the current class being analyzed (used for matching ignoreCalls).
   * @param methodName The name of the current method being analyzed (used for matching ignoreCalls).
   * @return A filtered array of instructions without the non-ignored critical method invocations.
   */
  private def removeCriticalInvokes(
                                     code: Code,
                                     criticalMethods: List[(String, String)],
                                     ignoreCalls: List[IgnoredCall],
                                     className: String,
                                     methodName: String
                                   ): Array[Instruction] = {
    val filtered = code.instructions.filterNot {
      case i: MethodInvocationInstruction =>
        val call = (i.declaringClass.toJava, i.name)

        val shouldIgnore = ignoreCalls.exists { ic =>
          ic.callerClass == className &&
            ic.callerMethod == methodName &&
            ic.targetClass == call._1 &&
            ic.targetMethod == call._2
        }

        // Debug for each call check (inside removeCriticalInvokes)
        println(f"[?] Should ignore: $className.$methodName -> ${call._1}.${call._2} = $shouldIgnore")

        // Show active critical methods
        println(s"[!] Active criticalMethods: " + criticalMethods.map { case (c, m) => s"$c#$m" }.mkString(", "))



        criticalMethods.contains(call) && !shouldIgnore

      case _ => false
    }

    filtered.filter(_ != null) // avoid nulls for assembler
  }

  // Replace critical method calls with NOPs to preserve the stack and instruction layout
  private def printCodeInstructions(instructions: Array[Instruction]): Unit = {
    instructions.zipWithIndex.foreach {
      case (instr, idx) =>
        println(f"$idx%03d: $instr")
    }
  }

  /**
   * Replaces all critical method invocation instructions with NOP instructions,
   * unless the invocation is listed in the ignoreCalls whitelist.
   *
   * This approach avoids deleting instructions outright, which may cause issues
   * with bytecode verification or control flow consistency (e.g., jump offsets).
   *
   * @param code The original bytecode of the method
   * @param criticalMethods A list of (className, methodName) tuples defining critical method calls
   * @param ignoreCalls A list of IgnoredCall entries representing calls that should not be removed
   * @param className The name of the current class (used to match ignoreCalls)
   * @param methodName The name of the current method (used to match ignoreCalls)
   * @return A modified instruction array where matched INVOKEs are replaced with NOPs
   */
  private def replaceCriticalInvokesWithNOP(
                                             code: Code,
                                             criticalMethods: List[(String, String)],
                                             ignoreCalls: List[IgnoredCall],
                                             className: String,
                                             methodName: String
                                           ): Array[Instruction] = {

    val modifiedInstructions = code.instructions.zipWithIndex.map {
      case (instr: MethodInvocationInstruction, idx) =>
        val call = (instr.declaringClass.toJava, instr.name)

        val shouldIgnore = ignoreCalls.exists { ic =>
          ic.callerClass == className &&
            ic.callerMethod == methodName &&
            ic.targetClass == call._1 &&
            ic.targetMethod == call._2
        }

        val isCritical = criticalMethods.contains(call)

        // Debug for each call check
        println(f"[?] Should ignore: $className.$methodName -> ${call._1}.${call._2} = $shouldIgnore")

        // Show active critical methods
        println(s"[!] Active criticalMethods: " + criticalMethods.map { case (c, m) => s"$c#$m" }.mkString(", "))

        // Final report
        println(">>> Bytecode modification complete. Critical methods replaced with NOP.")

        if (isCritical && !shouldIgnore) {
          println(s"[REPLACE] Replacing ${call._1}.${call._2} at index $idx with NOP")
          NOP
        } else {
          instr
        }

      case (instr, _) if instr != null => instr
      case (null, idx) =>
        println(s"[WARNING] Found null instruction at index $idx, replacing with NOP")
        NOP
    }

    modifiedInstructions.filter(_ != null)
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
