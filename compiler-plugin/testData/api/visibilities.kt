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

// TEST PROCESSOR: VisibilityProcessor
// EXPECTED:
// publicFun: PUBLIC,visible in A, B, D: true, true, true
// packageFun: JAVA_PACKAGE,visible in A, B, D: true, false, true
// privateFun: PRIVATE,visible in A, B, D: false, false, false
// protectedFun: PROTECTED,visible in A, B, D: true, false, true
// <init>: PUBLIC,visible in A, B, D: true, true, true
// END

// FILE: a.kt
annotation class TestA
annotation class TestB
annotation class TestD

@TestA
class A : C() {
}

@TestD
class D {}


// FILE: C.java
class C {
    public int publicFun() {
        return 1;
    }

    int packageFun() {
        return 1;
    }

    private int privateFun() {
        return 1;
    }

    protected int protectedFun() {
        return 1;
    }
}

// FILE: b.kt
package somePackage

import TestB

@TestB
class B
