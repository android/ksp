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

import com.google.devtools.ksp.*
import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.impl.ResolverAAImpl
import com.google.devtools.ksp.impl.recordLookupForPropertyOrMethod
import com.google.devtools.ksp.impl.recordLookupWithSupertypes
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSTypeReferenceResolvedImpl
import com.google.devtools.ksp.symbol.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction

class KSFunctionDeclarationImpl private constructor(internal val ktFunctionSymbol: KaFunctionSymbol) :
    KSFunctionDeclaration,
    AbstractKSDeclarationImpl(ktFunctionSymbol),
    KSExpectActual by KSExpectActualImpl(ktFunctionSymbol) {
    companion object : KSObjectCache<KaFunctionSymbol, KSFunctionDeclarationImpl>() {
        fun getCached(ktFunctionSymbol: KaFunctionSymbol) =
            cache.getOrPut(ktFunctionSymbol) { KSFunctionDeclarationImpl(ktFunctionSymbol) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val functionKind: FunctionKind by lazy {
        when (ktFunctionSymbol.location) {
            KaSymbolLocation.CLASS -> FunctionKind.MEMBER
            KaSymbolLocation.TOP_LEVEL -> FunctionKind.TOP_LEVEL
            else -> throw IllegalStateException("Unexpected location ${ktFunctionSymbol.location}")
        }
    }

    override val isAbstract: Boolean by lazy {
        (ktFunctionSymbol as? KaNamedFunctionSymbol)?.modality == KaSymbolModality.ABSTRACT
    }

    override val modifiers: Set<Modifier> by lazy {
        if (isConstructor()) {
            val ksClassDeclaration = parentDeclaration as KSClassDeclaration
            if (ksClassDeclaration.classKind == ClassKind.ENUM_CLASS) {
                setOf(Modifier.FINAL, Modifier.PRIVATE)
            } else if (isSyntheticConstructor() && ksClassDeclaration.isPublic()) {
                setOf(Modifier.FINAL, Modifier.PUBLIC)
            } else {
                super.modifiers + Modifier.FINAL
            }
        } else {
            super.modifiers
        }
    }

    override val extensionReceiver: KSTypeReference? by lazy {
        analyze {
            if (!ktFunctionSymbol.isExtension) {
                null
            } else {
                (ktFunctionSymbol.psiIfSource() as? KtFunction)?.receiverTypeReference
                    ?.let {
                        // receivers are modeled as parameter in AA therefore annotations are stored in
                        // the corresponding receiver parameter, need to pass it to the `KSTypeReferenceImpl`
                        KSTypeReferenceImpl.getCached(
                            it,
                            this@KSFunctionDeclarationImpl,
                            ktFunctionSymbol.receiverParameter?.annotations ?: emptyList()
                        )
                    }
                    ?: ktFunctionSymbol.receiverType?.let {
                        KSTypeReferenceResolvedImpl.getCached(
                            it,
                            this@KSFunctionDeclarationImpl,
                            -1,
                            ktFunctionSymbol.receiverParameter?.annotations ?: emptyList()
                        )
                    }
            }
        }
    }

    override val returnType: KSTypeReference? by lazy {
        (ktFunctionSymbol.psiIfSource() as? KtFunction)?.typeReference?.let { KSTypeReferenceImpl.getCached(it, this) }
            ?: analyze {
                // Constructors
                if (ktFunctionSymbol is KaConstructorSymbol) {
                    ((parentDeclaration as KSClassDeclaration).asStarProjectedType() as KSTypeImpl).type
                } else {
                    ktFunctionSymbol.returnType
                }.let { KSTypeReferenceResolvedImpl.getCached(it, this@KSFunctionDeclarationImpl) }
            }
    }

    override val parameters: List<KSValueParameter> by lazy {
        ktFunctionSymbol.valueParameters.map { KSValueParameterImpl.getCached(it, this) }
    }

    override fun findOverridee(): KSDeclaration? {
        closestClassDeclaration()?.asStarProjectedType()?.let {
            recordLookupWithSupertypes((it as KSTypeImpl).type)
        }
        recordLookupForPropertyOrMethod(this)
        return analyze {
            if (ktFunctionSymbol is KaPropertyAccessorSymbol) {
                (parentDeclaration as? KSPropertyDeclarationImpl)?.ktPropertySymbol
            } else {
                ktFunctionSymbol
            }?.directlyOverriddenSymbols?.firstOrNull()?.fakeOverrideOriginal?.toKSDeclaration()
        }?.also { recordLookupForPropertyOrMethod(it) }
    }

    override fun asMemberOf(containing: KSType): KSFunction {
        return ResolverAAImpl.instance.computeAsMemberOf(this, containing)
    }

    override val simpleName: KSName by lazy {
        when (ktFunctionSymbol) {
            is KaNamedFunctionSymbol -> KSNameImpl.getCached(ktFunctionSymbol.name.asString())
            is KaPropertyAccessorSymbol -> KSNameImpl.getCached((ktFunctionSymbol.psi as PsiMethod).name)
            is KaConstructorSymbol -> KSNameImpl.getCached("<init>")
            else -> throw IllegalStateException("Unexpected function symbol type ${ktFunctionSymbol.javaClass}")
        }
    }

    override val qualifiedName: KSName? by lazy {
        ktFunctionSymbol.callableId?.asSingleFqName()?.asString()?.let { KSNameImpl.getCached(it) }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitFunctionDeclaration(this, data)
    }

    override val declarations: Sequence<KSDeclaration> by lazy {
        val psi = ktFunctionSymbol.psi as? KtFunction ?: return@lazy emptySequence()
        if (!psi.hasBlockBody()) {
            emptySequence()
        } else {
            psi.bodyBlockExpression?.statements?.asSequence()?.filterIsInstance<KtDeclaration>()?.mapNotNull {
                analyze {
                    it.symbol.toKSDeclaration()
                }
            } ?: emptySequence()
        }
    }

    override val origin: Origin by lazy {
        // Override origin for java synthetic constructors.
        if (
            ktFunctionSymbol.origin == KaSymbolOrigin.JAVA_SOURCE &&
            (ktFunctionSymbol.psi == null || ktFunctionSymbol.psi is PsiClass)
        ) {
            Origin.SYNTHETIC
        } else {
            super.origin
        }
    }

    private fun isSyntheticConstructor(): Boolean {
        return isConstructor() && (
            origin == Origin.SYNTHETIC ||
                (origin == Origin.JAVA && ktFunctionSymbol.psi == null || ktFunctionSymbol.psi is PsiClass)
            )
    }

    override val annotations: Sequence<KSAnnotation> by lazy {
        if (isSyntheticConstructor()) {
            emptySequence()
        } else {
            super.annotations
        }
    }

    override fun toString(): String {
        // TODO: fix origin for implicit Java constructor in AA
        // TODO: should we change the toString() behavior for synthetic constructors?
        return if (isSyntheticConstructor()) {
            "synthetic constructor for ${this.parentDeclaration}"
        } else {
            this.simpleName.asString()
        }
    }

    override val docString: String? by lazy {
        if (isSyntheticConstructor()) {
            parentDeclaration?.docString
        } else {
            super.docString
        }
    }

    override fun defer(): Restorable? {
        return ktFunctionSymbol.defer(::getCached)
    }
}

internal fun KaFunctionSymbol.toModifiers(): Set<Modifier> {
    val result = mutableSetOf<Modifier>()
    when (this) {
        is KaConstructorSymbol -> {
            if (visibility != KaSymbolVisibility.PACKAGE_PRIVATE) {
                result.add(visibility.toModifier())
            }
            result.add(Modifier.FINAL)
        }
        is KaNamedFunctionSymbol -> {
            if (visibility != KaSymbolVisibility.PACKAGE_PRIVATE) {
                result.add(visibility.toModifier())
            }
            if (!isStatic || modality != KaSymbolModality.OPEN) {
                result.add(modality.toModifier())
            }
            if (isExternal) {
                result.add(Modifier.EXTERNAL)
            }
            if (isInfix) {
                result.add(Modifier.INFIX)
            }
            if (isInline) {
                result.add(Modifier.INLINE)
            }
            if (isStatic) {
                result.add(Modifier.JAVA_STATIC)
                result.add(Modifier.FINAL)
            }
            if (isSuspend) {
                result.add(Modifier.SUSPEND)
            }
            if (isOperator) {
                result.add(Modifier.OPERATOR)
            }
            if (isOperator) {
                result.add(Modifier.OVERRIDE)
            }
        }
        is KaPropertyAccessorSymbol -> {
            if (visibility != KaSymbolVisibility.PACKAGE_PRIVATE) {
                result.add(visibility.toModifier())
            }
            if (modality != KaSymbolModality.OPEN) {
                result.add(modality.toModifier())
            }
        }
        else -> Unit
    }
    return result
}
