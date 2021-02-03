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
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.visitor.KSTopDownVisitor

class DeclarationPackageNameProcessor : AbstractTestProcessor() {
    val result = mutableListOf<String>()

    override fun toResult(): List<String> {
        return result
    }

    override fun process(resolver: Resolver) {
        val visitor = NameCollector()
        resolver.getAllFiles().map { it.accept(visitor, result) }
    }
}

class NameCollector : KSTopDownVisitor<MutableCollection<String>, Unit>() {

    override fun defaultHandler(node: KSNode, data: MutableCollection<String>) {}

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: MutableCollection<String>) {
        classDeclaration.packageName.asString().let { data.add("${if (it == "") "<no name>" else it}:${classDeclaration.simpleName.asString()}") }
        super.visitClassDeclaration(classDeclaration, data)
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: MutableCollection<String>) {
        function.packageName.asString().let { packageName ->
            val packageNamePrefix = if (packageName == "") "<no name>" else packageName
            val parentDeclaration = function.parentDeclaration
            val parentPrefix = if (parentDeclaration == null) {
                ""
            } else {
                "${parentDeclaration.simpleName.asString()}."
            }
            data.add("$packageNamePrefix:$parentPrefix${function.simpleName.asString()}")
        }
        super.visitFunctionDeclaration(function, data)
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: MutableCollection<String>) {
        property.packageName.asString().let { data.add("${if (it == "") "<no name>" else it}:${property.simpleName.asString()}") }
        super.visitPropertyDeclaration(property, data)
    }
}