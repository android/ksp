// TEST PROCESSOR: DeclaredProcessor
// EXPECTED:
// Base class declared functions:
// subFun
// synthetic constructor for Sub
// Sub class declared functions:
// baseFun
// <init>
// JavaSource class declared functions:
// javaSourceFun
// synthetic constructor for JavaSource
// END
// MODULE: module1
// FILE: lib.kt
open class Base {
    fun baseFun() {}
}
// MODULE: main(module1)
// FILE: sub.kt
class Sub: Base() {
    fun subFun() {}
}

// FILE: JavaSource.java
public class JavaSource {
    public int javaSourceFun() {
        return 1
    }
}
