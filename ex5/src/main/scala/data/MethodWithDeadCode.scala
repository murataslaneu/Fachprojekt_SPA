package data

import play.api.libs.json.{Json, OFormat}

/**
 * Class containing a method that has at least one dead instruction that is never accessible during runtime.
 *
 * @param fullSignature The complete method signature (i.e. the method name, its parameters and the return type)
 * @param numberOfTotalInstructions Total amount of instructions this method contains
 * @param numberOfDeadInstructions Number of instructions that are never accessible
 * @param enclosingTypeName The class/type this method belongs to
 * @param deadInstructions List of all dead instructions inside this method
 */
case class MethodWithDeadCode(
                               fullSignature: String,
                               numberOfTotalInstructions: Int,
                               numberOfDeadInstructions: Int,
                               enclosingTypeName: String,
                               deadInstructions: List[DeadInstruction]
                             )

object MethodWithDeadCode {
  implicit val format: OFormat[MethodWithDeadCode] = Json.format[MethodWithDeadCode]
}
