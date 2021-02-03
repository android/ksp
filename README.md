# Kotlin Symbol Processing API

Kotlin Symbol Processing (KSP) is an API that you can use to develop
lightweight compiler plugins. KSP provides a simplified compiler plugin
API that leverages the power of Kotlin while keeping the learning curve at
a minimum. Compared to KAPT, annotation processors that use KSP can run 25% faster.

Here are more details on [why considering KSP](/docs/why-ksp.md) over other alternatives.

## Overview

The KSP API processes Kotlin programs idiomatically. KSP understands
Kotlin-specific features, such as extension functions, declaration-site
variance, and local functions. KSP also models types explicitly and
provides basic type checking, such as equivalence and assign-compatibility.

The API models Kotlin program structures at the symbol level according to
[Kotlin grammar](https://kotlinlang.org/docs/reference/grammar.html). When
KSP-based plugins process source programs, constructs like classes, class
members, functions, and associated parameters are easily accessible for the
processors, while things like if blocks and for loops are not.

Conceptually, KSP is similar to
[KType](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-type/)
in Kotlin reflection. The API allows processors to navigate from class
declarations to corresponding types with specific type arguments and
vice-versa. Substituting type arguments, specifying variances, applying
star projections, and marking nullabilities of types are also possible.

Another way to think of KSP is as a pre-processor framework of Kotlin
programs. If we refer to KSP-based plugins as _symbol processors_, or
simply _processors_, then the data flow in a compilation can be described
in the following steps:

1. Processors read and analyze source programs and resources.
1. Processors generate code or other forms of output.
1. The Kotlin compiler compiles the source programs together with the
   generated code.

Unlike a full-fledged compiler plugin, processors cannot modify the code.
A compiler plugin that changes language semantics can sometimes be very
confusing. KSP avoids that by treating the source programs as read-only.

## How KSP looks at source files

Most processors navigate through the various program structures of the
input source code. Before diving into usage of the API, let's look at how
a file might look from KSP's point of view:

```kotlin
KSFile
  packageName: KSName
  fileName: String
  annotations: List<KSAnnotation>  (File annotations)
  declarations: List<KSDeclaration>
    KSClassDeclaration // class, interface, object
      simpleName: KSName
      qualifiedName: KSName
      containingFile: String
      typeParameters: KSTypeParameter
      parentDeclaration: KSDeclaration
      classKind: ClassKind
      primaryConstructor: KSFunctionDeclaration
      superTypes: List<KSTypeReference>
      // contains inner classes, member functions, properties, etc.
      declarations: List<KSDeclaration>
    KSFunctionDeclaration // top level function
      simpleName: KSName
      qualifiedName: KSName
      containingFile: String
      typeParameters: KSTypeParameter
      parentDeclaration: KSDeclaration
      functionKind: FunctionKind
      extensionReceiver: KSTypeReference?
      returnType: KSTypeReference
      parameters: List<KSValueParameter>
      // contains local classes, local functions, local variables, etc.
      declarations: List<KSDeclaration>
    KSPropertyDeclaration // global variable
      simpleName: KSName
      qualifiedName: KSName
      containingFile: String
      typeParameters: KSTypeParameter
      parentDeclaration: KSDeclaration
      extensionReceiver: KSTypeReference?
      type: KSTypeReference
      getter: KSPropertyGetter
        returnType: KSTypeReference
      setter: KSPropertySetter
        parameter: KSValueParameter
```

This view lists common things that are declared in the file--classes,
functions, properties, and so on.

## SymbolProcessor: The entry point

Every processor in KSP implements `SymbolProcessor`:

```kotlin
interface SymbolProcessor {
    fun init(options: Map<String, String>,
             kotlinVersion: KotlinVersion,
             codeGenerator: CodeGenerator,
             logger: KSPLogger)
    fun process(resolver: Resolver) // Let's focus on this
    fun finish()
}
```

A `Resolver` provides `SymbolProcessor` with access to compiler details
such as symbols. A processor that finds all top-level functions and non-local functions in top-level
classes might look something like this:

```kotlin
class HelloFunctionFinderProcessor : SymbolProcessor() {
    ...
    val functions = mutableListOf<String>()
    val visitor = FindFunctionsVisitor()

    override fun process(resolver: Resolver) {
        resolver.getAllFiles().map { it.accept(visitor, Unit) }
    }

    inner class FindFunctionsVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            classDeclaration.getDeclaredFunctions().map { it.accept(this, Unit) }
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            functions.add(function)
        }

        override fun visitFile(file: KSFile, data: Unit) {
            file.declarations.map { it.accept(this, Unit) }
        }
    }
    ...
}
```
## Resources

Some handy links:

* [Quickstart](/docs/quickstart.md)
* [Why use KSP?](/docs/why-ksp.md)
* [Examples](/docs/examples.md)
* [How KSP models Kotlin code](/docs/ksp-additional-details.md)
* [Reference for Java annotation processor authors](/docs/reference.md)
* [Incremental processing notes](/docs/incremental.md)
* [Contributor guide](CONTRIBUTING.md)

## Development status

The API is still under development and is likely to change in the future.
Please do not use it in production yet. The purpose of this preview is
to get your feedback.
[Please let us know what you think about KSP by filing a Github issue](https://github.com/google/ksp/issues)
or connecting with our team in the `#ksp` channel in the
[Kotlin Slack workspace](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up?_ga=2.185732459.358956950.1590619123-888878822.1567025441)!

Here are some planned features that have not yet been implemented:

* Make the IDE aware of the generated code.
* Support Kotlin Multiplatform.