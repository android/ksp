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

import com.google.devtools.ksp.common.memoized
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.toFunctionKSModifiers
import com.google.devtools.ksp.symbol.impl.toKSModifiers
import com.google.devtools.ksp.symbol.impl.toKSPropertyDeclaration
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor

abstract class KSPropertyAccessorDescriptorImpl(val descriptor: PropertyAccessorDescriptor) /*: KSPropertyAccessor*/ {
    protected abstract fun asKSPropertyAccessor(): KSPropertyAccessor

    /*override*/ val origin: Origin by lazy {
        when (receiver.origin) {
            // if receiver is kotlin source, that means we are a synthetic where developer
            // didn't declare an explicit accessor so we used the descriptor instead
            Origin.KOTLIN -> Origin.SYNTHETIC
            else -> descriptor.origin
        }
    }

    /*override*/ val receiver: KSPropertyDeclaration by lazy {
        descriptor.correspondingProperty.toKSPropertyDeclaration()
    }

    /*override*/ val parent: KSNode? by lazy {
        receiver
    }

    /*override*/ val location: Location
        get() {
            // if receiver is kotlin source, that means `this` is synthetic hence we want the property's location
            // Otherwise, receiver is also from a .class file where the location will be NoLocation
            return receiver.location
        }

    /*override*/ val annotations: Sequence<KSAnnotation> by lazy {
        descriptor.annotations.asSequence()
            .map { KSAnnotationDescriptorImpl.getCached(it, this.asKSPropertyAccessor()) }
            .memoized()
    }

    /*override*/ val modifiers: Set<Modifier> by lazy {
        val modifiers = mutableSetOf<Modifier>()
        modifiers.addAll(descriptor.toKSModifiers())
        modifiers.addAll(descriptor.toFunctionKSModifiers())
        modifiers
    }
}
