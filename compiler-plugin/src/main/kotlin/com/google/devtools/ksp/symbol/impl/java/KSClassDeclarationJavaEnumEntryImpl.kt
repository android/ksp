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


package com.google.devtools.ksp.symbol.impl.java

import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import com.google.devtools.ksp.symbol.impl.binary.getAllFunctions
import com.google.devtools.ksp.symbol.impl.binary.getAllProperties
import com.google.devtools.ksp.symbol.impl.kotlin.KSExpectActualNoImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSNameImpl
import com.google.devtools.ksp.symbol.impl.kotlin.getKSTypeCached
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections

class KSClassDeclarationJavaEnumEntryImpl private constructor(val psi: PsiEnumConstant) : KSClassDeclaration, KSDeclarationJavaImpl(),
    KSExpectActual by KSExpectActualNoImpl() {
    companion object : KSObjectCache<PsiEnumConstant, KSClassDeclarationJavaEnumEntryImpl>() {
        fun getCached(psi: PsiEnumConstant) = cache.getOrPut(psi) { KSClassDeclarationJavaEnumEntryImpl(psi) }
    }

    override val origin = Origin.JAVA

    override val location: Location by lazy {
        psi.toLocation()
    }

    override val annotations: List<KSAnnotation> by lazy {
        psi.annotations.map { KSAnnotationJavaImpl.getCached(it) }
    }

    override val classKind: ClassKind = ClassKind.ENUM_ENTRY

    override val containingFile: KSFile? by lazy {
        KSFileJavaImpl.getCached(psi.containingFile as PsiJavaFile)
    }

    override val isCompanionObject = false

    private val descriptor: ClassDescriptor? by lazy {
        ResolverImpl.instance.resolveJavaDeclaration(psi) as ClassDescriptor
    }

    override fun getAllFunctions(): List<KSFunctionDeclaration> =
            descriptor?.getAllFunctions() ?: emptyList()

    override fun getAllProperties(): List<KSPropertyDeclaration> =
            descriptor?.getAllProperties() ?: emptyList()

    override val declarations: List<KSDeclaration> = emptyList()

    override val modifiers: Set<Modifier> by lazy {
        psi.toKSModifiers()
    }

    override val parentDeclaration: KSDeclaration? by lazy {
        psi.findParentDeclaration()
    }

    override val primaryConstructor: KSFunctionDeclaration? = null

    override val qualifiedName: KSName by lazy {
        KSNameImpl.getCached("${parentDeclaration!!.qualifiedName!!.asString()}.${psi.name}")
    }

    override val simpleName: KSName by lazy {
        KSNameImpl.getCached(psi.name)
    }

    override val superTypes: List<KSTypeReference> = emptyList()

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        return getKSTypeCached(descriptor!!.defaultType.replaceTypeArguments(typeArguments), typeArguments)
    }

    override fun asStarProjectedType(): KSType {
        return getKSTypeCached(descriptor!!.defaultType.replaceArgumentsWithStarProjections())
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }
}