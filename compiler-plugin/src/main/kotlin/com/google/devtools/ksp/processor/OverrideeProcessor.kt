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

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

@Suppress("unused") // used by tests
class OverrideeProcessor: AbstractTestProcessor() {
    private val results = mutableListOf<String>()

    override fun toResult() = results

    override fun process(resolver: Resolver) {
        logSubject(resolver, "NoOverride")
        logSubject(resolver, "Subject")
        logSubject(resolver, "JavaSubject.Subject")
        logSubject(resolver, "lib.Subject")
        logSubject(resolver, "ConflictingSubject1")
        logSubject(resolver, "ConflictingSubject2")
        logSubject(resolver, "ConflictingSubject3")
        logSubject(resolver, "ConflictingSubject4")
        logSubject(resolver, "OverrideOrder1")
        logSubject(resolver, "OverrideOrder2")
    }

    private fun logSubject(resolver: Resolver, qName:String) {
        results.add("$qName:")
        val subject = resolver.getClassDeclarationByName(qName)!!
        subject.declarations.filterIsInstance<KSClassDeclaration>().forEach {
            logClass(it)
        }
        logClass(subject)
    }

    private fun logClass(subject: KSClassDeclaration) {
        subject.declarations.filterIsInstance<KSPropertyDeclaration>()
            .forEach {
                val signature = it.toSignature()
                val overrideeSignature = it.findOverridee()?.toSignature()
                results.add("$signature -> $overrideeSignature")
            }
        subject.declarations.filterIsInstance<KSFunctionDeclaration>()
            .filterNot { it.simpleName.asString() in IGNORED_METHOD_NAMES }
            .forEach {
                val signature = it.toSignature()
                val overrideeSignature = it.findOverridee()?.toSignature()
                results.add("$signature -> $overrideeSignature")
            }
    }

    private fun KSFunctionDeclaration.toSignature(): String {
        val self = this
        return buildString {
            append(self.closestClassDeclaration()?.simpleName?.asString())
            append(".")
            append(self.simpleName.asString())
            append(
                self.parameters.joinToString(", ", prefix = "(", postfix = ")") {
                    "${it.name?.asString()}:${it.type.resolve().declaration.simpleName.asString()}"
                }
            )
        }
    }

    private fun KSPropertyDeclaration.toSignature(): String {
        val self = this
        return buildString {
            append(self.closestClassDeclaration()?.simpleName?.asString())
            append(".")
            append(self.simpleName.asString())
        }
    }

    companion object {
        // ignore these methods as we receive syntetics of it from compiled code
        private val IGNORED_METHOD_NAMES = listOf("equals", "hashCode", "toString", "<init>")
    }
}

interface MyInterface {
    fun openFoo(): Int { return 1}
    fun absFoo(): Unit
}

interface MyInterface2 {
    fun absFoo(): Unit
}

abstract class MyAbstract: MyInterface {
    override fun absFoo(): Unit {val a = 1}
    override fun openFoo(): Int { return 2 }
}

class Subject2: MyInterface, MyAbstract() {
    override fun absFoo(): Unit = TODO()
}
