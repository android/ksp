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
import com.google.devtools.ksp.processing.impl.KSObjectCache
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.org.objectweb.asm.Opcodes

class KSPropertyDeclarationDescriptorImpl private constructor(val descriptor: PropertyDescriptor) :
    KSPropertyDeclaration,
    KSDeclarationDescriptorImpl(descriptor),
    KSExpectActual by KSExpectActualDescriptorImpl(descriptor) {
    companion object : KSObjectCache<PropertyDescriptor, KSPropertyDeclarationDescriptorImpl>() {
        fun getCached(descriptor: PropertyDescriptor) = cache.getOrPut(descriptor) {
            KSPropertyDeclarationDescriptorImpl(descriptor)
        }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val extensionReceiver: KSTypeReference? by lazy {
        if (descriptor.extensionReceiverParameter != null) {
            KSTypeReferenceDescriptorImpl.getCached(descriptor.extensionReceiverParameter!!.type, origin, this)
        } else {
            null
        }
    }

    override val annotations: Sequence<KSAnnotation> by lazy {
        // annotations on backing field will not visible in the property declaration so we query it directly to load
        // its annotations as well.
        val backingFieldAnnotations = descriptor.backingField?.annotations?.map {
            KSAnnotationDescriptorImpl.getCached(it, this)
        }.orEmpty()
        (super.annotations + backingFieldAnnotations).memoized()
    }

    override val isMutable: Boolean by lazy {
        descriptor.isVar
    }

    override val modifiers: Set<Modifier> by lazy {
        val modifiers = mutableSetOf<Modifier>()
        modifiers.addAll(descriptor.toKSModifiers())
        if (descriptor.isConst) {
            modifiers.add(Modifier.CONST)
        }
        if (descriptor.isLateInit) {
            modifiers.add(Modifier.LATEINIT)
        }

        if (this.origin == Origin.JAVA_LIB) {
            if (this.jvmAccessFlag and Opcodes.ACC_TRANSIENT != 0)
                modifiers.add(Modifier.JAVA_TRANSIENT)
            if (this.jvmAccessFlag and Opcodes.ACC_VOLATILE != 0)
                modifiers.add(Modifier.JAVA_VOLATILE)
        }

        modifiers
    }

    override val setter: KSPropertySetter? by lazy {
        if (descriptor.setter != null) {
            KSPropertySetterDescriptorImpl.getCached(descriptor.setter as PropertySetterDescriptor)
        } else {
            null
        }
    }

    override val getter: KSPropertyGetter? by lazy {
        if (descriptor.getter != null) {
            KSPropertyGetterDescriptorImpl.getCached(descriptor.getter as PropertyGetterDescriptor)
        } else {
            null
        }
    }

    override val typeParameters: List<KSTypeParameter> by lazy {
        descriptor.typeParameters.map { KSTypeParameterDescriptorImpl.getCached(it) }
    }

    override val type: KSTypeReference by lazy {
        KSTypeReferenceDescriptorImpl.getCached(descriptor.type, origin, this)
    }

    override val hasBackingField: Boolean by lazy {
        descriptor.hasBackingFieldWithBinaryClassSupport()
    }

    override fun findOverridee(): KSPropertyDeclaration? {
        val propertyDescriptor = ResolverImpl.instance!!.resolvePropertyDeclaration(this)
        return propertyDescriptor?.findClosestOverridee()?.toKSPropertyDeclaration()
    }

    override fun isDelegated(): Boolean {
        return (descriptor as? PropertyDescriptor)?.delegateField != null
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyDeclaration(this, data)
    }

    override fun asMemberOf(containing: KSType): KSType =
        ResolverImpl.instance!!.asMemberOf(this, containing)
}
