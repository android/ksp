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

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSTopDownVisitor

open class TypeComparisonProcessor : AbstractTestProcessor() {
    val results = mutableListOf<String>()
    val typeCollector = TypeCollector()
    val types = mutableSetOf<KSType>()

    override fun process(resolver: Resolver) {
        val files = resolver.getAllFiles()
        val ignoredNames = mutableSetOf<String>()

        files.forEach {
            it.accept(typeCollector, types)
            it.annotations.forEach {
                if (it.shortName.asString() == "Suppress") {
                    it.arguments.forEach {
                        (it.value as List<String>).forEach {
                            ignoredNames.add(it)
                        }
                    }
                }
            }
        }

        val sortedTypes = types.filterNot { it.declaration.simpleName.asString() in ignoredNames }.sortedBy { it.toString() }

        for (i in sortedTypes) {
            for (j in sortedTypes) {
                results.add("$i ?= $j : ${i.isAssignableFrom(j)}")
            }
        }
    }

    override fun toResult(): List<String> {
        return results
    }

}

class TypeCollectorNoAccessor : TypeCollector() {
    override fun visitPropertyGetter(getter: KSPropertyGetter, data: MutableCollection<KSType>) {
    }

    override fun visitPropertySetter(setter: KSPropertySetter, data: MutableCollection<KSType>) {
    }
}

open class TypeCollector : KSTopDownVisitor<MutableCollection<KSType>, Unit>() {
    override fun defaultHandler(node: KSNode, data: MutableCollection<KSType>) = Unit

    override fun visitTypeReference(typeReference: KSTypeReference, data: MutableCollection<KSType>) {
        super.visitTypeReference(typeReference, data)
        typeReference.resolve().let { data.add(it) }
    }
}
