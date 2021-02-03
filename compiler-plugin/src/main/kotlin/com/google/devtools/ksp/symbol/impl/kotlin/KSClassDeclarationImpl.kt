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


package com.google.devtools.ksp.symbol.impl.kotlin

import com.google.devtools.ksp.isConstructor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import com.google.devtools.ksp.symbol.impl.binary.getAllFunctions
import com.google.devtools.ksp.symbol.impl.binary.getAllProperties
import com.google.devtools.ksp.symbol.impl.synthetic.KSConstructorSyntheticImpl
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections

class KSClassDeclarationImpl private constructor(val ktClassOrObject: KtClassOrObject) : KSClassDeclaration,
    KSDeclarationImpl(ktClassOrObject),
    KSExpectActual by KSExpectActualImpl(ktClassOrObject) {
    companion object : KSObjectCache<KtClassOrObject, KSClassDeclarationImpl>() {
        fun getCached(ktClassOrObject: KtClassOrObject) = cache.getOrPut(ktClassOrObject) { KSClassDeclarationImpl(ktClassOrObject) }
    }

    override val classKind: ClassKind by lazy {
        ktClassOrObject.getClassType()
    }

    override val isCompanionObject by lazy {
        (ktClassOrObject is KtObjectDeclaration) && (ktClassOrObject.isCompanion())
    }

    override fun getAllFunctions(): List<KSFunctionDeclaration> = descriptor.getAllFunctions()

    override fun getAllProperties(): List<KSPropertyDeclaration> = descriptor.getAllProperties()

    override val declarations: List<KSDeclaration> by lazy {
        val propertiesFromConstructor = primaryConstructor?.parameters
            ?.filter { it.isVar || it.isVal }
            ?.map { KSPropertyDeclarationParameterImpl.getCached((it as KSValueParameterImpl).ktParameter) } ?: emptyList()
        val result = ktClassOrObject.declarations.getKSDeclarations().toMutableList<KSDeclaration>()
        result.addAll(propertiesFromConstructor)
        primaryConstructor?.let { primaryConstructor: KSFunctionDeclaration ->
            // if primary constructor is from source, it won't show up in declarations
            // hence add it as well.
            if (primaryConstructor.origin == Origin.KOTLIN) {
                result.add(primaryConstructor)
            }
        }
        if (classKind != ClassKind.INTERFACE) {
            // check if we need to add a synthetic constructor
            val hasConstructor = result.any {
                it is KSFunctionDeclaration && it.isConstructor()
            }
            if (hasConstructor) {
                result
            } else {
                result + KSConstructorSyntheticImpl(this)
            }
        } else {
            result
        }
    }

    override val primaryConstructor: KSFunctionDeclaration? by lazy {
        ktClassOrObject.primaryConstructor?.let { KSFunctionDeclarationImpl.getCached(it) }
            ?: if ((classKind == ClassKind.CLASS || classKind == ClassKind.ENUM_CLASS)
                    && ktClassOrObject.declarations.none { it is KtSecondaryConstructor })
                KSConstructorSyntheticImpl.getCached(this) else null
    }

    override val superTypes: List<KSTypeReference> by lazy {
        ktClassOrObject.superTypeListEntries.map { KSTypeReferenceImpl.getCached(it.typeReference!!) }
    }

    private val descriptor: ClassDescriptor by lazy {
        (ResolverImpl.instance.resolveDeclaration(ktClassOrObject) as ClassDescriptor)
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        return getKSTypeCached(descriptor.defaultType.replaceTypeArguments(typeArguments), typeArguments)
    }

    override fun asStarProjectedType(): KSType {
        return getKSTypeCached(descriptor.defaultType.replaceArgumentsWithStarProjections())
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }
}
