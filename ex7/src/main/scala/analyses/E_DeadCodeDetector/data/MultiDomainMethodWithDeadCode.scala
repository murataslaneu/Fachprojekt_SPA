package analyses.E_DeadCodeDetector.data

import play.api.libs.json.{Json, OFormat}

/**
 * Class containing a method that has at least one dead instruction that is never accessible during runtime.
 *
 * Each instruction also saves which domain found this dead instruction.
 *
 * @param fullSignature The complete method signature (i.e. the method name, its parameters and the return type)
 * @param numberOfTotalInstructions Total amount of instructions this method contains
 * @param numberOfDeadInstructions Number of instructions that are never accessible
 * @param enclosingTypeName The class/type this method belongs to
 * @param deadInstructions List of all dead instructions inside this method
 */
case class MultiDomainMethodWithDeadCode
(
  fullSignature: String,
  numberOfTotalInstructions: Int,
  numberOfDeadInstructions: Int,
  enclosingTypeName: String,
  deadInstructions: List[MultiDomainDeadInstruction]
)

object MultiDomainMethodWithDeadCode {
  implicit val format: OFormat[MultiDomainMethodWithDeadCode] = Json.format[MultiDomainMethodWithDeadCode]
}