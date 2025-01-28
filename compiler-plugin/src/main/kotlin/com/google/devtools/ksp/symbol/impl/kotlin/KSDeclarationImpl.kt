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

import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.common.memoized
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import org.jetbrains.kotlin.psi.*

abstract class KSDeclarationImpl(val ktDeclaration: KtDeclaration) /*: KSDeclaration*/ {
    /*override*/ val origin: Origin = Origin.KOTLIN

    /*override*/ val location: Location by lazy {
        ktDeclaration.toLocation()
    }

    open /*override*/ val simpleName: KSName by lazy {
        KSNameImpl.getCached(ktDeclaration.name!!)
    }

    open /*override*/ val qualifiedName: KSName? by lazy {
        (ktDeclaration as? KtNamedDeclaration)?.fqName?.let { KSNameImpl.getCached(it.asString()) }
    }

    open /*override*/ val annotations: Sequence<KSAnnotation> by lazy {
        ktDeclaration.annotationEntries.asSequence().map { KSAnnotationImpl.getCached(it) }.memoized()
    }

    /*override*/ val modifiers: Set<Modifier> by lazy {
        // we do not check for JVM_STATIC here intentionally as it actually means static in parent class,
        // not in this class.
        // see: https://github.com/google/ksp/issues/378
        if (this is KSFunctionDeclaration && this.isConstructor() &&
            (this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
        ) {
            setOf(Modifier.FINAL, Modifier.PRIVATE)
        } else {
            ktDeclaration.toKSModifiers()
        }
    }

    /*override*/ val containingFile: KSFile? by lazy {
        KSFileImpl.getCached(ktDeclaration.containingKtFile)
    }

    /*override*/ val packageName: KSName by lazy {
        this.containingFile!!.packageName
    }

    open /*override*/ val typeParameters: List<KSTypeParameter> by lazy {
        (ktDeclaration as? KtTypeParameterListOwner)?.let {
            it.typeParameters.map { KSTypeParameterImpl.getCached(it) }
        } ?: emptyList()
    }

    open /*override*/ val parentDeclaration: KSDeclaration? by lazy {
        ktDeclaration.findParentDeclaration()
    }

    /*override*/ val parent: KSNode? by lazy {
        ktDeclaration.findParentAnnotated()
    }

    override fun toString(): String {
        return this.simpleName.asString()
    }

    internal val originalAnnotations: List<KSAnnotation> by lazy {
        ktDeclaration.annotationEntries.map { KSAnnotationImpl.getCached(it) }
    }

    /*override*/ val docString by lazy {
        ktDeclaration.getDocString()
    }
}
