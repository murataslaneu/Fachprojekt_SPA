import com.typesafe.config.Config
import modify.FileIO
import modify.data.{AnalysisConfig, AnalysisResult, IgnoredCall, RemovedCall}
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext
import org.opalj.br.instructions._
import org.opalj.br.{ClassFile, Code, Method, NoExceptionHandlers}
import org.opalj.bc.Assembler
import org.opalj.ba.toDA

import java.nio.file.{Files, Path, Paths}
import org.opalj.br.instructions.Instruction

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

// Application that implements exercise 4.1.1

/**
 * Application that searches for critical method calls (like in ex2) AND edits the bytecode to replaces these with
 * methods that return null. The program outputs new .class files where the edits are visible.
 */
object CriticalMethodsRemover extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Object holding the configuration for the analysis */
  var config: Option[AnalysisConfig] = None

  private val resultsBuffer = ListBuffer.empty[AnalysisResult]

  private var lastNOPReplacements: Option[List[(Int, String)]] = None

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
          config = Some(FileIO.readConfig(configPath))
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
    // Emptying the resultsBuffer is needed for the tests to succeed
    resultsBuffer.clear()
    super.setupProject(config.get.projectJars, config.get.libraryJars, config.get.completelyLoadLibraries, configuredConfig)
  }

  /** Main analysis logic */
  override def analyze(
                        project: Project[URL],
                        parameters: Seq[String],
                        initProgressManagement: Int => ProgressManagement
                      ): BasicReport = {

    // Print loaded config
    println("==================== Loaded Configuration ====================")
    println(s"* projectJars: ${if (config.get.projectJars.isEmpty) "[None]" else ""}")
    config.get.projectJars.foreach {file => println(s"  - $file")}
    println(s"* libraryJars: ${if (config.get.libraryJars.isEmpty) "[None]" else ""}")
    config.get.libraryJars.foreach {file => println(s"  - $file")}
    println(s"* completelyLoadLibraries: ${config.get.completelyLoadLibraries}")
    println(s"* criticalMethods: ${config.get.criticalMethods}")
    config.get.criticalMethods.foreach { crit =>
      if (crit.methods.nonEmpty) {
        println(s"  - Class ${crit.className.replace('/', '.')}:")
        crit.methods.foreach { method => println(s"    -- $method")}
      }
    }
    println(s"* ignoreCalls: ${if (config.get.ignoreCalls.isEmpty) "[None]" else ""}")
    config.get.ignoreCalls.foreach { ignoredCall =>
      println(s"  - Caller ${ignoredCall.callerClass} in ${ignoredCall.callerMethod} -> Target ${ignoredCall.targetClass} with ${ignoredCall.targetMethod}")
    }
    println(s"* outputClassFiles: ${config.get.outputClassFiles}")
    val outputJson = config.get.outputJson
    println(s"* outputJson: ${if (outputJson.isDefined) outputJson.get else "[None]"}")
    println("===============================================================\n")

    // Convert the critical methods list into a flat list of (className, methodName) tuples
    val criticalMethods: List[(String, String)] =
      config.get.criticalMethods.flatMap(sm =>
        sm.methods.map(methodName => (sm.className, methodName))
      )

    val outputDir = config.get.outputClassFiles

    var replacedInvalidCharacter = false
    // Analyze all methods in the project
    project.allProjectClassFiles.foreach { cf =>
      cf.methods.foreach { m =>
        val foundInvokes = findCriticalInvokes(m, criticalMethods)

        // If critical calls are found, proceed with modification
        if (foundInvokes.nonEmpty && m.body.isDefined) {
          printOriginalBytecode(cf, m)
          println(s"\n>>> Found critical call(s) in method: ${cf.thisType.toJava}.${m.name}${m.descriptor.toJava}")

          // Modify the method body to remove critical calls
          val oldCode = m.body.get
          val newInstructions = replaceCriticalInvokesWithNOP(
            oldCode,
            criticalMethods,
            config.get.ignoreCalls,
            cf.thisType.toJava,
            m.name
          )

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
          val newClassBytes: Array[Byte] = Assembler(toDA(newClassFile))
          // Windows does not accept some characters that may be contained in the class file names
          // Thus, replace them with similar-looking characters that are allowed
          val sanitizedClassFileName = newClassFile.fqn.map { c =>
            c match {
              case ':' =>
                replacedInvalidCharacter = true
                'ː' // Unicode character U+02D0
              case '<' =>
                replacedInvalidCharacter = true
                '‹' // Unicode character U+2039
              case '>' =>
                replacedInvalidCharacter = true
                '›' // Unicode character U+203A
              case other => other
            }
          }
          val classFilePath = Path.of(s"$outputDir/$sanitizedClassFileName.class")
          Files.createDirectories(classFilePath.getParent)
          Files.write(classFilePath, newClassBytes)

          // After class writing
          println(s"[OK] Modified class written to: $classFilePath")

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
            bytecodeVerified = bytecodeVerified,
            nopReplacements = lastNOPReplacements
          )

          resultsBuffer += result
        }
      }
    }

    // Write analysis results to JSON if configured
    val resultList = resultsBuffer.toList

    config.foreach { conf =>
      conf.outputJson.foreach { path =>
        modify.FileIO.writeResult(resultList, path)

        // After JSON writing
        println(s"[OK] Result JSON written to: $path")
      }
    }

    resultsBuffer.foreach { result =>
      // After verification
      println(s"[OK] ${result.className}.${result.methodName} -> bytecodeVerified = ${result.bytecodeVerified}")
    }

    if (replacedInvalidCharacter) {
      println(s"${Console.BLUE}Note: At least one of the class files contained a character not allowed in Windows file names (':', '<' or '>').${Console.RESET}")
      println(s"${Console.BLUE}      Such characters have been replaced with similar-looking Unicode characters (U+02D0, U+2039 or U+203A).${Console.RESET}\n")
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

  // Prints the original bytecode instructions of a method before modification
  private def printOriginalBytecode(classFile: ClassFile, method: Method): Unit = {
    println(s"====== Original Bytecode of ${classFile.thisType.toJava}.${method.name} ======")
    method.body.foreach { code =>
      code.instructions.zipWithIndex.foreach {
        case (instr, idx) =>
          val display = if (instr == null) "null" else instr.toString
          println(f"$idx%03d: $display")
      }
    }
    println("=" * 40)
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

    val replacedWithNOP = mutable.Buffer[(Int, String)]()

    val filtered = code.instructions.zipWithIndex.map {
      case (i: MethodInvocationInstruction, idx) =>
        val call = (i.declaringClass.toJava, i.name)

        val shouldIgnore = ignoreCalls.exists { ic =>
          ic.callerClass == className &&
            ic.callerMethod == methodName &&
            ic.targetClass == call._1 &&
            ic.targetMethod == call._2
        }

        //DEBUG for Ignore and CriticalMethods
        var debugLogCounter: Int = 0
        val debugLogLimit: Int = 0

        if (debugLogCounter < debugLogLimit) {
          println(f"[?] Should ignore: $className.$methodName -> ${call._1}.${call._2} = $shouldIgnore")
          println(s"[!] Active criticalMethods: " + criticalMethods.map { case (c, m) => s"$c#$m" }.mkString(", "))
          debugLogCounter += 1
        }

        if (criticalMethods.contains(call) && !shouldIgnore) {
          println(s">>> Will replace with NOP: $i at PC=$idx")
          replacedWithNOP += ((idx, i.toString))
          NOP
        } else i

      case (null, _) => NOP
      case (other, _) => other
    }

    //Final report
    println(">>> Bytecode modification complete. Critical methods replaced with NOP.")

    //Attach NOP info to global state for JSON generation
    lastNOPReplacements = Some(replacedWithNOP.toList)
    
    filtered
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
