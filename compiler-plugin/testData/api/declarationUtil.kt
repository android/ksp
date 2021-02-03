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

// TEST PROCESSOR: DeclarationUtilProcessor
// FORMAT: <name>: isInternal: isLocal: isPrivate: isProtected: isPublic: isOpen
// EXPECTED:
// Cls: true: false: false: false: false: false
// Cls / <init>: false: false: false: false: true: false
// Cls / <init> / aaa: false: true: false: false: false: false
// Cls.prop: false: false: false: false: true: true
// Cls.protectedProp: false: false: false: true: false: true
// Cls.abstractITFFun: false: false: false: false: true: true
// Cls.pri: false: false: true: false: false: false
// Cls.b: false: false: false: false: true: true
// Cls / <init>: false: false: false: false: true: false
// ITF: false: false: false: false: true: true
// ITF.prop: false: false: false: false: true: true
// ITF.protectedProp: false: false: false: true: false: true
// ITF.b: false: false: false: false: true: true
// ITF.abstractITFFun: false: false: false: false: true: true
// ITF.nonAbstractITFFun: false: false: false: false: true: true
// ITF.nonAbstractITFFun / aa: false: true: false: false: false: false
// END
// FILE: a.kt
internal class Cls(override val b: Int) : ITF {
    constructor() {
        val aaa = 2
        Cls(aaa)
    }
    override val prop: Int = 2

    override val protectedProp: Int = 2

    override fun abstractITFFun(): Int {
        return 2
    }

    private val pri: Int = 3
}

interface ITF {
    val prop: Int

    protected val protectedProp: Int

    val b: Int = 1

    fun abstractITFFun(): Int

    fun nonAbstractITFFun(): Int {
        val aa = "local"
        return 1
    }
}
