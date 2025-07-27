package analyses.D1_CriticalMethodsRemover

import analyses.SubAnalysis
import data.IgnoredCall
import analyses.D1_CriticalMethodsRemover.modify.data.{AnalysisResult, OriginalBytecode, RemovedCall}
import configs.StaticAnalysisConfig
import org.opalj.br.instructions._
import org.opalj.br.{ClassFile, Code, Method, NoExceptionHandlers}
import org.opalj.bc.Assembler
import org.opalj.ba.toDA
import org.opalj.br.analyses.Project

import java.nio.file.{Files, Path}
import org.opalj.br.instructions.Instruction
import org.slf4j.{Logger, LoggerFactory, MarkerFactory}
import util.{ProjectInitializer, Utils}

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

/**
 * Application that searches for critical method calls (like in ex2) AND edits the bytecode to replaces these with
 * methods that return null. The program outputs new .class files where the edits are visible.
 */
class CriticalMethodsRemover(override val shouldExecute: Boolean) extends SubAnalysis {

  /** Logger used inside this sub-analysis */
  override val logger: Logger = LoggerFactory.getLogger("CriticalMethodsRemover")
  /** The name of the sub-analysis */
  override val analysisName: String = "Critical Methods Remover"
  /** The number of the sub-analysis */
  override val analysisNumber: String = "4a"
  /** Name of the folder where this sub-analysis will put their results in */
  override val outputFolderName: String = "4a_CriticalMethodsRemover"

  private val resultsBuffer = ListBuffer.empty[AnalysisResult]

  private var lastNOPReplacements: Option[List[(Int, String)]] = None

  override def executeAnalysis(config: StaticAnalysisConfig): Unit = {
    resultsBuffer.clear()
    val analysisConfig = config.criticalMethodsRemover

    // Print out configuration
    val criticalMethodsString = Utils.buildSampleSelectedMethodsString(analysisConfig.criticalMethods, 5, 3)
    var ignoreClassString = Random.shuffle(analysisConfig.ignore).take(10).map { ignoreCall =>
      s"${ignoreCall.callerClass}#${ignoreCall.callerMethod} -> ${ignoreCall.targetClass}#${ignoreCall.targetMethod}"
    }.mkString("\n    - ", "\n    - ", "")
    val moreIgnoreCalls = if (analysisConfig.ignore.size > 10) s"\n... and ${analysisConfig.ignore.size - 10} more ignores"
    else ""
    // Override ignoreClassString if it doesn't contain anything
    if (analysisConfig.ignore.isEmpty) ignoreClassString = "None"
    logger.info(
      s"""Configuration:
         |  - Critical Methods: $criticalMethodsString
         |  - Ignore calls: $ignoreClassString$moreIgnoreCalls""".stripMargin
    )

    // Set up project
    logger.info("Initializing OPAL project...")
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = config.projectJars,
      libcpFiles = config.libraryJars
    )
    logger.info("Project initialization finished. Starting analysis on project...")

    // Analysis part
    // Convert the critical methods list into a flat list of (className, methodName) tuples
    val criticalMethods: List[(String, String)] =
      analysisConfig.criticalMethods.flatMap(sm =>
        sm.methods.map(methodName => (sm.className, methodName))
      )

    val outputDir = s"${config.resultsOutputPath}/$outputFolderName"
    val classFilesOutputDir = s"$outputDir/modifiedClasses"

    var replacedInvalidCharacter = false
    val originalBytecodes = mutable.ListBuffer[OriginalBytecode]()

    // Analyze all methods in the project
    project.allProjectClassFiles.foreach { cf =>
      cf.methods.foreach { m =>
        val foundInvokes = findCriticalInvokes(m, criticalMethods)

        // If critical calls are found, proceed with modification
        if (foundInvokes.nonEmpty && m.body.isDefined) {
          originalBytecodes += getOriginalBytecode(project, cf, m, foundInvokes.map(_._1))
          //logger.debug(s"Found critical call(s) in method: ${cf.thisType.toJava}.${m.name}${m.descriptor.toJava}.")

          // Modify the method body to remove critical calls
          val oldCode = m.body.get
          val newInstructions = replaceCriticalInvokesWithNOP(
            oldCode,
            criticalMethods,
            analysisConfig.ignore,
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
          val classFilePath = Path.of(s"$classFilesOutputDir/$sanitizedClassFileName.class")
          Files.createDirectories(classFilePath.getParent)
          Files.write(classFilePath, newClassBytes)

          // After class writing
          //logger.debug(s"Wrote modified class file to: $classFilePath.")

          // Track removed calls
          val removed = foundInvokes.collect {
            case (_, instr: MethodInvocationInstruction)
              if !analysisConfig.ignore.exists(ic =>
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
              analysisConfig.ignore.exists(ic =>
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

          val jarFile = project.source(cf) match {
            case Some(source) =>
              val sourcePath = source.toString
              if (sourcePath.contains("jar:file:")) {
                val jarPath = sourcePath.substring(sourcePath.indexOf("jar:file:") + 9)
                val jarName = jarPath.substring(0, jarPath.lastIndexOf("!"))
                new File(jarName).getName
              } else {
                "[Unknown]"
              }
            case None => "[Unknown]"
          }

          // Collect analysis result
          val result = AnalysisResult(
            className = cf.thisType.toJava,
            method = m.signature.toJava,
            fromJar = jarFile,
            removedCalls = removed,
            path = s"$classFilesOutputDir/${cf.thisType.toJava.replace('.', '/')}.class",
            ignored = wasIgnored,
            bytecodeVerified = bytecodeVerified,
            nopReplacements = lastNOPReplacements
          )

          resultsBuffer += result
        }
      }
    }
    logger.info("Analysis finished.")

    // Write analysis results to JSON if configured
    val resultList = resultsBuffer.toList
    val jsonOutputPath = s"$outputDir/results.json"

    modify.FileIO.writeResult(resultList, jsonOutputPath)
    logger.info(s"Wrote json report to $jsonOutputPath.")

    val originalBytecodePath = s"$outputDir/originalBytecode.txt"
    modify.FileIO.writeOriginalBytecodeFile(originalBytecodes.toList, originalBytecodePath)
    logger.info(s"Wrote text file with original bytecode for each modified method to $originalBytecodePath.")

    if (resultsBuffer.isEmpty) logger.info(s"No classes/methods have been modified.")
    else {
      val modifiedMethods = buildModifiedMethodsString(resultsBuffer.toList, 10)
      logger.info(s"Modified the following methods: $modifiedMethods")
    }

    if (replacedInvalidCharacter) {
      logger.info(
        MarkerFactory.getMarker("BLUE"),
        "Note: At least one of the class files contained a character not allowed in Windows file names (':', '<' or '>')."
      )
      logger.info(
        MarkerFactory.getMarker("BLUE"),
        "      Such characters have been replaced with similar-looking Unicode characters (U+02D0, U+2039 or U+203A).\n"
      )
    }
  }

  /**
   * Scans the bytecode of a method and finds all invocations to critical methods.
   *
   * @param method          The method to be scanned.
   * @param criticalMethods A list of (className, methodName) tuples marking critical calls.
   * @return A sequence of (instructionIndex, instruction) pairs for matching critical calls.
   */
  private def findCriticalInvokes(
                                   method: Method,
                                   criticalMethods: List[(String, String)]
                                 ): Array[(Int, Instruction)] = {

    val result: Option[Array[(Int, Instruction)]] = method.body.map { code =>
      code.instructions.zipWithIndex.collect {
        case (instr: MethodInvocationInstruction, idx)
          if criticalMethods.contains((instr.declaringClass.toJava, instr.name)) =>
          (idx, instr)
      }
    }

    result.getOrElse(Array.empty[(Int, Instruction)])
  }

  /**
   * Retrieves an [[OriginalBytecode]] that can be used to write to a text file.
   *
   * @param project Used project for this analysis.
   * @param classFile The class file that will be modified.
   * @param method The method that will be modified.
   * @param criticalInvokePCs The program counters whose instructions will be replaced with NOP.
   * @return [[OriginalBytecode]] object that can be used to show the original bytecode.
   */
  private def getOriginalBytecode(project: Project[URL], classFile: ClassFile, method: Method, criticalInvokePCs: Array[Int]): OriginalBytecode = {
    val originalBytecodeBuilder = new StringBuilder()
    method.body.foreach { code =>
      val digits = (code.instructions.length - 1).toString.length
      code.instructions.zipWithIndex.foreach {
        case (instr, idx) =>
          val replaceWithNop = if (criticalInvokePCs.contains(idx)) "  // Replaced with NOP" else ""
          val display = if (instr == null) "null" else instr.toString
          originalBytecodeBuilder.append(String.format("    %0" + digits + "d: %s%s%n", idx.asInstanceOf[Object], display, replaceWithNop))
      }
    }
    val jarFile = project.source(classFile) match {
      case Some(source) =>
        val sourcePath = source.toString
        if (sourcePath.contains("jar:file:")) {
          val jarPath = sourcePath.substring(sourcePath.indexOf("jar:file:") + 9)
          val jarName = jarPath.substring(0, jarPath.lastIndexOf("!"))
          new File(jarName).getName
        } else {
          "[Unknown]"
        }
      case None => "[Unknown]"
    }

    OriginalBytecode(
      className = classFile.thisType.toJava,
      method = method.signatureToJava(true),
      fromJar = jarFile,
      bytecode = originalBytecodeBuilder.toString
    )
  }

  /**
   * Builds a string from the analysis results given to give a sample of the methods modified.
   *
   * @param modified The List of AnalysisResults to build the string from.
   * @param k        The number of sample methods to take.
   * @return String that can be used to show in the logs.
   */
  // noinspection SameParameterValue
  private def buildModifiedMethodsString(modified: List[AnalysisResult], k: Int): String = {
    val samples = Random.shuffle(modified).take(k)
    if (samples.isEmpty) return "None"
    val mainString = samples.map { sample =>
      val className = sample.className
      val method = sample.method
      val numberOfRemovedCalls = sample.removedCalls.length

      s"""In $className:
         |    - Method: $method
         |    - Removed $numberOfRemovedCalls calls.""".stripMargin
    }.sorted.mkString("\n  - ", "\n  - ", "")
    val remainingMethods = modified.length - k
    val moreMethods = if (remainingMethods > 0) s"\n... and $remainingMethods more method${if (remainingMethods != 1) "s" else ""} modified"
    else ""

    s"$mainString$moreMethods"
  }

  /**
   * Replaces all critical method invocation instructions with NOP instructions,
   * unless the invocation is listed in the ignoreCalls whitelist.
   *
   * This approach avoids deleting instructions outright, which may cause issues
   * with bytecode verification or control flow consistency (e.g., jump offsets).
   *
   * @param code            The original bytecode of the method
   * @param criticalMethods A list of (className, methodName) tuples defining critical method calls
   * @param ignoreCalls     A list of IgnoredCall entries representing calls that should not be removed
   * @param className       The name of the current class (used to match ignoreCalls)
   * @param methodName      The name of the current method (used to match ignoreCalls)
   * @return A modified instruction array where matched INVOKEs are replaced with NOPs
   */
  private def replaceCriticalInvokesWithNOP(
                                             code: Code,
                                             criticalMethods: List[(String, String)],
                                             ignoreCalls: Set[IgnoredCall],
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

//        // DEBUG for Ignore and CriticalMethods
//        var debugLogCounter: Int = 0
//        val debugLogLimit: Int = 0
//
//        if (debugLogCounter < debugLogLimit) {
//          logger.debug(f"[?] Should ignore: $className.$methodName -> ${call._1}.${call._2} = $shouldIgnore")
//          logger.debug(s"[!] Active criticalMethods: " + criticalMethods.map { case (c, m) => s"$c#$m" }.mkString(", "))
//          debugLogCounter += 1
//        }

        if (criticalMethods.contains(call) && !shouldIgnore) {
          //logger.debug(s"Will replace with NOP: $i at PC=$idx")
          replacedWithNOP += ((idx, i.toString))
          NOP
        } else i

      case (null, _) => NOP
      case (other, _) => other
    }

    // Attach NOP info to global state for JSON generation
    lastNOPReplacements = Some(replacedWithNOP.toList)

    filtered
  }
}
