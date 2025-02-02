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

import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.binary.KSValueParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor

class KSPropertySetterSyntheticImpl(val ksPropertyDeclaration: KSPropertyDeclaration) :
    KSPropertyAccessorSyntheticImpl(ksPropertyDeclaration), KSPropertySetter {
    companion object : KSObjectCache<KSPropertyDeclaration, KSPropertySetterSyntheticImpl>() {
        fun getCached(ksPropertyDeclaration: KSPropertyDeclaration) =
            KSPropertySetterSyntheticImpl.cache.getOrPut(ksPropertyDeclaration) {
                KSPropertySetterSyntheticImpl(ksPropertyDeclaration)
            }
    }

    override fun asKSPropertyAccessor(): KSPropertyAccessor = this

    private val descriptor: PropertyAccessorDescriptor by lazy {
        ResolverImpl.instance!!.resolvePropertyDeclaration(ksPropertyDeclaration)!!.setter!!
    }

    override val parameter: KSValueParameter by lazy {
        descriptor.valueParameters.singleOrNull()?.let { KSValueParameterDescriptorImpl.getCached(it, this) }
            ?: throw IllegalStateException("Failed to resolve property type")
    }

    override val declarations: Sequence<KSDeclaration>
        get() = emptySequence()

    override val parent: KSNode? = ksPropertyDeclaration

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertySetter(this, data)
    }

    override fun toString(): String {
        return "$receiver.getter()"
    }
}
