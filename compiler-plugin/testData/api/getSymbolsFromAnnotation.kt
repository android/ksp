// TEST PROCESSOR: GetSymbolsFromAnnotationProcessor
// EXPECTED:
// ==== Anno superficial====
// Foo
// propertyFoo
// functionFoo
// constructorParameterFoo
// <init>
// ==== Anno in depth ====
// Foo
// propertyFoo
// functionFoo
// local
// constructorParameterFoo
// <init>
// ==== Bnno superficial====
// File: Foo.kt
// <init>
// propertyFoo.getter()
// ==== Bnno in depth ====
// File: Foo.kt
// <init>
// propertyFoo.getter()
// END
//FILE: annotations.kt
annotation class Anno
annotation class Bnno

//FILE: Foo.kt
@file:Bnno

@Anno
class Foo @Anno constructor(@Anno val constructorParameterFoo: Int){
    @Bnno constructor() {

    }

    @Anno
    val propertyFoo: String
    @Bnno get() = TODO()

    @Anno
    fun functionFoo(@Anno p1: Int, @Bnno p2: Int) {
        @Anno val local = 1
    }
}