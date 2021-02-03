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
fun Resolver.getClassDeclarationByName(name: String): KSClassDeclaration? = getClassDeclarationByName(getKSNameFromString(name))

/**
 * Get functions directly declared inside the class declaration.
 *
 * What are included: member functions, constructors, extension functions declared inside it, etc.
 * What are NOT included: inherited functions, extension functions declared outside it.
 */
fun KSClassDeclaration.getDeclaredFunctions(): List<KSFunctionDeclaration> {
    return this.declarations.filterIsInstance<KSFunctionDeclaration>()
}

/**
 * Get properties directly declared inside the class declaration.
 *
 * What are included: member properties, extension properties declared inside it, etc.
 * What are NOT included: inherited properties, extension properties declared outside it.
 */
fun KSClassDeclaration.getDeclaredProperties(): List<KSPropertyDeclaration> {
    return this.declarations.filterIsInstance<KSPropertyDeclaration>()
}

fun KSClassDeclaration.getConstructors(): List<KSFunctionDeclaration> {
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
fun KSNode.validate(predicate: (KSNode?, KSNode) -> Boolean = { _, _-> true } ): Boolean {
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
        this.modifiers.contains(Modifier.OVERRIDE) -> {
            when (this) {
                is KSFunctionDeclaration -> this.findOverridee()?.getVisibility()
                is KSPropertyDeclaration -> this.findOverridee()?.getVisibility()
                else -> null
            } ?: Visibility.PUBLIC
        }
        this.isLocal() -> Visibility.LOCAL
        this.modifiers.contains(Modifier.PRIVATE) -> Visibility.PRIVATE
        this.modifiers.contains(Modifier.PROTECTED) || this.modifiers.contains(Modifier.OVERRIDE) -> Visibility.PROTECTED
        this.modifiers.contains(Modifier.INTERNAL) -> Visibility.INTERNAL
        this.modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
        else -> if (this.origin != Origin.JAVA) Visibility.PUBLIC else Visibility.JAVA_PACKAGE
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

fun KSClassDeclaration.isAbstract() = this.classKind == ClassKind.INTERFACE || this.modifiers.contains(Modifier.ABSTRACT)

fun KSPropertyDeclaration.isAbstract() = this.modifiers.contains(Modifier.ABSTRACT)
        || ((this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE && this.getter == null && this.setter == null)

fun KSDeclaration.isOpen() = !this.isLocal()
        && ((this as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE
        || this.modifiers.contains(Modifier.OVERRIDE)
        || this.modifiers.contains(Modifier.ABSTRACT)
        || this.modifiers.contains(Modifier.OPEN)
        || (this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE
        || (!this.modifiers.contains(Modifier.FINAL) && this.origin == Origin.JAVA)
        )

fun KSDeclaration.isPublic() = this.getVisibility() == Visibility.PUBLIC

fun KSDeclaration.isProtected() = this.getVisibility() == Visibility.PROTECTED

fun KSDeclaration.isInternal() = this.modifiers.contains(Modifier.INTERNAL)

fun KSDeclaration.isPrivate() = this.modifiers.contains(Modifier.PRIVATE)

fun KSDeclaration.isJavaPackagePrivate() = this.getVisibility() == Visibility.JAVA_PACKAGE

fun KSDeclaration.closestClassDeclaration(): KSClassDeclaration? {
    if (this is KSClassDeclaration) {
        return this
    } else {
        return this.parentDeclaration?.closestClassDeclaration()
    }
}

fun KSAnnotated.findAnnotationFromUseSiteTarget(): Collection<KSAnnotation> {
    return when (this) {
        is KSPropertyGetter -> this.receiver.annotations.filter { it.useSiteTarget == AnnotationUseSiteTarget.GET }
        is KSPropertySetter -> this.receiver.annotations.filter { it.useSiteTarget == AnnotationUseSiteTarget.SET }
        else -> emptyList()
    }
}


// TODO: cross module visibility is not handled
fun KSDeclaration.isVisibleFrom(other: KSDeclaration): Boolean {
    fun KSDeclaration.isSamePackage(other: KSDeclaration): Boolean = this.containingFile?.packageName == other.containingFile?.packageName

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
        (other.isLocal() && other.parentDeclarationsForLocal().contains(this.parentDeclaration))
                || this.parentDeclaration == other.parentDeclaration
                || this.parentDeclaration == other
                || (
                this.parentDeclaration == null
                        && other.parentDeclaration == null
                        && this.containingFile == other.containingFile
                )

    return when {
        // locals are limited to lexical scope
        this.isLocal() -> this.parentDeclarationsForLocal().contains(other)
        // file visibility or member
        // TODO: address nested class.
        this.isPrivate() -> this.isVisibleInPrivate(other)
        this.isPublic() -> true
        this.isInternal() && other.containingFile != null && this.containingFile != null -> true
        // Non-private symbols in Java are always visible in same package.
        this.origin == Origin.JAVA -> this.isSamePackage(other)
        this.isProtected() -> this.isVisibleInPrivate(other) || other.closestClassDeclaration()?.let {
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
