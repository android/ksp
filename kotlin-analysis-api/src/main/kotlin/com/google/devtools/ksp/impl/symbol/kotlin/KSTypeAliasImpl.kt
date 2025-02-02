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
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSTypeReferenceResolvedImpl
import com.google.devtools.ksp.symbol.*
import org.jetbrains.kotlin.analysis.api.symbols.*

class KSTypeAliasImpl private constructor(private val ktTypeAliasSymbol: KaTypeAliasSymbol) :
    KSTypeAlias,
    AbstractKSDeclarationImpl(ktTypeAliasSymbol),
    KSExpectActual by KSExpectActualImpl(ktTypeAliasSymbol) {
    companion object : KSObjectCache<KaTypeAliasSymbol, KSTypeAliasImpl>() {
        fun getCached(ktTypeAliasSymbol: KaTypeAliasSymbol) =
            cache.getOrPut(ktTypeAliasSymbol) { KSTypeAliasImpl(ktTypeAliasSymbol) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val name: KSName by lazy {
        KSNameImpl.getCached(ktTypeAliasSymbol.nameOrAnonymous.asString())
    }

    override val type: KSTypeReference by lazy {
        KSTypeReferenceResolvedImpl.getCached(
            ktTypeAliasSymbol.expandedType.let { it.abbreviation ?: it },
            this
        )
    }

    override val simpleName: KSName
        get() = name

    override val qualifiedName: KSName? by lazy {
        ktTypeAliasSymbol.classId?.asFqNameString()?.let { KSNameImpl.getCached(it) }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeAlias(this, data)
    }

    override fun defer(): Restorable? {
        return ktTypeAliasSymbol.defer(::getCached)
    }
}

internal fun KaTypeAliasSymbol.toModifiers(): Set<Modifier> {
    val result = mutableSetOf<Modifier>()
    result.add(modality.toModifier())
    if (visibility != KaSymbolVisibility.PACKAGE_PRIVATE) {
        result.add(visibility.toModifier())
    }
    return result
}
