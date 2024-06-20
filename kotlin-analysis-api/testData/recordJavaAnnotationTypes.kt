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
// TEST PROCESSOR: RecordJavaProcessor
// EXPECTED:
// kotlin.Annotation: main/p1/J.java
// kotlin.Any: main/p1/J.java
// kotlin.collections.List: main/p1/J.java
// kotlin.collections.MutableList: main/p1/J.java
// p1.Anno: main/p1/J.java
// p1.Bnno: main/p1/J.java
// p1.J: main/p1/J.java
// p1.K: main/p1/J.java
// END

// FILE: p1/J.java
package p1;

import java.util.List;

@interface Anno {
}

@Anno
@Bnno
public class J {
    List<K> l = null;
}

// FILE: p1/K.kt
package p1;

annotation class Bnno

class K
