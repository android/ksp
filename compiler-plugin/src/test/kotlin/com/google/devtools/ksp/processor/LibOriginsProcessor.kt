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

package com.google.devtools.ksp.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSTopDownVisitor

class LibOriginsProcessor : AbstractTestProcessor() {
    private val result = mutableListOf<String>()

    override fun toResult(): List<String> {
        return result
    }

    inner class MyCollector : KSTopDownVisitor<Unit, Unit>() {
        override fun defaultHandler(node: KSNode, data: Unit) = Unit

        override fun visitDeclaration(declaration: KSDeclaration, data: Unit) {
            result.add(
                "declaration: ${
                declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
                }: ${declaration.origin.name}"
            )
            super.visitDeclaration(declaration, data)
        }

        override fun visitAnnotation(annotation: KSAnnotation, data: Unit) {
            result.add("annotation: ${annotation.shortName.asString()}: ${annotation.origin.name}")
            super.visitAnnotation(annotation, data)
        }

        override fun visitTypeReference(typeReference: KSTypeReference, data: Unit) {
            result.add("reference: $typeReference: ${typeReference.origin.name}")
            super.visitTypeReference(typeReference, data)
        }

        override fun visitClassifierReference(reference: KSClassifierReference, data: Unit) {
            result.add("classifier ref: $reference: ${reference.origin.name}")
            super.visitClassifierReference(reference, data)
        }

        override fun visitValueParameter(valueParameter: KSValueParameter, data: Unit) {
            result.add("value param: $valueParameter: ${valueParameter.origin.name}")
            super.visitValueParameter(valueParameter, data)
        }

        override fun visitTypeArgument(typeArgument: KSTypeArgument, data: Unit) {
            result.add("type arg: $typeArgument: ${typeArgument.origin.name}")
            super.visitTypeArgument(typeArgument, data)
        }

        override fun visitPropertyAccessor(accessor: KSPropertyAccessor, data: Unit) {
            result.add("property accessor: $accessor: ${accessor.origin.name}")
            super.visitPropertyAccessor(accessor, data)
        }
    }

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val visitor = MyCollector()

        // FIXME: workaround for https://github.com/google/ksp/issues/418
        resolver.getDeclarationsFromPackage("foo.bar").forEach {
            if (it.containingFile == null) {
                it.accept(visitor, Unit)
            }
        }

        resolver.getNewFiles().forEach {
            it.accept(visitor, Unit)
        }

        result.sort()
        return emptyList()
    }
}
