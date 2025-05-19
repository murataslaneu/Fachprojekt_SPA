package misc

/**
 * Holds the critical methods of a class.
 *
 * @param className Name of the class
 * @param criticalMethods Corresponding methods names of the class that are deemed critical and should be looked out for.
 */
case class CriticalClassMethods(className: String, criticalMethods: List[String])
