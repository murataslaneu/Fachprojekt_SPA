package configs

import analyses.F_ArchitectureValidator.data.Rule

/**
 * Analysis 6: Architecture validator (ex6), config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param onlyMethodAndFieldAccesses Whether only method calls and field accesses should be treated as a dependency
 *                                   (`true`) or also more dependencies (inheritance, interface implementation,
 *                                   field/parameter/return type)
 * @param defaultRule Determines whether a dependency is allowed ("ALLOWED") or forbidden ("FORBIDDEN") if there is no
 *                    rule further specifying that.
 * @param rules List of (recursive) rules on what is allowed or not. Each rule specifies which class/package/jar ("from")
 *              can/cannot ("type") access another class/package/jar ("to"), with exceptions to this rule ("except").
 */
case class ArchitectureValidatorConfig
(
  override val execute: Boolean,
  onlyMethodAndFieldAccesses: Boolean = false,
  defaultRule: String = "ALLOWED",
  rules: List[Rule] = List.empty
) extends SubAnalysisConfig()
