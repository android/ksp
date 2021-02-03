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
// Foo<*> ?= Foo<*> : true
// Foo<*> ?= Foo<A> : true
// Foo<*> ?= Foo<C> : true
// Foo<*> ?= Foo<in B> : true
// Foo<*> ?= Foo<out B> : true
// Foo<A> ?= Foo<*> : false
// Foo<A> ?= Foo<A> : true
// Foo<A> ?= Foo<C> : false
// Foo<A> ?= Foo<in B> : false
// Foo<A> ?= Foo<out B> : false
// Foo<C> ?= Foo<*> : false
// Foo<C> ?= Foo<A> : false
// Foo<C> ?= Foo<C> : true
// Foo<C> ?= Foo<in B> : false
// Foo<C> ?= Foo<out B> : false
// Foo<in B> ?= Foo<*> : false
// Foo<in B> ?= Foo<A> : true
// Foo<in B> ?= Foo<C> : false
// Foo<in B> ?= Foo<in B> : true
// Foo<in B> ?= Foo<out B> : false
// Foo<out B> ?= Foo<*> : false
// Foo<out B> ?= Foo<A> : false
// Foo<out B> ?= Foo<C> : true
// Foo<out B> ?= Foo<in B> : false
// Foo<out B> ?= Foo<out B> : true
// END

@file:kotlin.Suppress("A", "B", "C", "Suppress")

open class A
open class B: A()
open class C: B()

class Foo<T>

var ib: Foo<in B> = Foo<B>()
var ob: Foo<out B> = Foo<B>()

var a: Foo<A> = Foo<A>()
var c: Foo<C> = Foo<C>()
