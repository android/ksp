# Examples

Get all member functions that are declared directly within a class:

```kotlin
fun KSClassDeclaration.getDeclaredFunctions(): List<KSFunctionDeclaration> {
    return this.declarations.filterIsInstance<KSFunctionDeclaration>()
}
```

Determine whether a class or function is local to another function:

```kotlin
fun KSDeclaration.isLocal(): Boolean {
    return this.parentDeclaration != null && this.parentDeclaration !is KSClassDeclaration
}
```

Find the actual class or interface declaration that the alias points to recursively:

```
fun KSTypeAlias.findActualType(): KSClassDeclaration {
    val resolvedType = this.type.resolve().declaration
    return if (resolvedType is KSTypeAlias) {
        resolvedType.findActualType()
    } else {
        resolvedType as KSClassDeclaration
    }
}
```

Find out suppressed names in a file annotation:

```kotlin
// @file:kotlin.Suppress("Example1", "Example2")
fun KSFile.suppressedNames(): List<String> {
    val ignoredNames = mutableListOf<String>()
    annotations.forEach {
        if (it.shortName.asString() == "Suppress" && it.annotationType.resolve()?.declaration?.qualifiedName?.asString() == "kotlin.Suppress") {
            it.arguments.forEach {
                (it.value as List<String>).forEach { ignoredNames.add(it) }
            }
        }
    }
    return ignoredNames
}
```
