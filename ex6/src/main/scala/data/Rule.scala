package data

case class Rule(
                 from: String,
                 to: String,
                 `type`: String, // "ALLOWED" or "FORBIDDEN"
                 except: Option[List[Rule]] = None
               )