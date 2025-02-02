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
import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.findParentDeclaration
import com.google.devtools.ksp.symbol.impl.kotlin.KSExpectActualNoImpl
import com.google.devtools.ksp.symbol.impl.toLocation
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiTypeParameter

class KSTypeParameterJavaImpl private constructor(val psi: PsiTypeParameter) :
    KSTypeParameter,
    KSDeclarationJavaImpl(psi),
    KSExpectActual by KSExpectActualNoImpl() {
    companion object : KSObjectCache<PsiTypeParameter, KSTypeParameterJavaImpl>() {
        fun getCached(psi: PsiTypeParameter) = cache.getOrPut(psi) { KSTypeParameterJavaImpl(psi) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val origin = Origin.JAVA

    override val location: Location by lazy {
        psi.toLocation()
    }

    override val annotations: Sequence<KSAnnotation> by lazy {
        psi.annotations.asSequence().map { KSAnnotationJavaImpl.getCached(it) }.memoized()
    }

    override val bounds: Sequence<KSTypeReference> by lazy {
        psi.extendsListTypes.asSequence().map { KSTypeReferenceJavaImpl.getCached(it, this) }.memoized()
    }
    override val simpleName: KSName by lazy {
        KSNameImpl.getCached(psi.name ?: "_")
    }

    override val qualifiedName: KSName? by lazy {
        KSNameImpl.getCached(parentDeclaration?.qualifiedName?.asString() ?: "" + "." + simpleName.asString())
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val parentDeclaration: KSDeclaration? by lazy {
        psi.findParentDeclaration()
    }

    override val containingFile: KSFile? by lazy {
        KSFileJavaImpl.getCached(psi.containingFile as PsiJavaFile)
    }

    override val modifiers: Set<Modifier> = emptySet()

    override val isReified: Boolean = false

    override val name: KSName by lazy {
        KSNameImpl.getCached(psi.name!!)
    }

    override val variance: Variance = Variance.INVARIANT

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeParameter(this, data)
    }
}
