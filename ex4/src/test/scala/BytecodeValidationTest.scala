import org.opalj.br.instructions._
import org.opalj.br.analyses.Project
import org.opalj.br.ClassFile
import org.scalatest.funsuite.AnyFunSuite
import org.opalj.br.ObjectType

import java.io.File

class BytecodeValidationTest extends AnyFunSuite {

  // Loads the bytecode (instruction array) of the main method from a compiled .class file
  def loadMainMethodBytecode(classFileDir: String): Option[Array[Instruction]] = {
    val project = Project(new File(classFileDir))
    val mainClassOpt: Option[ClassFile] = project.classFile(ObjectType("Main"))

    mainClassOpt.flatMap { cf =>
      cf.methods.find(_.name == "main").flatMap(_.body).map(_.instructions)
    }
  }

  // TEST 1 – Validate that the call to setSecurityManager is replaced with NOP in yget_nset
  test("setSecurityManager should be replaced with NOP in yget_nset") {
    val instructionsOpt = loadMainMethodBytecode("output/yget_nset")
    assert(instructionsOpt.isDefined, "Main class or main method not found")
    val instructions = instructionsOpt.get

    val nops = instructions.slice(31, 34) // Approximate position of removed setSecurityManager call

    // Print bytecode with line numbers
    println("Bytecode (yget_nset):")
    instructions.zipWithIndex.foreach {
      case (i, idx) =>
        val display = if (i == null) "null" else i.mnemonic
        println(f"$idx%03d: $display")
    }

    // Assert that the sliced range contains only NOPs
    assert(nops.forall(i => i != null && i.mnemonic.equalsIgnoreCase("nop")))
  }

  // TEST 2 – Validate that the call to getSecurityManager is replaced with NOP in nget_yset
  test("getSecurityManager should be replaced with NOP in nget_yset") {
    val instructionsOpt = loadMainMethodBytecode("output/nget_yset")
    assert(instructionsOpt.isDefined, "Main class or main method not found")
    val instructions = instructionsOpt.get

    val nops = instructions.slice(37, 40) // Approximate position of removed getSecurityManager call

    println("Bytecode (nget_yset):")
    instructions.zipWithIndex.foreach {
      case (i, idx) =>
        val display = if (i == null) "null" else i.mnemonic
        println(f"$idx%03d: $display")
    }

    // Assert that all instructions in the specified range are NOP
    assert(nops.forall(i => i != null && i.mnemonic.equalsIgnoreCase("nop")))
  }

  // TEST 3 – Validate that both setSecurityManager and getSecurityManager are removed in yget_yset
  test("Both methods replaced with NOP in yget_yset") {
    val instructionsOpt = loadMainMethodBytecode("output/yget_yset")
    assert(instructionsOpt.isDefined)
    val instructions = instructionsOpt.get

    val setNops = instructions.slice(31, 34) // Approx. position of setSecurityManager
    val getNops = instructions.slice(37, 40) // Approx. position of getSecurityManager

    println("Bytecode (yget_yset):")
    instructions.zipWithIndex.foreach {
      case (i, idx) =>
        val display = if (i == null) "null" else i.mnemonic
        println(f"$idx%03d: $display")
    }

    // Assert both are NOPs
    assert(setNops.forall(i => i != null && i.mnemonic.equalsIgnoreCase("nop")))
    assert(getNops.forall(i => i != null && i.mnemonic.equalsIgnoreCase("nop")))
  }

  // TEST 4 – Validate that no critical methods are removed when both are ignored in nget_nset
  test("No method should be replaced in nget_nset") {
    val instructionsOpt = loadMainMethodBytecode("output/nget_nset")
    assert(instructionsOpt.isDefined)
    val instructions = instructionsOpt.get

    val checkRange = instructions.slice(30, 41) // Scan typical area where critical calls may exist

    println("Bytecode (nget_nset):")
    instructions.zipWithIndex.foreach {
      case (i, idx) =>
        val display = if (i == null) "null" else i.mnemonic
        println(f"$idx%03d: $display")
    }

    // Assert that no NOP instructions are found
    assert(!checkRange.exists(i => i != null && i.mnemonic.equalsIgnoreCase("nop")))
  }
}
