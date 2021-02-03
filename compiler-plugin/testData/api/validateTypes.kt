// WITH_RUNTIME
// TEST PROCESSOR: ValidateProcessor
// EXPECTED:
// ErrorInMember invalid
// goodProp valid
// errorFun invalid
// <init> valid
// GoodClass valid
// C valid
// BadJavaClass invalid
// ErrorAnnotationType invalid
// END
// FILE: a.kt
annotation class Anno(val i: Int)

@Anno(1)
class ErrorInMember : C {
    val goodProp: Int
    fun errorFun(): NonExistType {

    }
}

@NonExistAnnotation
class ErrorAnnotationType {
}

@Anno(1)
open class GoodClass {
    val a: Int

    fun foo(): Int = 1
}

// FILE: C.java

public class C extends GoodClass {}

class BadJavaClass extends NonExistType {

}