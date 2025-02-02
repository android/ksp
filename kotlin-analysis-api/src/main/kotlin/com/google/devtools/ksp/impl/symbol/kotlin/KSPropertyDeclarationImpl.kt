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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.impl.ResolverAAImpl
import com.google.devtools.ksp.impl.recordLookupForPropertyOrMethod
import com.google.devtools.ksp.impl.recordLookupWithSupertypes
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSAnnotationResolvedImpl
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSTypeReferenceResolvedImpl
import com.google.devtools.ksp.impl.symbol.util.BinaryClassInfoCache
import com.google.devtools.ksp.symbol.*
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.api.KaConstantInitializerValue
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.fir.symbols.KaFirKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtProperty

class KSPropertyDeclarationImpl private constructor(internal val ktPropertySymbol: KaPropertySymbol) :
    KSPropertyDeclaration,
    AbstractKSDeclarationImpl(ktPropertySymbol),
    KSExpectActual by KSExpectActualImpl(ktPropertySymbol) {
    companion object : KSObjectCache<KaPropertySymbol, KSPropertyDeclarationImpl>() {
        fun getCached(ktPropertySymbol: KaPropertySymbol) =
            cache.getOrPut(ktPropertySymbol) { KSPropertyDeclarationImpl(ktPropertySymbol) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val originalAnnotations: Sequence<KSAnnotation>
        get() = annotations

    override val annotations: Sequence<KSAnnotation> by lazy {
        ktPropertySymbol.annotations.asSequence()
            .filter { !it.isUseSiteTargetAnnotation() }
            .map { KSAnnotationResolvedImpl.getCached(it, this) }
            .plus(
                if (ktPropertySymbol.isFromPrimaryConstructor) {
                    (parentDeclaration as? KSClassDeclaration)?.primaryConstructor?.parameters
                        ?.singleOrNull { it.name == simpleName }?.annotations ?: emptySequence()
                } else {
                    emptySequence()
                }
            ).filterNot { valueParameterAnnotation ->
                valueParameterAnnotation.annotationType.resolve().declaration.annotations.any { metaAnnotation ->
                    metaAnnotation.annotationType.resolve().declaration.qualifiedName
                        ?.asString() == "kotlin.annotation.Target" &&
                        (metaAnnotation.arguments.singleOrNull()?.value as? ArrayList<*>)?.any {
                        (it as? KSClassDeclaration)?.qualifiedName
                            ?.asString() == "kotlin.annotation.AnnotationTarget.VALUE_PARAMETER"
                    } ?: false
                }
            }.plus(
                // TODO: optimize for psi
                ktPropertySymbol.backingFieldSymbol?.annotations
                    ?.map { KSAnnotationResolvedImpl.getCached(it, this@KSPropertyDeclarationImpl) } ?: emptyList()
            )
    }

    override val getter: KSPropertyGetter? by lazy {
        if (ktPropertySymbol.psi is PsiClass) {
            null
        } else {
            ktPropertySymbol.getter?.let { KSPropertyGetterImpl.getCached(this, it) }
        }
    }

    override val setter: KSPropertySetter? by lazy {
        if (ktPropertySymbol.psi is PsiClass) {
            null
        } else {
            ktPropertySymbol.setter?.let { KSPropertySetterImpl.getCached(this, it) }
        }
    }

    override val extensionReceiver: KSTypeReference? by lazy {
        (ktPropertySymbol.psiIfSource() as? KtProperty)?.receiverTypeReference
            ?.let {
                // receivers are modeled as parameter in AA therefore annotations are stored in
                // the corresponding receiver parameter, need to pass it to the `KSTypeReferenceImpl`
                KSTypeReferenceImpl.getCached(
                    it,
                    this,
                    ktPropertySymbol.receiverParameter?.annotations ?: emptyList()
                )
            }
            ?: ktPropertySymbol.receiverType?.let {
                KSTypeReferenceResolvedImpl.getCached(
                    it,
                    this@KSPropertyDeclarationImpl,
                    -1,
                    ktPropertySymbol.receiverParameter?.annotations ?: emptyList()
                )
            }
    }

    override val type: KSTypeReference by lazy {
        (ktPropertySymbol.psiIfSource() as? KtProperty)?.typeReference?.let { KSTypeReferenceImpl.getCached(it, this) }
            ?: KSTypeReferenceResolvedImpl.getCached(ktPropertySymbol.returnType, this@KSPropertyDeclarationImpl)
    }

    override val isMutable: Boolean by lazy {
        !ktPropertySymbol.isVal
    }

    @OptIn(KaExperimentalApi::class)
    override val hasBackingField: Boolean by lazy {
        if (origin == Origin.KOTLIN_LIB || origin == Origin.JAVA_LIB) {
            when {
                ktPropertySymbol.receiverParameter != null -> false
                ktPropertySymbol.initializer is KaConstantInitializerValue -> true
                (ktPropertySymbol as? KaKotlinPropertySymbol)?.isLateInit == true -> true
                ktPropertySymbol.modality == KaSymbolModality.ABSTRACT -> false
                else -> {
                    if (!ResolverAAImpl.instance.isJvm)
                        return@lazy ktPropertySymbol.hasBackingField
                    val classId = when (
                        val containerSource =
                            (ktPropertySymbol as? KaFirKotlinPropertySymbol<*>)?.firSymbol?.containerSource
                    ) {
                        is JvmPackagePartSource -> containerSource.classId
                        is KotlinJvmBinarySourceElement -> containerSource.binaryClass.classId
                        else -> null
                    } ?: return@lazy ktPropertySymbol.hasBackingField
                    val fileManager = ResolverAAImpl.instance.javaFileManager
                    BinaryClassInfoCache.getCached(classId, fileManager)
                        ?.fieldAccFlags?.containsKey(simpleName.asString()) ?: ktPropertySymbol.hasBackingField
                }
            }
        } else {
            ktPropertySymbol.hasBackingField
        }
    }

    override fun isDelegated(): Boolean {
        return ktPropertySymbol.isDelegatedProperty
    }

    override fun findOverridee(): KSPropertyDeclaration? {
        closestClassDeclaration()?.asStarProjectedType()?.let {
            recordLookupWithSupertypes((it as KSTypeImpl).type)
        }
        recordLookupForPropertyOrMethod(this)
        return analyze {
            ktPropertySymbol.directlyOverriddenSymbols.firstOrNull()
                ?.fakeOverrideOriginal?.toKSDeclaration() as? KSPropertyDeclaration
        }?.also { recordLookupForPropertyOrMethod(it) }
    }

    override fun asMemberOf(containing: KSType): KSType {
        return ResolverAAImpl.instance.computeAsMemberOf(this, containing)
    }

    override val qualifiedName: KSName? by lazy {
        ktPropertySymbol.callableId?.asSingleFqName()?.asString()?.let { KSNameImpl.getCached(it) }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyDeclaration(this, data)
    }

    override fun defer(): Restorable? {
        return ktPropertySymbol.defer(::getCached)
    }
}

internal fun KaAnnotation.isUseSiteTargetAnnotation(): Boolean {
    return this.useSiteTarget?.let {
        it == AnnotationUseSiteTarget.PROPERTY_GETTER ||
            it == AnnotationUseSiteTarget.PROPERTY_SETTER ||
            it == AnnotationUseSiteTarget.SETTER_PARAMETER ||
            it == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
    } ?: false
}
internal fun KtAnnotationEntry.isUseSiteTargetAnnotation(): Boolean {
    return this.useSiteTarget?.getAnnotationUseSiteTarget()?.let {
        it == AnnotationUseSiteTarget.PROPERTY_GETTER ||
            it == AnnotationUseSiteTarget.PROPERTY_SETTER ||
            it == AnnotationUseSiteTarget.SETTER_PARAMETER ||
            it == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER ||
            it == AnnotationUseSiteTarget.FIELD
    } ?: false
}
internal fun KaPropertySymbol.toModifiers(): Set<Modifier> {
    val result = mutableSetOf<Modifier>()
    if (visibility != KaSymbolVisibility.PACKAGE_PRIVATE) {
        result.add(visibility.toModifier())
    }
    if (isOverride) {
        result.add(Modifier.OVERRIDE)
    }
    if (isStatic) {
        result.add(Modifier.JAVA_STATIC)
        result.add(Modifier.FINAL)
    }
    // Analysis API returns open for static members which should be ignored.
    if (!isStatic || modality != KaSymbolModality.OPEN) {
        result.add(modality.toModifier())
    }

    if (this is KaKotlinPropertySymbol) {
        if (isLateInit) {
            result.add(Modifier.LATEINIT)
        }
        if (isConst) {
            result.add(Modifier.CONST)
        }
    }
    return result
}
