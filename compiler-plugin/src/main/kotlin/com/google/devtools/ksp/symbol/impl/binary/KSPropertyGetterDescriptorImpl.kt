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

package com.google.devtools.ksp.symbol.impl.binary

import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.symbol.*
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor

class KSPropertyGetterDescriptorImpl private constructor(descriptor: PropertyGetterDescriptor) :
    KSPropertyAccessorDescriptorImpl(descriptor), KSPropertyGetter {
    companion object : KSObjectCache<PropertyGetterDescriptor, KSPropertyGetterDescriptorImpl>() {
        fun getCached(descriptor: PropertyGetterDescriptor) = cache.getOrPut(descriptor) {
            KSPropertyGetterDescriptorImpl(descriptor)
        }
    }

    override fun asKSPropertyAccessor(): KSPropertyAccessor = this

    override val returnType: KSTypeReference? by lazy {
        if (descriptor.returnType != null) {
            KSTypeReferenceDescriptorImpl.getCached(descriptor.returnType!!, origin, this)
        } else {
            null
        }
    }

    override val declarations: Sequence<KSDeclaration>
        get() = emptySequence()

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyGetter(this, data)
    }

    override fun toString(): String {
        return "$receiver.getter()"
    }
}
