// TEST PROCESSOR: GetSymbolsFromAnnotationProcessor
// EXPECTED:
// ==== Anno superficial====
// Foo:KSClassDeclaration
// propertyFoo:KSPropertyDeclaration
// functionFoo:KSFunctionDeclaration
// p1:KSValueParameter
// constructorParameterFoo:KSPropertyDeclaration
// <init>:KSFunctionDeclaration
// constructorParameterFoo:KSValueParameter
// param:KSValueParameter
// Bar:KSClassDeclaration
// Baz:KSClassDeclaration
// Flux:KSTypeAlias
// ==== Anno in depth ====
// Foo:KSClassDeclaration
// propertyFoo:KSPropertyDeclaration
// functionFoo:KSFunctionDeclaration
// p1:KSValueParameter
// local:KSPropertyDeclaration
// constructorParameterFoo:KSPropertyDeclaration
// <init>:KSFunctionDeclaration
// constructorParameterFoo:KSValueParameter
// param:KSValueParameter
// Bar:KSClassDeclaration
// Baz:KSClassDeclaration
// Flux:KSTypeAlias
// ==== Bnno superficial====
// File: Foo.kt:KSFile
// <init>:KSFunctionDeclaration
// propertyFoo.getter():KSPropertyAccessorImpl
// p2:KSValueParameter
// ==== Bnno in depth ====
// File: Foo.kt:KSFile
// <init>:KSFunctionDeclaration
// propertyFoo.getter():KSPropertyAccessorImpl
// p2:KSValueParameter
// ==== A1 superficial====
// Foo:KSClassDeclaration
// propertyFoo:KSPropertyDeclaration
// functionFoo:KSFunctionDeclaration
// p1:KSValueParameter
// constructorParameterFoo:KSPropertyDeclaration
// <init>:KSFunctionDeclaration
// constructorParameterFoo:KSValueParameter
// param:KSValueParameter
// Bar:KSClassDeclaration
// Baz:KSClassDeclaration
// Flux:KSTypeAlias
// ==== A1 in depth ====
// Foo:KSClassDeclaration
// propertyFoo:KSPropertyDeclaration
// functionFoo:KSFunctionDeclaration
// p1:KSValueParameter
// local:KSPropertyDeclaration
// constructorParameterFoo:KSPropertyDeclaration
// <init>:KSFunctionDeclaration
// constructorParameterFoo:KSValueParameter
// param:KSValueParameter
// Bar:KSClassDeclaration
// Baz:KSClassDeclaration
// Flux:KSTypeAlias
// ==== A2 superficial====
// Foo:KSClassDeclaration
// propertyFoo:KSPropertyDeclaration
// functionFoo:KSFunctionDeclaration
// p1:KSValueParameter
// constructorParameterFoo:KSPropertyDeclaration
// <init>:KSFunctionDeclaration
// constructorParameterFoo:KSValueParameter
// param:KSValueParameter
// Bar:KSClassDeclaration
// Baz:KSClassDeclaration
// Flux:KSTypeAlias
// ==== A2 in depth ====
// Foo:KSClassDeclaration
// propertyFoo:KSPropertyDeclaration
// functionFoo:KSFunctionDeclaration
// p1:KSValueParameter
// local:KSPropertyDeclaration
// constructorParameterFoo:KSPropertyDeclaration
// <init>:KSFunctionDeclaration
// constructorParameterFoo:KSValueParameter
// param:KSValueParameter
// Bar:KSClassDeclaration
// Baz:KSClassDeclaration
// Flux:KSTypeAlias
// ==== Cnno in depth ====
// <set-?>:KSValueParameter
// constructorParameterFoo:KSValueParameter
// x:KSPropertyDeclaration
// x:KSValueParameter
// END
//FILE: annotations.kt
annotation class Anno
annotation class Bnno
annotation class Cnno
typealias A1 = Anno
typealias A2 = A1

//FILE: Foo.kt
@file:Bnno

@Anno
class Foo @Anno constructor(@Anno @param:Cnno val constructorParameterFoo: Int, @Anno param: Int){
    @Bnno constructor() {

    }

    @Anno
    val propertyFoo: String
    @Bnno get() = TODO()

    @Anno
    fun functionFoo(@Anno p1: Int, @Bnno p2: Int) {
        @Anno val local = 1
    }

    @setparam:Cnno
    var a = 1
}

class C(@Cnno val x: Int)

@A1
class Bar

@A2
class Baz

@Anno
typealias Flux = String
