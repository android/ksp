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

import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.common.memoized
import com.google.devtools.ksp.common.toKSModifiers
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import com.google.devtools.ksp.symbol.impl.binary.getAllFunctions
import com.google.devtools.ksp.symbol.impl.binary.getAllProperties
import com.google.devtools.ksp.symbol.impl.kotlin.KSErrorType
import com.google.devtools.ksp.symbol.impl.kotlin.KSExpectActualNoImpl
import com.google.devtools.ksp.symbol.impl.kotlin.getKSTypeCached
import com.google.devtools.ksp.symbol.impl.replaceTypeArguments
import com.google.devtools.ksp.symbol.impl.synthetic.KSConstructorSyntheticImpl
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections

class KSClassDeclarationJavaImpl private constructor(val psi: PsiClass) :
    KSClassDeclaration,
    KSDeclarationJavaImpl(psi),
    KSExpectActual by KSExpectActualNoImpl() {
    companion object : KSObjectCache<PsiClass, KSClassDeclarationJavaImpl>() {
        fun getCached(psi: PsiClass) = cache.getOrPut(psi) { KSClassDeclarationJavaImpl(psi) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val origin = Origin.JAVA

    override val location: Location by lazy {
        psi.toLocation()
    }

    override val annotations: Sequence<KSAnnotation> by lazy {
        psi.annotations.asSequence().map { KSAnnotationJavaImpl.getCached(it) }.memoized()
    }

    override val classKind: ClassKind by lazy {
        when {
            psi.isAnnotationType -> ClassKind.ANNOTATION_CLASS
            psi.isInterface -> ClassKind.INTERFACE
            psi.isEnum -> ClassKind.ENUM_CLASS
            else -> ClassKind.CLASS
        }
    }

    override val containingFile: KSFile? by lazy {
        KSFileJavaImpl.getCached(psi.containingFile as PsiJavaFile)
    }

    override val isCompanionObject = false

    // Could the resolution ever fail?
    private val descriptor: ClassDescriptor? by lazy {
        ResolverImpl.instance!!.moduleClassResolver.resolveClass(JavaClassImpl(psi))
    }

    // TODO in 1.5 + jvmTarget 15, will we return Java permitted types?
    override fun getSealedSubclasses(): Sequence<KSClassDeclaration> = emptySequence()

    override fun getAllFunctions(): Sequence<KSFunctionDeclaration> =
        descriptor?.getAllFunctions() ?: emptySequence()

    override fun getAllProperties(): Sequence<KSPropertyDeclaration> =
        descriptor?.getAllProperties() ?: emptySequence()

    override val declarations: Sequence<KSDeclaration> by lazy {
        val allDeclarations = (
            psi.fields.asSequence().map {
                when (it) {
                    is PsiEnumConstant -> KSClassDeclarationJavaEnumEntryImpl.getCached(it)
                    else -> KSPropertyDeclarationJavaImpl.getCached(it)
                }
            } +
                psi.innerClasses.map { KSClassDeclarationJavaImpl.getCached(it) } +
                psi.constructors.map { KSFunctionDeclarationJavaImpl.getCached(it) } +
                psi.methods.map { KSFunctionDeclarationJavaImpl.getCached(it) }
            )
            .distinct()
        // java annotation classes are interface. they get a constructor in .class
        // hence they should get one here.
        if (classKind == ClassKind.ANNOTATION_CLASS || !psi.isInterface) {
            val hasConstructor = allDeclarations.any {
                it is KSFunctionDeclaration && it.isConstructor()
            }
            if (hasConstructor) {
                allDeclarations.memoized()
            } else {
                (allDeclarations + KSConstructorSyntheticImpl.getCached(this)).memoized()
            }
        } else {
            allDeclarations.memoized()
        }
    }

    override val modifiers: Set<Modifier> by lazy {
        val modifiers = mutableSetOf<Modifier>()
        modifiers.addAll(psi.toKSModifiers())
        if (psi.isAnnotationType) {
            modifiers.add(Modifier.ANNOTATION)
        }
        if (psi.isEnum) {
            modifiers.add(Modifier.ENUM)
        }
        modifiers
    }

    override val primaryConstructor: KSFunctionDeclaration? = null

    override val qualifiedName: KSName by lazy {
        KSNameImpl.getCached(psi.qualifiedName!!)
    }

    override val simpleName: KSName by lazy {
        KSNameImpl.getCached(psi.name!!)
    }

    override val superTypes: Sequence<KSTypeReference> by lazy {
        val adjusted = if (!psi.isInterface && psi.superTypes.size > 1) {
            psi.superTypes.filterNot {
                it.className == "Object" && it.canonicalText == "java.lang.Object"
            }
        } else {
            psi.superTypes.toList()
        }
        adjusted.asSequence().map { KSTypeReferenceJavaImpl.getCached(it, this) }.memoized()
    }

    override val typeParameters: List<KSTypeParameter> by lazy {
        psi.typeParameters.map { KSTypeParameterJavaImpl.getCached(it) }
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        return descriptor?.defaultType?.replaceTypeArguments(typeArguments) ?: KSErrorType(psi.qualifiedName)
    }

    override fun asStarProjectedType(): KSType {
        return descriptor?.let {
            getKSTypeCached(it.defaultType.replaceArgumentsWithStarProjections())
        } ?: KSErrorType(psi.qualifiedName)
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }
}
