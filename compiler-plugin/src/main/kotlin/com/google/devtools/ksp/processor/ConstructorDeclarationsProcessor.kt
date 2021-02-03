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

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*

class ConstructorDeclarationsProcessor : AbstractTestProcessor() {
    val visitor = ConstructorsVisitor()

    override fun toResult(): List<String> {
        return visitor.toResult()
    }

    override fun process(resolver: Resolver) {
        resolver.getAllFiles().map { it.accept(visitor, Unit) }
        val classNames = visitor.classNames().toList() // copy
        // each class has a cousin in the lib package, visit them as well, make sure
        // we report the same structure when they are compiled code as well
        classNames.forEach {
            resolver
                .getClassDeclarationByName("lib.${it.simpleName.asString()}")
                ?.accept(visitor, Unit)
        }
    }

    class ConstructorsVisitor : KSVisitorVoid() {
        private val declarationsByClass = LinkedHashMap<KSClassDeclaration, MutableList<String>>()
        fun classNames() = declarationsByClass.keys
        fun toResult() : List<String> {
            return declarationsByClass.entries
                .sortedBy {
                    // sort by simple name to get cousin classes next to each-other
                    // since we traverse the lib after main, lib will be the second one
                    // because sortedBy is stable sort
                    it.key.simpleName.asString()
                }.flatMap {
                    listOf("class: " + it.key.qualifiedName!!.asString()) + it.value
                }
        }
        fun KSFunctionDeclaration.toSignature(): String {
            return this.simpleName.asString() +
                    "(${this.parameters.map { 
                        buildString {
                            append(it.type.resolve().declaration.qualifiedName?.asString())
                            if (it.hasDefault) {
                                append("(hasDefault)")
                            }
                        }
                    }.joinToString(",")})" +
                    ": ${this.returnType?.resolve()?.declaration?.qualifiedName?.asString()
                        ?: "<no-return>"}"
        }

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val declarations = mutableListOf<String>()
            declarations.addAll(
                classDeclaration.getConstructors().map {
                    it.toSignature()
                }.sorted()
            )
            // TODO add some assertions that if we go through he path of getDeclarations
            //  we still find the same constructors
            declarationsByClass[classDeclaration] = declarations
        }

        override fun visitFile(file: KSFile, data: Unit) {
            file.declarations.map { it.accept(this, Unit) }
        }
    }
}