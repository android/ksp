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

import com.google.devtools.ksp.ExceptionMessage
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.KSObjectCache
import com.google.devtools.ksp.symbol.impl.kotlin.getKSTypeCached
import com.google.devtools.ksp.symbol.impl.replaceTypeArguments
import com.google.devtools.ksp.symbol.impl.toKSModifiers
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.descriptors.ClassKind as KtClassKind

class KSClassDeclarationDescriptorImpl private constructor(val descriptor: ClassDescriptor) : KSClassDeclaration,
    KSDeclarationDescriptorImpl(descriptor),
    KSExpectActual by KSExpectActualDescriptorImpl(descriptor) {
    companion object : KSObjectCache<ClassDescriptor, KSClassDeclarationDescriptorImpl>() {
        fun getCached(descriptor: ClassDescriptor) = cache.getOrPut(descriptor) { KSClassDeclarationDescriptorImpl(descriptor) }
    }

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

    override fun getAllFunctions(): List<KSFunctionDeclaration> = descriptor.getAllFunctions()

    override fun getAllProperties(): List<KSPropertyDeclaration> = descriptor.getAllProperties()

    override val primaryConstructor: KSFunctionDeclaration? by lazy {
        descriptor.unsubstitutedPrimaryConstructor?.let { KSFunctionDeclarationDescriptorImpl.getCached(it) }
    }

    // Workaround for https://github.com/google/ksp/issues/195
    private val mockSerializableType = ResolverImpl.instance.mockSerializableType
    private val javaSerializableType = ResolverImpl.instance.javaSerializableType

    override val superTypes: List<KSTypeReference> by lazy {
        descriptor.defaultType.constructor.supertypes.map {
            KSTypeReferenceDescriptorImpl.getCached(
                if (it === mockSerializableType) javaSerializableType else it
            )
        }
    }

    override val typeParameters: List<KSTypeParameter> by lazy {
        descriptor.declaredTypeParameters.map { KSTypeParameterDescriptorImpl.getCached(it) }
    }

    override val declarations: List<KSDeclaration> by lazy {
        listOf(
            descriptor.unsubstitutedMemberScope.getDescriptorsFiltered(),
            descriptor.staticScope.getDescriptorsFiltered(),
            descriptor.constructors
        ).flatten()
            .filter {
                it is MemberDescriptor
                        && it.visibility != DescriptorVisibilities.INHERITED
                        && it.visibility != DescriptorVisibilities.INVISIBLE_FAKE
                        && (it !is CallableMemberDescriptor || it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE)
            }
            .map {
                when (it) {
                    is PropertyDescriptor -> KSPropertyDeclarationDescriptorImpl.getCached(it)
                    is FunctionDescriptor -> KSFunctionDeclarationDescriptorImpl.getCached(it)
                    is ClassDescriptor -> getCached(it)
                    else -> throw IllegalStateException("Unexpected descriptor type ${it.javaClass}, $ExceptionMessage")
                }
            }
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
        modifiers
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType =
        getKSTypeCached(descriptor.defaultType.replaceTypeArguments(typeArguments), typeArguments)

    override fun asStarProjectedType(): KSType {
        return getKSTypeCached(descriptor.defaultType.replaceArgumentsWithStarProjections())
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }
}

internal fun ClassDescriptor.getAllFunctions(): List<KSFunctionDeclaration> {
    ResolverImpl.instance.incrementalContext.recordLookupForGetAllFunctions(this)
    val functionDescriptors = unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS).toList()
            .filter { (it as FunctionDescriptor).visibility != DescriptorVisibilities.INVISIBLE_FAKE }.toMutableList()
    functionDescriptors += constructors
    return functionDescriptors.map { KSFunctionDeclarationDescriptorImpl.getCached(it as FunctionDescriptor) }
}

internal fun ClassDescriptor.getAllProperties(): List<KSPropertyDeclaration> {
    ResolverImpl.instance.incrementalContext.recordLookupForGetAllProperties(this)
    return unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.VARIABLES).toList()
            .filter { (it as PropertyDescriptor).visibility != DescriptorVisibilities.INVISIBLE_FAKE }
            .map { KSPropertyDeclarationDescriptorImpl.getCached(it as PropertyDescriptor) }
}
