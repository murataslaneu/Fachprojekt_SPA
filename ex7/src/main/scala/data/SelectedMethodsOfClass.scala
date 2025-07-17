package data

/**
 * Holds a list of selected methods for a class. Used for either storing the critical methods of a class or the entry
 * points of a class.
 *
 * @param className Name of the class
 * @param selectedMethods Corresponding methods names of the class which have been selected.
 */
case class SelectedMethodsOfClass(var className: String, var selectedMethods: List[String])
