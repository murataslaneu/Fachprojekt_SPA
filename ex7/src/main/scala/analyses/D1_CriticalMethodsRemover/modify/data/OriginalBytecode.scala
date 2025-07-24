package analyses.D1_CriticalMethodsRemover.modify.data

case class OriginalBytecode
(
  className: String,
  method: String,
  fromJar: String,
  bytecode: String
)
