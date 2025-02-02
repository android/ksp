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

package com.google.devtools.ksp.symbol.impl.binary

import com.google.devtools.ksp.*
import com.google.devtools.ksp.common.memoized
import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import com.google.devtools.ksp.symbol.impl.java.KSFunctionDeclarationJavaImpl
import com.google.devtools.ksp.symbol.impl.java.KSPropertyDeclarationJavaImpl
import com.google.devtools.ksp.symbol.impl.kotlin.*
import com.google.devtools.ksp.symbol.impl.replaceTypeArguments
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.calls.tower.isSynthesized
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.descriptors.ClassKind as KtClassKind

class KSClassDeclarationDescriptorImpl private constructor(val descriptor: ClassDescriptor) :
    KSClassDeclaration,
    KSDeclarationDescriptorImpl(descriptor),
    KSExpectActual by KSExpectActualDescriptorImpl(descriptor) {
    companion object : KSObjectCache<ClassDescriptor, KSClassDeclarationDescriptorImpl>() {
        fun getCached(descriptor: ClassDescriptor) = cache.getOrPut(descriptor) {
            KSClassDeclarationDescriptorImpl(descriptor)
        }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val classKind: ClassKind by lazy {
        when (descriptor.kind) {
            KtClassKind.INTERFACE -> ClassKind.INTERFACE
            KtClassKind.CLASS -> ClassKind.CLASS
            KtClassKind.OBJECT -> ClassKind.OBJECT
            KtClassKind.ENUM_CLASS -> ClassKind.ENUM_CLASS
            KtClassKind.ENUM_ENTRY -> ClassKind.ENUM_ENTRY
            KtClassKind.ANNOTATION_CLASS -> ClassKind.ANNOTATION_CLASS
        }
    }

    override val isCompanionObject by lazy {
        descriptor.isCompanionObject
    }

    override fun getSealedSubclasses(): Sequence<KSClassDeclaration> {
        return descriptor.sealedSubclassesSequence()
    }

    override fun getAllFunctions(): Sequence<KSFunctionDeclaration> = descriptor.getAllFunctions()

    override fun getAllProperties(): Sequence<KSPropertyDeclaration> = descriptor.getAllProperties()

    override val primaryConstructor: KSFunctionDeclaration? by lazy {
        descriptor.unsubstitutedPrimaryConstructor?.let { KSFunctionDeclarationDescriptorImpl.getCached(it) }
    }

    // Workaround for https://github.com/google/ksp/issues/195
    private val mockSerializableType = ResolverImpl.instance!!.mockSerializableType
    private val javaSerializableType = ResolverImpl.instance!!.javaSerializableType

    override val superTypes: Sequence<KSTypeReference> by lazy {

        descriptor.defaultType.constructor.supertypes.asSequence().map { kotlinType ->
            KSTypeReferenceDescriptorImpl.getCached(
                javaSerializableType?.let { if (kotlinType === mockSerializableType) it else kotlinType }
                    ?: kotlinType,
                origin,
                this
            )
        }.memoized()
    }

    override val typeParameters: List<KSTypeParameter> by lazy {
        descriptor.declaredTypeParameters.map { KSTypeParameterDescriptorImpl.getCached(it) }
    }

    override val declarations: Sequence<KSDeclaration> by lazy {
        sequenceOf(
            descriptor.unsubstitutedMemberScope.getDescriptorsFiltered(),
            // FIXME: Support static, synthetic `entries` for enums when the language feature is enabled.
            descriptor.staticScope.getDescriptorsFiltered().filterNot {
                descriptor.kind == KtClassKind.ENUM_CLASS &&
                    it is CallableDescriptor &&
                    it.isSynthesized &&
                    it.name == StandardNames.ENUM_ENTRIES
            },
            descriptor.constructors
        ).flatten()
            .filter {
                it is MemberDescriptor &&
                    it.visibility != DescriptorVisibilities.INHERITED &&
                    it.visibility != DescriptorVisibilities.INVISIBLE_FAKE &&
                    (it !is CallableMemberDescriptor || it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE)
            }
            .map {
                when (it) {
                    is PropertyDescriptor -> KSPropertyDeclarationDescriptorImpl.getCached(it)
                    is FunctionDescriptor -> KSFunctionDeclarationDescriptorImpl.getCached(it)
                    is ClassDescriptor -> getCached(it)
                    else -> throw IllegalStateException("Unexpected descriptor type ${it.javaClass}, $ExceptionMessage")
                }
            }.memoized()
    }

    override val modifiers: Set<Modifier> by lazy {
        val modifiers = mutableSetOf<Modifier>()
        modifiers.addAll(descriptor.toKSModifiers())
        if (descriptor.isData) {
            modifiers.add(Modifier.DATA)
        }
        if (descriptor.isInline) {
            modifiers.add(Modifier.INLINE)
        }
        if (descriptor.kind == KtClassKind.ANNOTATION_CLASS) {
            modifiers.add(Modifier.ANNOTATION)
        }
        if (descriptor.isInner) {
            modifiers.add(Modifier.INNER)
        }
        if (descriptor.isFun) {
            modifiers.add(Modifier.FUN)
        }
        if (descriptor.isValue) {
            modifiers.add(Modifier.VALUE)
        }
        modifiers
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType =
        descriptor.defaultType.replaceTypeArguments(typeArguments)

    override fun asStarProjectedType(): KSType {
        return getKSTypeCached(descriptor.defaultType.replaceArgumentsWithStarProjections())
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }
}

internal fun ClassDescriptor.getAllFunctions(): Sequence<KSFunctionDeclaration> {
    ResolverImpl.instance!!.incrementalContext.recordLookupForGetAllFunctions(this)
    val functionDescriptors = unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS)
        .asSequence()
        .filter { (it as FunctionDescriptor).visibility != DescriptorVisibilities.INVISIBLE_FAKE }
    return functionDescriptors.plus(constructors).map {
        when (val psi = it.findPsi()) {
            is KtFunction -> KSFunctionDeclarationImpl.getCached(psi)
            is PsiMethod -> KSFunctionDeclarationJavaImpl.getCached(psi)
            else -> KSFunctionDeclarationDescriptorImpl.getCached(it as FunctionDescriptor)
        }
    }
}

internal fun ClassDescriptor.getAllProperties(): Sequence<KSPropertyDeclaration> {
    ResolverImpl.instance!!.incrementalContext.recordLookupForGetAllProperties(this)
    return unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.VARIABLES).asSequence()
        .filter { (it as PropertyDescriptor).visibility != DescriptorVisibilities.INVISIBLE_FAKE }
        .map {
            when (val psi = it.findPsi()) {
                is KtParameter -> KSPropertyDeclarationParameterImpl.getCached(psi)
                is KtProperty -> KSPropertyDeclarationImpl.getCached(psi)
                is PsiField -> KSPropertyDeclarationJavaImpl.getCached(psi)
                else -> KSPropertyDeclarationDescriptorImpl.getCached(it as PropertyDescriptor)
            }
        }
}

internal fun ClassDescriptor.sealedSubclassesSequence(): Sequence<KSClassDeclaration> {
    // TODO record incremental subclass lookups in Kotlin 1.5.x?
    return sealedSubclasses
        .asSequence()
        .map { sealedSubClass ->
            when (val psi = sealedSubClass.findPsi()) {
                is KtClassOrObject -> KSClassDeclarationImpl.getCached(psi)
                else -> KSClassDeclarationDescriptorImpl.getCached(sealedSubClass)
            }
        }
}
