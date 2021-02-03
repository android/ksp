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

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.binary.KSTypeReferenceDescriptorImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeImpl
import com.google.devtools.ksp.visitor.KSTopDownVisitor

open class PlatformDeclarationProcessor: AbstractTestProcessor() {
    val results = mutableListOf<String>()
    val collector = EverythingVisitor()
    val declarations = mutableListOf<KSDeclaration>()

    override fun process(resolver: Resolver) {
        val files = resolver.getAllFiles()

        files.forEach {
            it.accept(collector, declarations)
        }

        declarations
            .filterNot {
                // TODO we should figure out how constructors work in expect-actual world
                //  expand this test to include constructors
                it is KSFunctionDeclaration && it.isConstructor()
            }
            .sortedBy { "${it.containingFile?.fileName} : ${it.qualifiedName?.asString()}" }.forEach {
            val r = mutableListOf<Any?>()
            r.add(it.containingFile?.fileName)
            r.add(it.qualifiedName?.asString())
            r.add(it.isActual)
            r.add(it.isExpect)
            r.add(it.findActuals().joinToString(", ", "[", "]") { it.containingFile?.fileName.toString() })
            r.add(it.findExpects().joinToString(", ", "[", "]") { it.containingFile?.fileName.toString() })
            results.add(r.map { it.toString() }.joinToString(" : "))
        }
    }

    override fun toResult(): List<String> {
        return results
    }

}

class EverythingVisitor : KSTopDownVisitor<MutableList<KSDeclaration>, Unit>() {
    override fun defaultHandler(node: KSNode, data: MutableList<KSDeclaration>) = Unit

    override fun visitDeclaration(declaration: KSDeclaration, data: MutableList<KSDeclaration>) {
        super.visitDeclaration(declaration, data)
        data.add(declaration)
    }
}
