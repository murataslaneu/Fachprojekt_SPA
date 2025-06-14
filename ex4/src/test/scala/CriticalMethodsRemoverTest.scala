import org.scalatest.funsuite.AnyFunSuite
import modify.JsonIO

class CriticalMethodsRemoverTest extends AnyFunSuite {

  /**
   * Case: yget_nset
   * Description: getSecurityManager remains, setSecurityManager is removed
   * Only setSecurityManager is marked as critical, getSecurityManager is allowed (not critical)
   */
  test("Case: yget_nset -> only set should be removed") {
    val result = JsonIO.readJsonResult("output/yget_nset/cm_test_result.json")
    assert(result.head.removedCalls.exists(_.targetMethod == "setSecurityManager"))
    assert(!result.head.removedCalls.exists(_.targetMethod == "getSecurityManager"))
    assert(!result.head.ignored)
    assert(result.head.bytecodeVerified)
  }

  /**
   * Case: nget_yset
   * Description: setSecurityManager remains, getSecurityManager is removed
   * Only getSecurityManager is marked as critical, setSecurityManager is allowed (not critical)
   */
  test("Case: nget_yset -> only get should be removed") {
    val result = JsonIO.readJsonResult("output/nget_yset/cm_test_result.json")
    assert(result.head.removedCalls.exists(_.targetMethod == "getSecurityManager"))
    assert(!result.head.removedCalls.exists(_.targetMethod == "setSecurityManager"))
    assert(!result.head.ignored)
    assert(result.head.bytecodeVerified)
  }

  /**
   * Case: yget_yset
   * Description: both getSecurityManager and setSecurityManager are removed
   * Both methods are marked as critical and neither is ignored
   */
  test("Case: yget_yset -> both should be removed") {
    val result = JsonIO.readJsonResult("output/yget_yset/cm_test_result.json")
    val removed = result.head.removedCalls.map(_.targetMethod).toSet
    assert(removed == Set("getSecurityManager", "setSecurityManager"))
    assert(!result.head.ignored)
    assert(result.head.bytecodeVerified)
  }

  /**
   * Case: nget_nset
   * Description: both getSecurityManager and setSecurityManager remain
   * Both methods are marked as critical but are explicitly ignored via ignoreCalls
   */
  test("Case: nget_nset -> both should be ignored") {
    val result = JsonIO.readJsonResult("output/nget_nset/cm_test_result.json")
    assert(result.head.removedCalls.isEmpty)
    assert(result.head.ignored)
    assert(result.head.bytecodeVerified)
  }
}
