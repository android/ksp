// WITH_RUNTIME
// TEST PROCESSOR: HelloWorldProcessor
// EXPECTED:
// Foo.kt
// END
// MODULE: lib
// FILE: Bar.kt
open class Bar

// MODULE: main(lib)
// FILE: Foo.kt
class Foo : Bar()

