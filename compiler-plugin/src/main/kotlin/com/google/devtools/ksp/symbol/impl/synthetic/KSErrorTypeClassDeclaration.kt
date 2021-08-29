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

import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.kotlin.KSNameImpl

object KSErrorTypeClassDeclaration : KSClassDeclaration {
    override val annotations: Sequence<KSAnnotation> = emptySequence()

    override val classKind: ClassKind = ClassKind.CLASS

    override val declarations: Sequence<KSDeclaration> = emptySequence()

    override val isActual: Boolean = false

    override val isExpect: Boolean = false

    override val isCompanionObject: Boolean = false

    override val location: Location = NonExistLocation

    override val parent: KSNode? = null

    override val modifiers: Set<Modifier> = emptySet()

    override val origin: Origin = Origin.SYNTHETIC

    override val packageName: KSName = KSNameImpl.getCached("")

    override val parentDeclaration: KSDeclaration? = null

    override val primaryConstructor: KSFunctionDeclaration? = null

    override val qualifiedName: KSName? = null

    override val simpleName: KSName = KSNameImpl.getCached("<Error>")

    override val superTypes: Sequence<KSTypeReference> = emptySequence()

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override fun getSealedSubclasses(): Sequence<KSClassDeclaration> = emptySequence()

    override fun asStarProjectedType(): KSType {
        return ResolverImpl.instance.builtIns.nothingType
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        return ResolverImpl.instance.builtIns.nothingType
    }

    override fun findActuals(): Sequence<KSDeclaration> {
        return emptySequence()
    }

    override fun findExpects(): Sequence<KSDeclaration> {
        return emptySequence()
    }

    override fun getAllFunctions(): Sequence<KSFunctionDeclaration> {
        return emptySequence()
    }

    override fun getAllProperties(): Sequence<KSPropertyDeclaration> {
        return emptySequence()
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }

    override fun toString(): String {
        return "Error type synthetic declaration"
    }

    override val docString = null
}
