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

import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.synthetic.KSValueParameterSyntheticImpl
import org.jetbrains.kotlin.psi.KtPropertyAccessor

class KSPropertySetterImpl private constructor(ktPropertySetter: KtPropertyAccessor) :
    KSPropertyAccessorImpl(ktPropertySetter),
    KSPropertySetter {
    companion object : KSObjectCache<KtPropertyAccessor, KSPropertySetterImpl>() {
        fun getCached(ktPropertySetter: KtPropertyAccessor) = cache.getOrPut(ktPropertySetter) {
            KSPropertySetterImpl(ktPropertySetter)
        }
    }

    override fun asKSPropertyAccessor(): KSPropertyAccessor = this

    override val parameter: KSValueParameter by lazy {
        ktPropertySetter.parameterList?.parameters?.singleOrNull()?.let { KSValueParameterImpl.getCached(it) }
            ?: KSValueParameterSyntheticImpl.getCached(this) {
                ResolverImpl.instance!!.resolvePropertyAccessorDeclaration(this)
                    ?.valueParameters?.singleOrNull()
            }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertySetter(this, data)
    }

    override fun toString(): String {
        return "$receiver.setter()"
    }
}
