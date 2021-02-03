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

// WITH_RUNTIME
// TEST PROCESSOR: TypeComparisonProcessor
// EXPECTED:
// (kotlin.Array<(C..C?)>..kotlin.Array<out (C..C?)>?) ?= (kotlin.Array<(C..C?)>..kotlin.Array<out (C..C?)>?) : true
// (kotlin.Array<(C..C?)>..kotlin.Array<out (C..C?)>?) ?= Array<C> : true
// (kotlin.Array<(C..C?)>..kotlin.Array<out (C..C?)>?) ?= Array<D> : true
// Array<C> ?= (kotlin.Array<(C..C?)>..kotlin.Array<out (C..C?)>?) : true
// Array<C> ?= Array<C> : true
// Array<C> ?= Array<D> : false
// Array<D> ?= (kotlin.Array<(C..C?)>..kotlin.Array<out (C..C?)>?) : false
// Array<D> ?= Array<C> : false
// Array<D> ?= Array<D> : true
// END

// FILE: ArrayTest.java
class ArrayTest {
    public static C[] javaArrayOfC() {
        return null;
    }
}

// FILE: K.kt
@file:kotlin.Suppress("C", "D", "Suppress", "Any", "ArrayTest")

open class C
open class D : C()

val j = ArrayTest.javaArrayOfC()
val c: Array<C> = arrayOf()
val d: Array<D> = arrayOf()
