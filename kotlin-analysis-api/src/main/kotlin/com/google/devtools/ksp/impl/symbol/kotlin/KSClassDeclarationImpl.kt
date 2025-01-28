/*
 * Copyright 2022 Google LLC
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
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

package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.common.errorTypeOnInconsistentArguments
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.common.impl.KSTypeReferenceSyntheticImpl
import com.google.devtools.ksp.impl.ResolverAAImpl
import com.google.devtools.ksp.impl.recordGetSealedSubclasses
import com.google.devtools.ksp.impl.recordLookup
import com.google.devtools.ksp.impl.recordLookupForGetAllFunctions
import com.google.devtools.ksp.impl.recordLookupForGetAllProperties
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSTypeReferenceResolvedImpl
import com.google.devtools.ksp.symbol.*
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.impl.base.types.KaBaseStarTypeProjection
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.psi.KtClassOrObject

class KSClassDeclarationImpl private constructor(internal val ktClassOrObjectSymbol: KaClassSymbol) :
    KSClassDeclaration,
    AbstractKSDeclarationImpl(ktClassOrObjectSymbol),
    KSExpectActual by KSExpectActualImpl(ktClassOrObjectSymbol) {
    companion object : KSObjectCache<KaClassSymbol, KSClassDeclarationImpl>() {
        fun getCached(ktClassOrObjectSymbol: KaClassSymbol) =
            cache.getOrPut(ktClassOrObjectSymbol) { KSClassDeclarationImpl(ktClassOrObjectSymbol) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val qualifiedName: KSName? by lazy {
        ktClassOrObjectSymbol.classId?.asFqNameString()?.let { KSNameImpl.getCached(it) }
    }

    override val classKind: ClassKind by lazy {
        when (ktClassOrObjectSymbol.classKind) {
            KaClassKind.CLASS -> ClassKind.CLASS
            KaClassKind.ENUM_CLASS -> ClassKind.ENUM_CLASS
            KaClassKind.ANNOTATION_CLASS -> ClassKind.ANNOTATION_CLASS
            KaClassKind.INTERFACE -> ClassKind.INTERFACE
            KaClassKind.COMPANION_OBJECT, KaClassKind.ANONYMOUS_OBJECT, KaClassKind.OBJECT -> ClassKind.OBJECT
        }
    }

    override val primaryConstructor: KSFunctionDeclaration? by lazy {
        if (ktClassOrObjectSymbol.origin == KaSymbolOrigin.JAVA_SOURCE) {
            null
        } else {
            analyze {
                ktClassOrObjectSymbol.memberScope.constructors.singleOrNull { it.isPrimary }?.let {
                    KSFunctionDeclarationImpl.getCached(it)
                }
            }
        }
    }

    override val superTypes: Sequence<KSTypeReference> by lazy {
        (ktClassOrObjectSymbol.psiIfSource() as? KtClassOrObject)?.let { classOrObject ->
            if (classKind == ClassKind.ANNOTATION_CLASS || classKind == ClassKind.ENUM_CLASS) {
                null
            } else {
                classOrObject.superTypeListEntries.map {
                    KSTypeReferenceImpl.getCached(it.typeReference!!, this)
                }.asSequence().ifEmpty {
                    sequenceOf(
                        KSTypeReferenceSyntheticImpl.getCached(ResolverAAImpl.instance.builtIns.anyType, this)
                    )
                }
            }
        } ?: analyze {
            val supers = ktClassOrObjectSymbol.superTypes.mapIndexed { index, type ->
                KSTypeReferenceResolvedImpl.getCached(type, this@KSClassDeclarationImpl, index)
            }
            // AA is returning additional kotlin.Any for java classes, explicitly extending kotlin.Any will result in
            // compile error, therefore filtering by name should work.
            // TODO: reconsider how to model super types for interface.
            if (supers.size > 1) {
                supers.filterNot { it.resolve().declaration.qualifiedName?.asString() == "kotlin.Any" }
            } else {
                supers
            }.asSequence()
        }
    }

    override val isCompanionObject: Boolean by lazy {
        ktClassOrObjectSymbol.classKind == KaClassKind.COMPANION_OBJECT
    }

    override fun getSealedSubclasses(): Sequence<KSClassDeclaration> {
        if (!modifiers.contains(Modifier.SEALED)) return emptySequence()
        recordGetSealedSubclasses(this)
        return (ktClassOrObjectSymbol as? KaNamedClassSymbol)?.let {
            analyze {
                it.sealedClassInheritors.map { getCached(it) }.asSequence()
            }
        } ?: emptySequence()
    }

    override fun getAllFunctions(): Sequence<KSFunctionDeclaration> {
        ktClassOrObjectSymbol.superTypes.forEach { recordLookup(it, this) }
        recordLookupForGetAllFunctions(ktClassOrObjectSymbol.superTypes)
        return ktClassOrObjectSymbol.getAllFunctions()
    }

    override fun getAllProperties(): Sequence<KSPropertyDeclaration> {
        ktClassOrObjectSymbol.superTypes.forEach { recordLookup(it, this) }
        recordLookupForGetAllProperties(ktClassOrObjectSymbol.superTypes)
        return ktClassOrObjectSymbol.getAllProperties()
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        errorTypeOnInconsistentArguments(
            arguments = typeArguments,
            placeholdersProvider = { asStarProjectedType().arguments },
            withCorrectedArguments = ::asType,
            errorType = ::KSErrorType,
        )?.let { error -> return error }
        return analyze {
            ktClassOrObjectSymbol.tryResolveToTypePhase()
            if (typeArguments.isEmpty()) {
                // Resolving a class symbol also resolves its type parameters.
                typeParameters.map { buildTypeParameterType((it as KSTypeParameterImpl).ktTypeParameterSymbol) }
                    .let { typeParameterTypes ->
                        buildClassType(ktClassOrObjectSymbol) {
                            typeParameterTypes.forEach { argument(it) }
                        }
                    }
            } else {
                buildClassType(ktClassOrObjectSymbol) {
                    typeArguments.forEach { argument(it.toKtTypeProjection()) }
                }
            }.let { KSTypeImpl.getCached(it) }
        }
    }

    @OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
    override fun asStarProjectedType(): KSType {
        return analyze {
            KSTypeImpl.getCached(
                useSiteSession.buildClassType(ktClassOrObjectSymbol.tryResolveToTypePhase()) {
                    var current: KSNode? = this@KSClassDeclarationImpl
                    while (current is KSClassDeclarationImpl) {
                        current.ktClassOrObjectSymbol.typeParameters.forEach { _ ->
                            argument(
                                KaBaseStarTypeProjection(
                                    (current as KSClassDeclarationImpl).ktClassOrObjectSymbol.token
                                )
                            )
                        }
                        current = if (Modifier.INNER in current.modifiers) {
                            current.parent
                        } else {
                            null
                        }
                    }
                }
            )
        }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }

    override val declarations: Sequence<KSDeclaration> by lazy {
        val decls = ktClassOrObjectSymbol.declarations()
        if (origin == Origin.JAVA && classKind != ClassKind.ANNOTATION_CLASS) {
            buildList {
                decls.forEach { decl ->
                    if (decl is KSPropertyDeclarationImpl && decl.ktPropertySymbol is KaSyntheticJavaPropertySymbol) {
                        decl.getter?.let {
                            add(
                                KSFunctionDeclarationImpl.getCached(
                                    (it as KSPropertyAccessorImpl).ktPropertyAccessorSymbol
                                )
                            )
                        }
                        decl.setter?.let {
                            add(
                                KSFunctionDeclarationImpl.getCached(
                                    (it as KSPropertyAccessorImpl).ktPropertyAccessorSymbol
                                )
                            )
                        }
                    } else {
                        add(decl)
                    }
                }
            }.asSequence()
        } else decls
    }

    override fun defer(): Restorable? {
        return ktClassOrObjectSymbol.defer(::getCached)
    }
}

internal fun KaClassSymbol.toModifiers(): Set<Modifier> {
    val result = mutableSetOf<Modifier>()
    if (this is KaNamedClassSymbol) {
        result.add(modality.toModifier())
        if (visibility != KaSymbolVisibility.PACKAGE_PRIVATE) {
            result.add(visibility.toModifier())
        }
        if (isFun) {
            result.add(Modifier.FUN)
        }
        if (isInline) {
            result.add(Modifier.INLINE)
        }
        if (isData) {
            result.add(Modifier.DATA)
        }
        if (isExternal) {
            result.add(Modifier.EXTERNAL)
        }
        if (isInner) {
            result.add(Modifier.INNER)
        }
    }
    if (classKind == KaClassKind.ENUM_CLASS) {
        result.add(Modifier.ENUM)
    }
    return result
}
