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
// TEST PROCESSOR: TypeAliasProcessor
// EXPECTED:
// A = String
// A = String
// B = String
// C = A = String
// Int
// List<Int>
// ListOfInt = List<Int>
// ListOfInt = List<Int>
// ListOfInt_B = ListOfInt = List<Int>
// ListOfInt_B = ListOfInt = List<Int>
// ListOfInt_C = ListOfInt_B = ListOfInt = List<Int>
// String
// String
// String
// END

typealias A = String
typealias B = String
typealias C = A
typealias ListOfInt = List<Int>
typealias ListOfInt_B = ListOfInt
typealias ListOfInt_C = ListOfInt_B
val a: A = ""
val b: B = ""
val c: C = ""
val d: String = ""
val listOfInt: ListOfInt = TODO()
val listOfInt_B: ListOfInt_B = TODO()
val listOfInt_C: ListOfInt_C = TODO()
