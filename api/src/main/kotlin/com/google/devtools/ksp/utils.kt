/*
 * Copyright 2020 Google LLC
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSValidateVisitor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Try to resolve the [KSClassDeclaration] for a class using its fully qualified name.
 *
 * @param T The class to resolve a [KSClassDeclaration] for.
 * @return Resolved [KSClassDeclaration] if found, `null` otherwise.
 *
 * @see [Resolver.getClassDeclarationByName]
 */
inline fun <reified T> Resolver.getClassDeclarationByName(): KSClassDeclaration? {
    return T::class.qualifiedName?.let { fqcn ->
        getClassDeclarationByName(getKSNameFromString(fqcn))
    }
}

/**
 * Find a class in the compilation classpath for the given name.
 *
 * @param name fully qualified name of the class to be loaded; using '.' as separator.
 * @return a KSClassDeclaration, or null if not found.
 */
fun Resolver.getClassDeclarationByName(name: String): KSClassDeclaration? =
    getClassDeclarationByName(getKSNameFromString(name))

/**
 * Find functions in the compilation classpath for the given name.
 *
 * @param name fully qualified name of the function to be loaded; using '.' as separator.
 * @param includeTopLevel a boolean value indicate if top level functions should be searched. Default false. Note if top level functions are included, this operation can be expensive.
 * @return a Sequence of KSFunctionDeclaration.
 */
fun Resolver.getFunctionDeclarationsByName(
    name: String,
    includeTopLevel: Boolean = false
): Sequence<KSFunctionDeclaration> = getFunctionDeclarationsByName(getKSNameFromString(name), includeTopLevel)

/**
 * Find a property in the compilation classpath for the given name.
 *
 * @param name fully qualified name of the property to be loaded; using '.' as separator.
 * @param includeTopLevel a boolean value indicate if top level properties should be searched. Default false. Note if top level properties are included, this operation can be expensive.
 * @return a KSPropertyDeclaration, or null if not found.
 */
fun Resolver.getPropertyDeclarationByName(name: String, includeTopLevel: Boolean = false): KSPropertyDeclaration? =
    getPropertyDeclarationByName(getKSNameFromString(name), includeTopLevel)

/**
 * Find the containing file of a KSNode.
 * @return KSFile if the given KSNode has a containing file.
 * example of symbols without a containing file: symbols from class files, synthetic symbols created by user.
 */
val KSNode.containingFile: KSFile?
    get() {
        var current: KSNode? = this.parent
        while (current != null) {
            if (current is KSFile) {
                return current
            }
            current = current.parent
        }
        return null
    }

/**
 * Get functions directly declared inside the class declaration.
 *
 * What are included: member functions, constructors, extension functions declared inside it, etc.
 * What are NOT included: inherited functions, extension functions declared outside it.
 */
fun KSClassDeclaration.getDeclaredFunctions(): Sequence<KSFunctionDeclaration> {
    return this.declarations.filterIsInstance<KSFunctionDeclaration>()
}

/**
 * Get properties directly declared inside the class declaration.
 *
 * What are included: member properties, extension properties declared inside it, etc.
 * What are NOT included: inherited properties, extension properties declared outside it.
 */
fun KSClassDeclaration.getDeclaredProperties(): Sequence<KSPropertyDeclaration> {
    return this.declarations.filterIsInstance<KSPropertyDeclaration>()
}

fun KSClassDeclaration.getConstructors(): Sequence<KSFunctionDeclaration> {
    return getDeclaredFunctions().filter {
        it.isConstructor()
    }
}

/**
 * Check whether this is a local declaration, or namely, declared in a function.
 */
fun KSDeclaration.isLocal(): Boolean {
    return this.parentDeclaration != null && this.parentDeclaration !is KSClassDeclaration
}

/**
 * Perform a validation on a given symbol to check if all interested types in symbols enclosed scope are valid, i.e. resolvable.
 * @param predicate: A lambda for filtering interested symbols for performance purpose. Default checks all.
 */
fun KSNode.validate(predicate: (KSNode?, KSNode) -> Boolean = { _, _ -> true }): Boolean {
    return this.accept(KSValidateVisitor(predicate), null)
}

/**
 * Find the KSClassDeclaration that the alias points to recursively.
 */
fun KSTypeAlias.findActualType(): KSClassDeclaration {
    val resolvedType = this.type.resolve().declaration
    return if (resolvedType is KSTypeAlias) {
        resolvedType.findActualType()
    } else {
        resolvedType as KSClassDeclaration
    }
}

/**
 * Determine [Visibility] of a [KSDeclaration].
 */
fun KSDeclaration.getVisibility(): Visibility {
    return when {
        this.modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
        this.modifiers.contains(Modifier.OVERRIDE) -> {
            when (this) {
                is KSFunctionDeclaration -> this.findOverridee()?.getVisibility()
                is KSPropertyDeclaration -> this.findOverridee()?.getVisibility()
                else -> null
            } ?: Visibility.PUBLIC
        }
        this.isLocal() -> Visibility.LOCAL
        this.modifiers.contains(Modifier.PRIVATE) -> Visibility.PRIVATE
        this.modifiers.contains(Modifier.PROTECTED) ||
            this.modifiers.contains(Modifier.OVERRIDE) -> Visibility.PROTECTED
        this.modifiers.contains(Modifier.INTERNAL) -> Visibility.INTERNAL
        // for synthetic origin from Java source, synthetic members follow visibility from parent to avoid
        // package private synthetic members being mishandled as public.
        this.origin == Origin.SYNTHETIC && this.parentDeclaration?.origin == Origin.JAVA ->
            this.parentDeclaration!!.getVisibility()
        else -> if (this.origin != Origin.JAVA && this.origin != Origin.JAVA_LIB)
            Visibility.PUBLIC else Visibility.JAVA_PACKAGE
    }
}

/**
 * get all super types for a class declaration
 * Calling [getAllSuperTypes] requires type resolution therefore is expensive and should be avoided if possible.
 */
fun KSClassDeclaration.getAllSuperTypes(): Sequence<KSType> {

    fun KSTypeParameter.getTypesUpperBound(): Sequence<KSClassDeclaration> =
        this.bounds.asSequence().flatMap {
            when (val resolvedDeclaration = it.resolve().declaration) {
                is KSClassDeclaration -> sequenceOf(resolvedDeclaration)
                is KSTypeAlias -> sequenceOf(resolvedDeclaration.findActualType())
                is KSTypeParameter -> resolvedDeclaration.getTypesUpperBound()
                else -> throw IllegalStateException("unhandled type parameter bound, $ExceptionMessage")
            }
        }

    return this.superTypes
        .asSequence()
        .map { it.resolve() }
        .plus(
            this.superTypes
                .asSequence()
                .mapNotNull { it.resolve().declaration }
                .flatMap {
                    when (it) {
                        is KSClassDeclaration -> it.getAllSuperTypes()
                        is KSTypeAlias -> it.findActualType().getAllSuperTypes()
                        is KSTypeParameter -> it.getTypesUpperBound().flatMap { it.getAllSuperTypes() }
                        else -> throw IllegalStateException("unhandled super type kind, $ExceptionMessage")
                    }
                }
        )
        .distinct()
}

fun KSClassDeclaration.isAbstract() =
    this.classKind == ClassKind.INTERFACE || this.modifiers.contains(Modifier.ABSTRACT)

fun KSPropertyDeclaration.isAbstract(): Boolean {
    if (modifiers.contains(Modifier.ABSTRACT)) {
        return true
    }
    val parentClass = parentDeclaration as? KSClassDeclaration ?: return false
    if (parentClass.classKind != ClassKind.INTERFACE) return false
    // this is abstract if it does not have setter/getter or setter/getter have abstract modifiers
    return (getter?.modifiers?.contains(Modifier.ABSTRACT) ?: true) &&
        (setter?.modifiers?.contains(Modifier.ABSTRACT) ?: true)
}

fun KSDeclaration.isOpen() = !this.isLocal() && !this.modifiers.contains(Modifier.FINAL) &&
    (
        (this as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE ||
            this.modifiers.contains(Modifier.OVERRIDE) ||
            this.modifiers.contains(Modifier.ABSTRACT) ||
            this.modifiers.contains(Modifier.OPEN) ||
            this.modifiers.contains(Modifier.SEALED) ||
            (
                this !is KSClassDeclaration &&
                    (this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE
                ) ||
            (!this.modifiers.contains(Modifier.FINAL) && this.origin == Origin.JAVA)
        )

fun KSDeclaration.isPublic() = this.getVisibility() == Visibility.PUBLIC

fun KSDeclaration.isProtected() = this.getVisibility() == Visibility.PROTECTED

fun KSDeclaration.isInternal() = this.modifiers.contains(Modifier.INTERNAL)

fun KSDeclaration.isPrivate() = this.modifiers.contains(Modifier.PRIVATE)

fun KSDeclaration.isJavaPackagePrivate() = this.getVisibility() == Visibility.JAVA_PACKAGE

fun KSDeclaration.closestClassDeclaration(): KSClassDeclaration? {
    return if (this is KSClassDeclaration) {
        this
    } else {
        this.parentDeclaration?.closestClassDeclaration()
    }
}

// TODO: cross module visibility is not handled
fun KSDeclaration.isVisibleFrom(other: KSDeclaration): Boolean {
    fun KSDeclaration.isSamePackage(other: KSDeclaration): Boolean =
        this.packageName == other.packageName

    // lexical scope for local declaration.
    fun KSDeclaration.parentDeclarationsForLocal(): List<KSDeclaration> {
        val parents = mutableListOf<KSDeclaration>()

        var parentDeclaration = this.parentDeclaration!!

        while (parentDeclaration.isLocal()) {
            parents.add(parentDeclaration)
            parentDeclaration = parentDeclaration.parentDeclaration!!
        }

        parents.add(parentDeclaration)

        return parents
    }

    fun KSDeclaration.isVisibleInPrivate(other: KSDeclaration) =
        (other.isLocal() && other.parentDeclarationsForLocal().contains(this.parentDeclaration)) ||
            this.parentDeclaration == other.parentDeclaration ||
            this.parentDeclaration == other || (
            this.parentDeclaration == null &&
                other.parentDeclaration == null &&
                this.containingFile == other.containingFile
            )

    return when {
        // locals are limited to lexical scope
        this.isLocal() -> this.parentDeclarationsForLocal().contains(other)
        // file visibility or member
        // TODO: address nested class.
        this.isPrivate() -> this.isVisibleInPrivate(other)
        this.isPublic() -> true
        this.isInternal() && other.containingFile != null && this.containingFile != null -> true
        this.isJavaPackagePrivate() -> this.isSamePackage(other)
        this.isProtected() -> this.isVisibleInPrivate(other) || this.isSamePackage(other) ||
            other.closestClassDeclaration()?.let {
            this.closestClassDeclaration()!!.asStarProjectedType().isAssignableFrom(it.asStarProjectedType())
        } ?: false
        else -> false
    }
}

/**
 * Returns `true` if this is a constructor function.
 */
fun KSFunctionDeclaration.isConstructor() = this.simpleName.asString() == "<init>"

const val ExceptionMessage = "please file a bug at https://github.com/google/ksp/issues/new"

val KSType.outerType: KSType?
    get() {
        if (Modifier.INNER !in declaration.modifiers)
            return null
        val outerDecl = declaration.parentDeclaration as? KSClassDeclaration ?: return null
        return outerDecl.asType(arguments.subList(declaration.typeParameters.size, arguments.size))
    }

val KSType.innerArguments: List<KSTypeArgument>
    get() = arguments.subList(0, declaration.typeParameters.size)

@KspExperimental
fun Resolver.getKotlinClassByName(name: KSName): KSClassDeclaration? {
    val kotlinName = mapJavaNameToKotlin(name) ?: name
    return getClassDeclarationByName(kotlinName)
}

@KspExperimental
fun Resolver.getKotlinClassByName(name: String): KSClassDeclaration? =
    getKotlinClassByName(getKSNameFromString(name))

@KspExperimental
fun Resolver.getJavaClassByName(name: KSName): KSClassDeclaration? {
    val javaName = mapKotlinNameToJava(name) ?: name
    return getClassDeclarationByName(javaName)
}

@KspExperimental
fun Resolver.getJavaClassByName(name: String): KSClassDeclaration? =
    getJavaClassByName(getKSNameFromString(name))

@KspExperimental
fun <T : Annotation> KSAnnotated.getAnnotationsByType(annotationKClass: KClass<T>): Sequence<T> {
    return this.annotations.filter {
        it.shortName.getShortName() == annotationKClass.simpleName && it.annotationType.resolve().declaration
            .qualifiedName?.asString() == annotationKClass.qualifiedName
    }.map { it.toAnnotation(annotationKClass.java) }
}

@KspExperimental
fun <T : Annotation> KSAnnotated.isAnnotationPresent(annotationKClass: KClass<T>): Boolean =
    getAnnotationsByType(annotationKClass).firstOrNull() != null

@KspExperimental
@Suppress("UNCHECKED_CAST")
private fun <T : Annotation> KSAnnotation.toAnnotation(annotationClass: Class<T>): T {
    return Proxy.newProxyInstance(
        annotationClass.classLoader,
        arrayOf(annotationClass),
        createInvocationHandler(annotationClass)
    ) as T
}

@KspExperimental
@Suppress("TooGenericExceptionCaught")
private fun KSAnnotation.createInvocationHandler(clazz: Class<*>): InvocationHandler {
    val cache = ConcurrentHashMap<Pair<Class<*>, Any>, Any>(arguments.size)
    return InvocationHandler { proxy, method, _ ->
        if (method.name == "toString" && arguments.none { it.name?.asString() == "toString" }) {
            clazz.canonicalName +
                arguments.map { argument: KSValueArgument ->
                    // handles default values for enums otherwise returns null
                    val methodName = argument.name?.asString()
                    val value = proxy.javaClass.methods.find { m -> m.name == methodName }?.invoke(proxy)
                    "$methodName=$value"
                }.toList()
        } else {
            val argument = arguments.first { it.name?.asString() == method.name }
            when (val result = argument.value ?: method.defaultValue) {
                is Proxy -> result
                is List<*> -> {
                    val value = { result.asArray(method, clazz) }
                    cache.getOrPut(Pair(method.returnType, result), value)
                }
                else -> {
                    when {
                        // Workaround for java annotation value array type
                        // https://github.com/google/ksp/issues/1329
                        method.returnType.isArray -> {
                            if (result !is Array<*>) {
                                val value = { result.asArray(method, clazz) }
                                cache.getOrPut(Pair(method.returnType, value), value)
                            } else {
                                throw IllegalStateException("unhandled value type, $ExceptionMessage")
                            }
                        }
                        method.returnType.isEnum -> {
                            val value = { result.asEnum(method.returnType) }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.isAnnotation -> {
                            val value = { (result as KSAnnotation).asAnnotation(method.returnType) }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "java.lang.Class" -> {
                            cache.getOrPut(Pair(method.returnType, result)) {
                                when (result) {
                                    is KSType -> result.asClass(clazz)
                                    // Handles com.intellij.psi.impl.source.PsiImmediateClassType using reflection
                                    // since api doesn't contain a reference to this
                                    else -> Class.forName(
                                        result.javaClass.methods
                                            .first { it.name == "getCanonicalText" }
                                            .invoke(result, false) as String
                                    )
                                }
                            }
                        }
                        method.returnType.name == "byte" -> {
                            val value = { result.asByte() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "short" -> {
                            val value = { result.asShort() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "long" -> {
                            val value = { result.asLong() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "float" -> {
                            val value = { result.asFloat() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "double" -> {
                            val value = { result.asDouble() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        else -> result // original value
                    }
                }
            }
        }
    }
}

@KspExperimental
@Suppress("UNCHECKED_CAST")
private fun KSAnnotation.asAnnotation(
    annotationInterface: Class<*>,
): Any {
    return Proxy.newProxyInstance(
        annotationInterface.classLoader, arrayOf(annotationInterface),
        this.createInvocationHandler(annotationInterface)
    ) as Proxy
}

@KspExperimental
@Suppress("UNCHECKED_CAST")
private fun List<*>.asArray(method: Method, proxyClass: Class<*>) =
    when (method.returnType.componentType.name) {
        "boolean" -> (this as List<Boolean>).toBooleanArray()
        "byte" -> (this as List<Byte>).toByteArray()
        "short" -> (this as List<Short>).toShortArray()
        "char" -> (this as List<Char>).toCharArray()
        "double" -> (this as List<Double>).toDoubleArray()
        "float" -> (this as List<Float>).toFloatArray()
        "int" -> (this as List<Int>).toIntArray()
        "long" -> (this as List<Long>).toLongArray()
        "java.lang.Class" -> (this as List<KSType>).asClasses(proxyClass).toTypedArray()
        "java.lang.String" -> (this as List<String>).toTypedArray()
        else -> { // arrays of enums or annotations
            when {
                method.returnType.componentType.isEnum -> {
                    this.toArray(method) { result -> result.asEnum(method.returnType.componentType) }
                }
                method.returnType.componentType.isAnnotation -> {
                    this.toArray(method) { result ->
                        (result as KSAnnotation).asAnnotation(method.returnType.componentType)
                    }
                }
                else -> throw IllegalStateException("Unable to process type ${method.returnType.componentType.name}")
            }
        }
    }

@Suppress("UNCHECKED_CAST")
private fun List<*>.toArray(method: Method, valueProvider: (Any) -> Any): Array<Any?> {
    val array: Array<Any?> = java.lang.reflect.Array.newInstance(
        method.returnType.componentType,
        this.size
    ) as Array<Any?>
    for (r in 0 until this.size) {
        array[r] = this[r]?.let { valueProvider.invoke(it) }
    }
    return array
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.asEnum(returnType: Class<T>): T =
    returnType.getDeclaredMethod("valueOf", String::class.java)
        .invoke(
            null,
            if (this is KSType) {
                this.declaration.simpleName.getShortName()
            } else if (this is KSClassDeclaration) {
                this.simpleName.getShortName()
            } else {
                this.toString()
            }
        ) as T

private fun Any.asByte(): Byte = if (this is Int) this.toByte() else this as Byte

private fun Any.asShort(): Short = if (this is Int) this.toShort() else this as Short

private fun Any.asLong(): Long = if (this is Int) this.toLong() else this as Long

private fun Any.asFloat(): Float = if (this is Int) this.toFloat() else this as Float

private fun Any.asDouble(): Double = if (this is Int) this.toDouble() else this as Double

// for Class/KClass member
@KspExperimental
class KSTypeNotPresentException(val ksType: KSType, cause: Throwable) : RuntimeException(cause)
// for Class[]/Array<KClass<*>> member.
@KspExperimental
class KSTypesNotPresentException(val ksTypes: List<KSType>, cause: Throwable) : RuntimeException(cause)

@KspExperimental
private fun KSType.asClass(proxyClass: Class<*>) = try {
    Class.forName(this.declaration.qualifiedName!!.asString(), true, proxyClass.classLoader)
} catch (e: Exception) {
    throw KSTypeNotPresentException(this, e)
}

@KspExperimental
private fun List<KSType>.asClasses(proxyClass: Class<*>) = try {
    this.map { type -> type.asClass(proxyClass) }
} catch (e: Exception) {
    throw KSTypesNotPresentException(this, e)
}

fun KSValueArgument.isDefault() = origin == Origin.SYNTHETIC

@KspExperimental
private fun Any.asArray(method: Method, proxyClass: Class<*>) = listOf(this).asArray(method, proxyClass)
