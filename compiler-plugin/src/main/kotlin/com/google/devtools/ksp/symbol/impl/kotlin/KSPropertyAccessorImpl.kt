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

import com.google.devtools.ksp.common.memoized
import com.google.devtools.ksp.processing.impl.findAnnotationFromUseSiteTarget
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.getKSDeclarations
import com.google.devtools.ksp.symbol.impl.toKSModifiers
import com.google.devtools.ksp.symbol.impl.toLocation
import org.jetbrains.kotlin.psi.KtPropertyAccessor

abstract class KSPropertyAccessorImpl(val ktPropertyAccessor: KtPropertyAccessor) /*: KSPropertyAccessor*/ {
    companion object {
        fun getCached(ktPropertyAccessor: KtPropertyAccessor): KSPropertyAccessor {
            return if (ktPropertyAccessor.isGetter) {
                KSPropertyGetterImpl.getCached(ktPropertyAccessor)
            } else {
                KSPropertySetterImpl.getCached(ktPropertyAccessor)
            }
        }
    }

    protected abstract fun asKSPropertyAccessor(): KSPropertyAccessor

    /*override*/ val receiver: KSPropertyDeclaration by lazy {
        KSPropertyDeclarationImpl.getCached(ktPropertyAccessor.property)
    }
    /*override*/ val annotations: Sequence<KSAnnotation> by lazy {
        ktPropertyAccessor.filterUseSiteTargetAnnotations().map { KSAnnotationImpl.getCached(it) }
            .plus(this.asKSPropertyAccessor().findAnnotationFromUseSiteTarget())
    }

    /*override*/ val parent: KSNode? by lazy {
        receiver
    }

    /*override*/ val location: Location by lazy {
        ktPropertyAccessor.toLocation()
    }

    /*override*/ val modifiers: Set<Modifier> by lazy {
        ktPropertyAccessor.toKSModifiers()
    }

    /*override*/ val declarations: Sequence<KSDeclaration> by lazy {
        if (!ktPropertyAccessor.hasBlockBody()) {
            emptySequence()
        } else {
            ktPropertyAccessor.bodyBlockExpression?.statements?.asSequence()?.getKSDeclarations()?.memoized()
                ?: emptySequence()
        }
    }

    /*override*/ val origin: Origin = Origin.KOTLIN

    internal val originalAnnotations: List<KSAnnotation> by lazy {
        ktPropertyAccessor.annotationEntries.map { KSAnnotationImpl.getCached(it) }
    }
}
