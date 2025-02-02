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
import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import com.google.devtools.ksp.symbol.impl.kotlin.KSExpectActualNoImpl
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile

class KSPropertyDeclarationJavaImpl private constructor(val psi: PsiField) :
    KSPropertyDeclaration,
    KSDeclarationJavaImpl(psi),
    KSExpectActual by KSExpectActualNoImpl() {
    companion object : KSObjectCache<PsiField, KSPropertyDeclarationJavaImpl>() {
        fun getCached(psi: PsiField) = cache.getOrPut(psi) { KSPropertyDeclarationJavaImpl(psi) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val origin = Origin.JAVA

    override val location: Location by lazy {
        psi.toLocation()
    }

    override val isMutable: Boolean
        get() = !modifiers.contains(Modifier.FINAL)

    override val hasBackingField: Boolean
        get() = true

    override val annotations: Sequence<KSAnnotation> by lazy {
        psi.annotations.asSequence().map { KSAnnotationJavaImpl.getCached(it) }.memoized()
    }

    override val containingFile: KSFile? by lazy {
        KSFileJavaImpl.getCached(psi.containingFile as PsiJavaFile)
    }

    override val extensionReceiver: KSTypeReference? = null

    override val getter: KSPropertyGetter? = null

    override val setter: KSPropertySetter? = null

    override val modifiers: Set<Modifier> by lazy {
        psi.toKSModifiers()
    }

    override val parentDeclaration: KSDeclaration? by lazy {
        psi.findParentDeclaration()
    }

    override val qualifiedName: KSName by lazy {
        KSNameImpl.getCached("${parentDeclaration?.qualifiedName?.asString()}.${this.simpleName.asString()}")
    }

    override val simpleName: KSName by lazy {
        KSNameImpl.getCached(psi.name)
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val type: KSTypeReference by lazy {
        KSTypeReferenceJavaImpl.getCached(psi.type, this)
    }

    override fun findOverridee(): KSPropertyDeclaration? {
        return null
    }

    override fun isDelegated(): Boolean {
        return false
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyDeclaration(this, data)
    }

    override fun asMemberOf(containing: KSType): KSType =
        ResolverImpl.instance!!.asMemberOf(this, containing)
}
