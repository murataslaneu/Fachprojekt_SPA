package analyses.E_DeadCodeDetector.helpers

import data.{AnalysisConfig, DeadCodeReport, DeadInstruction, MethodWithDeadCode}
import org.opalj.ai.{AIResult, BaseAI}
import org.opalj.ai.common.DomainRegistry
import org.opalj.br.analyses.Project

import java.net.URL
import scala.io.StdIn
import scala.collection.mutable

object DeadCodeAnalysis {

  /**
   * Simple method to select which domain shall be used for abstract interpretation. In interactive mode, the user can
   * select one of the available domains from the DomainRegistry. In non-interactive mode, the first domain is automatically
   * selected.
   *
   * Domains dictate how precise the tracking of variable values is during abstract interpretation.
   *
   * @param interactive True if interactive selection shall be used
   * @return A string identifier of the selected domain
   */
  def selectDomain(interactive: Boolean): String = {
    val domainDescriptions = DomainRegistry.domainDescriptions

    var cnt = 0
    val domainMap = domainDescriptions.map { descr =>
      val t = (cnt, descr)
      cnt += 1
      t
    }.toMap

    println("The available domains are: ")
    domainMap.toList.sortBy { case (cnt, _) => cnt } foreach { case (cnt, str) =>
      println(s"\t [$cnt] - $str")
    }

    if (interactive) {
      println(s"\nEnter the corresponding domain number to proceed...")
      print(">>> ")
      val input = StdIn.readInt()
      if (input < 0 || input >= cnt) {
        println(s"${Console.RED}Invalid domain number selected, exiting.${Console.RESET}")
        System.exit(1)
      }
      domainMap(input)
    } else {
      val theDomain = domainDescriptions.head
      println(s"Automatically selected domain $theDomain")
      theDomain
    }
  }

  def analyze(project: Project[URL], domainStr: String, domainName: String, config: AnalysisConfig): DeadCodeReport = {
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
//          println(s"${classFile.fqn}#${method.name}")
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
            val enclosingType = method.classFile.fqn.replace('/','.')
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
      config.projectJars.map { file => file.getPath.replace('\\', '/') },
      domainName,
      java.time.LocalDateTime.now(),
      runtime,
      methodsWithDeadCode.toList
    )
  }
}
