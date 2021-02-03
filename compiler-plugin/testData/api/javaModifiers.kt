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

// TEST PROCESSOR: JavaModifierProcessor
// EXPECTED:
// C: PUBLIC ABSTRACT
// C.staticStr: PRIVATE
// C.s1: FINAL JAVA_TRANSIENT
// C.i1: PROTECTED JAVA_STATIC JAVA_VOLATILE
// C.NestedC: PUBLIC JAVA_STATIC
// NestedC.<init>: FINAL PUBLIC
// C.InnerC: PUBLIC
// InnerC.<init>: FINAL PUBLIC
// C.intFun: JAVA_SYNCHRONIZED JAVA_DEFAULT
// C.foo: ABSTRACT JAVA_STRICT
// C.<init>: FINAL PUBLIC
// OuterJavaClass: PUBLIC
// OuterJavaClass.InnerJavaClass: PUBLIC
// InnerJavaClass.<init>: FINAL PUBLIC
// OuterJavaClass.NestedJavaClass: PUBLIC JAVA_STATIC
// NestedJavaClass.<init>: FINAL PUBLIC
// OuterJavaClass.<init>: FINAL PUBLIC
// OuterKotlinClass: OPEN
// OuterKotlinClass.InnerKotlinClass: INNER
// InnerKotlinClass.<init>: FINAL PUBLIC
// OuterKotlinClass.NestedKotlinClass: OPEN
// NestedKotlinClass.<init>: FINAL PUBLIC
// OuterKotlinClass.<init>: FINAL PUBLIC
// DependencyOuterJavaClass: OPEN PUBLIC
// DependencyOuterJavaClass.DependencyNestedJavaClass: OPEN PUBLIC
// DependencyNestedJavaClass.<init>: FINAL PUBLIC
// DependencyOuterJavaClass.DependencyInnerJavaClass: OPEN PUBLIC INNER
// DependencyInnerJavaClass.<init>: FINAL PUBLIC
// DependencyOuterJavaClass.<init>: FINAL PUBLIC
// DependencyOuterKotlinClass: OPEN PUBLIC
// DependencyOuterKotlinClass.DependencyInnerKotlinClass: FINAL PUBLIC INNER
// DependencyInnerKotlinClass.<init>: FINAL PUBLIC
// DependencyOuterKotlinClass.DependencyNestedKotlinClass: OPEN PUBLIC
// DependencyNestedKotlinClass.<init>: FINAL PUBLIC
// DependencyOuterKotlinClass.<init>: FINAL PUBLIC
// END
// MODULE: module1
// FILE: DependencyOuterJavaClass.java
public class DependencyOuterJavaClass {
    public class DependencyInnerJavaClass {}
    public static class DependencyNestedJavaClass {}
}
// FILE: DependencyOuterKotlinClass.kt
open class DependencyOuterKotlinClass {
    inner class DependencyInnerKotlinClass
    open class DependencyNestedKotlinClass
}
// MODULE: main(module1)
// FILE: a.kt
annotation class Test

@Test
class Foo : C() {

}

@Test
class Bar : OuterJavaClass()

@Test
class Baz : OuterKotlinClass()

@Test
class JavaDependency : DependencyOuterJavaClass()

@Test
class KotlinDependency : DependencyOuterKotlinClass()

// FILE: C.java

public abstract class C {

    private String staticStr = "str"

    final transient String s1;

    protected static volatile int i1;

    default synchronized int intFun() {
        return 1;
    }

    abstract strictfp void foo() {}

    public static class NestedC {

    }

    public class InnerC {

    }
}

// FILE: OuterJavaClass.java
public class OuterJavaClass {
    public class InnerJavaClass {}
    public static class NestedJavaClass {}
}
// FILE: OuterKotlinClass.kt
open class OuterKotlinClass {
    inner class InnerKotlinClass
    open class NestedKotlinClass
}
