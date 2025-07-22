package analyses.D2_TPLMethodsRemover.create

import configs.TPLMethodsRemoverConfig
import org.opalj.br.analyses.Project
import org.opalj.br.instructions._
import org.opalj.br._
import org.opalj.tac.cg.CallGraph

import java.net.{URL, URLDecoder}
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

/**
 * Main object responsible for the static analysis of TPL method usage.
 */
object TPLMethodUsageAnalysis {

  /**
   * Function that does the main analysis of this application and creates the modified class files.
   *
   * @param project        The OPAL project with all loaded class files
   * @param callGraph      Call graph generated from the project
   * @param analysisConfig The config for this analysis
   * @return List containing results for each library
   */
  def analyzeAndCreate(
                        project: Project[URL],
                        callGraph: CallGraph,
                        analysisConfig: TPLMethodsRemoverConfig): mutable.ListBuffer[ClassFile] = {
    // Step 1: Find corresponding class files of the TPL jar
    val tplPath = analysisConfig.tplJar
    val tplClasses = mutable.Map.empty[ClassFile, mutable.ListBuffer[Method]]

    project.libraryClassFilesWithSources.foreach { case (classFile, source) =>
      val classPath = URLDecoder.decode(source.getPath, StandardCharsets.UTF_8.name())
      if (classPath.contains(tplPath)) tplClasses += (classFile -> mutable.ListBuffer.empty[Method])
    }

    // Step 2: Go through all reachable methods (found by the call graph)
    callGraph.reachableMethods.foreach { context =>
      // Step 2.1: Check type of declaredMethod
      val declaredMethod = context.method
      val methods = mutable.ListBuffer.empty[Method]
      // Virtual declared method: Method where original definition unavailable. Not usable for analysis
      if (declaredMethod.isVirtualOrHasSingleDefinedMethod && !declaredMethod.hasSingleDefinedMethod) {
        /* Ignore */
      }
      // Single defined method: No ambiguity of which method definition will be called, interesting for analysis
      else if (declaredMethod.hasSingleDefinedMethod) methods += declaredMethod.definedMethod
      // Multiple defined methods: Multiple method definitions may be callable (and thus should each be added)
      else methods.addAll(declaredMethod.definedMethods)

      // Step 2.2: Add method as being used/accessed if it its corresponding class file is part of the TPL to analyze
      methods.foreach { method =>
        if (analysisConfig.includeNonPublicMethods || method.isPublic) tplClasses.get(method.classFile).foreach {
          accessedMethods => accessedMethods += method
        }
      }
    }

    // Step 3: Modifying class files
    val modifiedClassFiles = mutable.ListBuffer.empty[ClassFile]
    tplClasses.foreach { case (classFile, accessedMethods) =>
      if (accessedMethods.nonEmpty) {
        // Modify each used method to have an empty method body that either returns 0 or null
        // (depending on the return type of the method)
        val emptyMethods = accessedMethods.map { currentMethod =>
          currentMethod.body match {
            // Method has a body --> Replace it
            case Some(oldCode) => val emptyMethodInstructions = getEmptyMethodBody(currentMethod.returnType)
              currentMethod.copy(body = Some(oldCode.copy(instructions = emptyMethodInstructions, exceptionHandlers = NoExceptionHandlers)))
            // Method has no body --> No modifications needed, method can be copied unchanged
            case None => currentMethod.copy()
          }
        }
        // Final step: Copy class file with new methods
        modifiedClassFiles += classFile.copy(methods = ArraySeq.from(emptyMethods))
      }
    }

    modifiedClassFiles
  }

  /**
   * Creates a new instruction array whose code either returns nothing or 0, depending on the return type.
   *
   * @param returnType The return type of the method
   */
  private def getEmptyMethodBody(returnType: Type): Array[Instruction] = returnType match {
    case VoidType => Array(RETURN)
    case IntegerType | BooleanType | ByteType | CharType | ShortType => Array(ICONST_0, IRETURN)
    case LongType => Array(LCONST_0, LRETURN)
    case FloatType => Array(FCONST_0, FRETURN)
    case DoubleType => Array(DCONST_0, DRETURN)
    // Any other type means that return type is an object
    case _ => Array(ACONST_NULL, ARETURN)
  }
}