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

package com.google.devtools.ksp.symbol.impl.synthetic

import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.common.impl.KSTypeReferenceSyntheticImpl
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*

class KSConstructorSyntheticImpl private constructor(val ksClassDeclaration: KSClassDeclaration) :
    KSFunctionDeclaration {
    companion object : KSObjectCache<KSClassDeclaration, KSConstructorSyntheticImpl>() {
        fun getCached(ksClassDeclaration: KSClassDeclaration) =
            KSConstructorSyntheticImpl.cache.getOrPut(ksClassDeclaration) {
                KSConstructorSyntheticImpl(ksClassDeclaration)
            }
    }

    override val isAbstract: Boolean = false

    override val extensionReceiver: KSTypeReference? = null

    override val parameters: List<KSValueParameter> = emptyList()

    override val functionKind: FunctionKind = FunctionKind.MEMBER

    override val docString: String? get() = ksClassDeclaration.docString

    override val packageName: KSName get() = ksClassDeclaration.packageName

    override val qualifiedName: KSName? by lazy {
        KSNameImpl.getCached(ksClassDeclaration.qualifiedName?.asString()?.plus(".<init>") ?: "")
    }

    override val simpleName: KSName by lazy {
        KSNameImpl.getCached("<init>")
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val containingFile: KSFile? by lazy {
        ksClassDeclaration.containingFile
    }

    override val parentDeclaration: KSDeclaration? by lazy {
        ksClassDeclaration
    }

    override val parent: KSNode? by lazy {
        parentDeclaration
    }

    override val returnType: KSTypeReference by lazy {
        KSTypeReferenceSyntheticImpl(
            ksClassDeclaration.asStarProjectedType(), this
        )
    }

    override val annotations: Sequence<KSAnnotation> = emptySequence()

    override val isActual: Boolean = false

    override val isExpect: Boolean = false

    override val declarations: Sequence<KSDeclaration> = emptySequence()

    override val location: Location by lazy {
        ksClassDeclaration.location
    }

    override val modifiers: Set<Modifier> by lazy {
        if (ksClassDeclaration.classKind == ClassKind.ENUM_CLASS) {
            return@lazy setOf(Modifier.FINAL, Modifier.PRIVATE)
        }
        // add public if parent class is public
        if (ksClassDeclaration.isPublic()) {
            setOf(Modifier.FINAL, Modifier.PUBLIC)
        } else {
            setOf(Modifier.FINAL)
        }
    }

    override val origin: Origin = Origin.SYNTHETIC

    override fun findOverridee(): KSFunctionDeclaration? = null

    override fun findActuals(): Sequence<KSDeclaration> {
        return emptySequence()
    }

    override fun findExpects(): Sequence<KSDeclaration> {
        return emptySequence()
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitFunctionDeclaration(this, data)
    }

    override fun toString(): String {
        return "synthetic constructor for ${this.parentDeclaration}"
    }

    override fun asMemberOf(containing: KSType): KSFunction =
        ResolverImpl.instance!!.asMemberOf(this, containing)
}
