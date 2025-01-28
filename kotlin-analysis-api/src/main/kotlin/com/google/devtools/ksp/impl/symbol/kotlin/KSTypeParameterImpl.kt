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

package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.common.impl.KSTypeReferenceSyntheticImpl
import com.google.devtools.ksp.impl.ResolverAAImpl
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSTypeReferenceResolvedImpl
import com.google.devtools.ksp.symbol.*
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType

class KSTypeParameterImpl private constructor(
    internal val ktTypeParameterSymbol: KaTypeParameterSymbol,
    private val boundsSubstitued: List<KaType>?
) :
    KSTypeParameter,
    AbstractKSDeclarationImpl(ktTypeParameterSymbol),
    KSExpectActual by KSExpectActualImpl(ktTypeParameterSymbol) {
    companion object : KSObjectCache<Pair<KaTypeParameterSymbol, List<KaType>?>, KSTypeParameterImpl>() {
        fun getCached(ktTypeParameterSymbol: KaTypeParameterSymbol, bounds: List<KaType>? = null) =
            cache.getOrPut(Pair(ktTypeParameterSymbol, bounds)) {
                KSTypeParameterImpl(ktTypeParameterSymbol, bounds)
            }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val name: KSName by lazy {
        KSNameImpl.getCached(ktTypeParameterSymbol.name.asString())
    }

    override val variance: Variance by lazy {
        when (ktTypeParameterSymbol.variance) {
            org.jetbrains.kotlin.types.Variance.IN_VARIANCE -> Variance.CONTRAVARIANT
            org.jetbrains.kotlin.types.Variance.OUT_VARIANCE -> Variance.COVARIANT
            org.jetbrains.kotlin.types.Variance.INVARIANT -> Variance.INVARIANT
        }
    }

    override val isReified: Boolean = ktTypeParameterSymbol.isReified

    override val bounds: Sequence<KSTypeReference> by lazy {
        boundsSubstitued?.mapIndexed { index, type ->
            KSTypeReferenceResolvedImpl.getCached(type, this@KSTypeParameterImpl, index)
        }?.asSequence() ?: ktTypeParameterSymbol.upperBounds.asSequence().mapIndexed { index, type ->
            KSTypeReferenceResolvedImpl.getCached(type, this@KSTypeParameterImpl, index)
        }.ifEmpty {
            sequenceOf(
                KSTypeReferenceSyntheticImpl.getCached(ResolverAAImpl.instance.builtIns.anyType.makeNullable(), this)
            )
        }
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val qualifiedName: KSName? by lazy {
        this.parentDeclaration?.qualifiedName?.let {
            KSNameImpl.getCached("${it.asString()}.${simpleName.asString()}")
        }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeParameter(this, data)
    }

    override fun defer(): Restorable? {
        return ktTypeParameterSymbol.defer(::getCached)
    }
}
