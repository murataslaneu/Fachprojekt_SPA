package analyses.E_DeadCodeDetector.helpers

import analyses.E_DeadCodeDetector.data.{DeadCodeReport, DeadInstruction, MethodWithDeadCode, MultiDomainDeadCodeReport, MultiDomainDeadInstruction, MultiDomainMethodWithDeadCode}
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import org.opalj.ai.{AIResult, BaseAI}
import org.opalj.ai.common.DomainRegistry
import org.opalj.br.analyses.Project

import java.net.URL
import scala.collection.mutable

object DeadCodeAnalysis {

  def analyze(logger: Logger, project: Project[URL], config: StaticAnalysisConfig): (MultiDomainDeadCodeReport, List[(Int, DeadCodeReport)]) = {
    // Retrieve all available domains, sorted by their name
    val domains = DomainRegistry
      .domainDescriptions()
      .map { domain =>
        (
          // Identifier for OPAL
          domain.substring(domain.indexOf(']') + 2),
          // Domain name
          domain.substring(domain.indexOf("[") + 1, domain.indexOf("]"))
        )
      }
      .toList
      .sortBy { case (_, domainName) => domainName }

    // Index domains such that the indexes from the config can be used to access these domains
    val domainsIndexed = (1 to domains.length).toList.map { i =>
      i -> domains(i - 1)
    }.toMap

    logger.info("Start abstract interpretation with the selected domains...")
    val singleDomainReports = mutable.Map[Int, DeadCodeReport]()
    config.deadCodeDetector.domains.distinct.foreach { domainNumber =>
      val (domainIdentifier, domainName) = domainsIndexed(domainNumber)
      logger.info(s"  - [$domainNumber]${if (domainNumber < 10) " " else ""} $domainName...")
      singleDomainReports.put(domainNumber, analyzeForDomain(project, domainIdentifier, domainName, config))
    }

    logger.info("Finished abstract interpretation. Group results into a single report...")
    // First, collect results in a map
    // Key: (Signature of method, total instructions, enclosing type name)
    val multiDomainResults = mutable.Map[(String, Int, String), mutable.Map[DeadInstruction, mutable.ListBuffer[Int]]]()
    // Update for each domain
    singleDomainReports
      .toList
      .sortBy { case (domainNumber, _) => domainNumber }
      .foreach { case (domainNumber, singleDomainReport) =>
        // Update each method
        singleDomainReport.methodsFound.foreach { methodWithDeadCode =>
          // Retrieve or create new value
          val deadInstructionsMap = multiDomainResults.getOrElseUpdate(
            key = (
              methodWithDeadCode.fullSignature,
              methodWithDeadCode.numberOfTotalInstructions,
              methodWithDeadCode.enclosingTypeName
            ),
            defaultValue = mutable.Map.empty
          )
          // Update each instruction
          methodWithDeadCode.deadInstructions.foreach { deadInstruction =>
            // Retrieve or create new value
            val foundByDomains = deadInstructionsMap.getOrElseUpdate(
              key = deadInstruction,
              defaultValue = mutable.ListBuffer.empty
            )
            // Insert that this instruction was found by the current domain
            // (i.e. is contained in the current singleDomainReport)
            foundByDomains += domainNumber
          }
        }
      }

    // Then, build the actual report to output
    val foundMethodsWithDomains: List[MultiDomainMethodWithDeadCode] = multiDomainResults.toList.map { case (methodData, deadInstructionsWithDomains) =>
      MultiDomainMethodWithDeadCode(
        fullSignature = methodData._1,
        numberOfTotalInstructions = methodData._2,
        numberOfDeadInstructions = deadInstructionsWithDomains.size,
        enclosingTypeName = methodData._3,
        deadInstructions = deadInstructionsWithDomains.toList.map { case (deadInstruction, foundByDomains) =>
          MultiDomainDeadInstruction(
            stringRepresentation = deadInstruction.stringRepresentation,
            programCounter = deadInstruction.programCounter,
            foundByDomain = foundByDomains.toList
          )
        }
      )
    }
    val totalDeadInstructions = foundMethodsWithDomains
      .map { method => method.numberOfDeadInstructions }.sum

    val multiDomainReport = MultiDomainDeadCodeReport(
      projectJars = config.projectJars.map { file => file.getPath.replace('\\', '/') },
      libraryJars = config.libraryJars.map { file => file.getPath.replace('\\', '/') },
      completelyLoadedLibraries = config.deadCodeDetector.completelyLoadLibraries,
      totalMethodsWithDeadInstructions = foundMethodsWithDomains.length,
      totalDeadInstructions = totalDeadInstructions,
      methodsFound = foundMethodsWithDomains
    )
    logger.info("Finished grouping results.")

    (multiDomainReport, singleDomainReports.toList)
  }

  private def analyzeForDomain(project: Project[URL], domainStr: String, domainName: String, config: StaticAnalysisConfig): DeadCodeReport = {
    // Measuring required time for the analysis
    val startTime = System.currentTimeMillis()
    val ai = new BaseAI(IdentifyDeadVariables = true, RegisterStoreMayThrowExceptions = false)

    val methodsWithDeadCode = mutable.ListBuffer.empty[MethodWithDeadCode]

    // Iterate over all class files in the project
    project.allClassFiles.foreach { classFile =>
      // Iterate over all methods in the project
      classFile.methods.foreach { method =>
        // We can only start abstract interpretation when there is code inside the method
        if (method.body.isDefined) {
          // Build the domain object
          val domain = DomainRegistry.newDomain(domainStr, project, method)

          // Run the actual abstract interpretation for the current method using the interpreter and domain defined above
          val result: AIResult = ai(method, domain)

          val methodCode = result.code
          val evaluatedInstructionPCs = result.evaluatedPCs

          // If number of evaluated instructions is lower than the total number of instructions,
          // there must be at one dead instruction.
          if (evaluatedInstructionPCs.length < methodCode.instructionsCount) {
            val methodSignature = method.signature.toJava
            val enclosingType = method.classFile.fqn.replace('/', '.')
            val totalInstructions = methodCode.instructionsCount
            // Looking for dead instructions
            val deadInstructions = mutable.ListBuffer.empty[DeadInstruction]
            method.body.get.foreach { pcInstruction =>
              val pc = pcInstruction.pc
              val inst = pcInstruction.instruction
              val evaluated = result.wasEvaluated(pc)
              if (!evaluated) deadInstructions += DeadInstruction(inst.toString, pc)
            }
            val deadInstructionsCount = deadInstructions.size
            // Create object containing info about the method
            methodsWithDeadCode += MethodWithDeadCode(
              fullSignature = methodSignature,
              numberOfTotalInstructions = totalInstructions,
              numberOfDeadInstructions = deadInstructionsCount,
              enclosingTypeName = enclosingType,
              deadInstructions = deadInstructions.toList
            )
          }
        }
      }
    }
    // Analysis finished, calculate elapsed time
    val endTime = System.currentTimeMillis()
    val runtime = endTime - startTime
    // Create report object
    DeadCodeReport(
      config.projectJars.map { file => file.getPath.replace('\\', '/') }.toList,
      domainName,
      java.time.LocalDateTime.now(),
      runtime,
      methodsWithDeadCode.toList
    )
  }
}
