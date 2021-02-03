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
// TEST PROCESSOR: ConstructorDeclarationsProcessor
// EXPECTED:
// class: AbstractJavaClassWithExplicitConstructor
// <init>(kotlin.Int): AbstractJavaClassWithExplicitConstructor
// class: lib.AbstractJavaClassWithExplicitConstructor
// <init>(kotlin.Int): lib.AbstractJavaClassWithExplicitConstructor
// class: AbstractJavaClassWithExplicitEmptyConstructor
// <init>(): AbstractJavaClassWithExplicitEmptyConstructor
// class: lib.AbstractJavaClassWithExplicitEmptyConstructor
// <init>(): lib.AbstractJavaClassWithExplicitEmptyConstructor
// class: AbstractJavaClassWithMultipleConstructors1
// <init>(): AbstractJavaClassWithMultipleConstructors1
// <init>(kotlin.Int): AbstractJavaClassWithMultipleConstructors1
// <init>(kotlin.String): AbstractJavaClassWithMultipleConstructors1
// class: lib.AbstractJavaClassWithMultipleConstructors1
// <init>(): lib.AbstractJavaClassWithMultipleConstructors1
// <init>(kotlin.Int): lib.AbstractJavaClassWithMultipleConstructors1
// <init>(kotlin.String): lib.AbstractJavaClassWithMultipleConstructors1
// class: AbstractJavaClassWithoutExplicitConstructor
// <init>(): AbstractJavaClassWithoutExplicitConstructor
// class: lib.AbstractJavaClassWithoutExplicitConstructor
// <init>(): lib.AbstractJavaClassWithoutExplicitConstructor
// class: AbstractKotlinClassWithExplicitConstructor
// <init>(kotlin.Int): AbstractKotlinClassWithExplicitConstructor
// class: lib.AbstractKotlinClassWithExplicitConstructor
// <init>(kotlin.Int): lib.AbstractKotlinClassWithExplicitConstructor
// class: AbstractKotlinClassWithExplicitEmptyConstructor
// <init>(): AbstractKotlinClassWithExplicitEmptyConstructor
// class: lib.AbstractKotlinClassWithExplicitEmptyConstructor
// <init>(): lib.AbstractKotlinClassWithExplicitEmptyConstructor
// class: AbstractKotlinClassWithMultipleConstructors1
// <init>(): AbstractKotlinClassWithMultipleConstructors1
// <init>(kotlin.Int): AbstractKotlinClassWithMultipleConstructors1
// <init>(kotlin.String): AbstractKotlinClassWithMultipleConstructors1
// class: lib.AbstractKotlinClassWithMultipleConstructors1
// <init>(): lib.AbstractKotlinClassWithMultipleConstructors1
// <init>(kotlin.Int): lib.AbstractKotlinClassWithMultipleConstructors1
// <init>(kotlin.String): lib.AbstractKotlinClassWithMultipleConstructors1
// class: AbstractKotlinClassWithMultipleConstructors2
// <init>(kotlin.Float): AbstractKotlinClassWithMultipleConstructors2
// <init>(kotlin.Int): AbstractKotlinClassWithMultipleConstructors2
// <init>(kotlin.String): AbstractKotlinClassWithMultipleConstructors2
// class: lib.AbstractKotlinClassWithMultipleConstructors2
// <init>(kotlin.Float): lib.AbstractKotlinClassWithMultipleConstructors2
// <init>(kotlin.Int): lib.AbstractKotlinClassWithMultipleConstructors2
// <init>(kotlin.String): lib.AbstractKotlinClassWithMultipleConstructors2
// class: AbstractKotlinClassWithPrimaryConstructor
// <init>(kotlin.Int): AbstractKotlinClassWithPrimaryConstructor
// class: lib.AbstractKotlinClassWithPrimaryConstructor
// <init>(kotlin.Int): lib.AbstractKotlinClassWithPrimaryConstructor
// class: AbstractKotlinClassWithoutExplicitConstructor
// <init>(): AbstractKotlinClassWithoutExplicitConstructor
// class: lib.AbstractKotlinClassWithoutExplicitConstructor
// <init>(): lib.AbstractKotlinClassWithoutExplicitConstructor
// class: DataClass
// <init>(kotlin.Int,kotlin.String): DataClass
// class: lib.DataClass
// <init>(kotlin.Int,kotlin.String): lib.DataClass
// class: DataClassWithSecondaryConstructor
// <init>(kotlin.Int): DataClassWithSecondaryConstructor
// <init>(kotlin.Int,kotlin.String): DataClassWithSecondaryConstructor
// class: lib.DataClassWithSecondaryConstructor
// <init>(kotlin.Int): lib.DataClassWithSecondaryConstructor
// <init>(kotlin.Int,kotlin.String): lib.DataClassWithSecondaryConstructor
// class: JavaAnnotation
// <init>(): JavaAnnotation
// class: lib.JavaAnnotation
// <init>(): lib.JavaAnnotation
// class: JavaClassWithExplicitConstructor
// <init>(kotlin.Int): JavaClassWithExplicitConstructor
// class: lib.JavaClassWithExplicitConstructor
// <init>(kotlin.Int): lib.JavaClassWithExplicitConstructor
// class: JavaClassWithExplicitEmptyConstructor
// <init>(): JavaClassWithExplicitEmptyConstructor
// class: lib.JavaClassWithExplicitEmptyConstructor
// <init>(): lib.JavaClassWithExplicitEmptyConstructor
// class: JavaClassWithMultipleConstructors1
// <init>(): JavaClassWithMultipleConstructors1
// <init>(kotlin.Int): JavaClassWithMultipleConstructors1
// <init>(kotlin.String): JavaClassWithMultipleConstructors1
// class: lib.JavaClassWithMultipleConstructors1
// <init>(): lib.JavaClassWithMultipleConstructors1
// <init>(kotlin.Int): lib.JavaClassWithMultipleConstructors1
// <init>(kotlin.String): lib.JavaClassWithMultipleConstructors1
// class: JavaClassWithoutExplicitConstructor
// <init>(): JavaClassWithoutExplicitConstructor
// class: lib.JavaClassWithoutExplicitConstructor
// <init>(): lib.JavaClassWithoutExplicitConstructor
// class: JavaInterface
// class: lib.JavaInterface
// class: KotlinAnnotation
// <init>(): KotlinAnnotation
// class: lib.KotlinAnnotation
// <init>(): lib.KotlinAnnotation
// class: KotlinClassWithCompanion
// <init>(): KotlinClassWithCompanion
// class: lib.KotlinClassWithCompanion
// <init>(): lib.KotlinClassWithCompanion
// class: KotlinClassWithExplicitConstructor
// <init>(kotlin.Int): KotlinClassWithExplicitConstructor
// class: lib.KotlinClassWithExplicitConstructor
// <init>(kotlin.Int): lib.KotlinClassWithExplicitConstructor
// class: KotlinClassWithExplicitEmptyConstructor
// <init>(): KotlinClassWithExplicitEmptyConstructor
// class: lib.KotlinClassWithExplicitEmptyConstructor
// <init>(): lib.KotlinClassWithExplicitEmptyConstructor
// class: KotlinClassWithMultipleConstructors1
// <init>(): KotlinClassWithMultipleConstructors1
// <init>(kotlin.Int): KotlinClassWithMultipleConstructors1
// <init>(kotlin.String): KotlinClassWithMultipleConstructors1
// class: lib.KotlinClassWithMultipleConstructors1
// <init>(): lib.KotlinClassWithMultipleConstructors1
// <init>(kotlin.Int): lib.KotlinClassWithMultipleConstructors1
// <init>(kotlin.String): lib.KotlinClassWithMultipleConstructors1
// class: KotlinClassWithMultipleConstructors2
// <init>(kotlin.Float): KotlinClassWithMultipleConstructors2
// <init>(kotlin.Int): KotlinClassWithMultipleConstructors2
// <init>(kotlin.String): KotlinClassWithMultipleConstructors2
// class: lib.KotlinClassWithMultipleConstructors2
// <init>(kotlin.Float): lib.KotlinClassWithMultipleConstructors2
// <init>(kotlin.Int): lib.KotlinClassWithMultipleConstructors2
// <init>(kotlin.String): lib.KotlinClassWithMultipleConstructors2
// class: KotlinClassWithNamedCompanion
// <init>(): KotlinClassWithNamedCompanion
// class: lib.KotlinClassWithNamedCompanion
// <init>(): lib.KotlinClassWithNamedCompanion
// class: KotlinClassWithPrimaryConstructor
// <init>(kotlin.Int): KotlinClassWithPrimaryConstructor
// class: lib.KotlinClassWithPrimaryConstructor
// <init>(kotlin.Int): lib.KotlinClassWithPrimaryConstructor
// class: KotlinClassWithoutExplicitConstructor
// <init>(): KotlinClassWithoutExplicitConstructor
// class: lib.KotlinClassWithoutExplicitConstructor
// <init>(): lib.KotlinClassWithoutExplicitConstructor
// class: KotlinInterface
// class: lib.KotlinInterface
// class: KotlinObject
// <init>(): KotlinObject
// class: lib.KotlinObject
// <init>(): lib.KotlinObject
// END

// MODULE: lib
// FILE: lib/JavaInterface.java
package lib;
interface JavaInterface {
}
// FILE: lib/AbstractJavaClassWithoutExplicitConstructor.java
package lib;
abstract class AbstractJavaClassWithoutExplicitConstructor {
}
// FILE: lib/AbstractJavaClassWithExplicitEmptyConstructor.java
package lib;
abstract class AbstractJavaClassWithExplicitEmptyConstructor {
    AbstractJavaClassWithExplicitEmptyConstructor() {}
}
// FILE: lib/AbstractJavaClassWithExplicitConstructor.java
package lib;
abstract class AbstractJavaClassWithExplicitConstructor {
    AbstractJavaClassWithExplicitConstructor(int x) {}
}
// FILE: lib/AbstractJavaClassWithMultipleConstructors1.java
package lib;
abstract class AbstractJavaClassWithMultipleConstructors1 {
    AbstractJavaClassWithMultipleConstructors1() {}
    AbstractJavaClassWithMultipleConstructors1(int y) {}
    AbstractJavaClassWithMultipleConstructors1(String x) {}
}
// FILE: lib/JavaClassWithoutExplicitConstructor.java
package lib;
class JavaClassWithoutExplicitConstructor {
}
// FILE: lib/JavaClassWithExplicitEmptyConstructor.java
package lib;
class JavaClassWithExplicitEmptyConstructor {
    JavaClassWithExplicitEmptyConstructor() {}
}
// FILE: lib/JavaClassWithExplicitConstructor.java
package lib;
class JavaClassWithExplicitConstructor {
    JavaClassWithExplicitConstructor(int x) {}
}
// FILE: lib/JavaClassWithMultipleConstructors1.java
package lib;
class JavaClassWithMultipleConstructors1 {
    JavaClassWithMultipleConstructors1() {}
    JavaClassWithMultipleConstructors1(int y) {}
    JavaClassWithMultipleConstructors1(String x) {}
}
// FILE: JavaAnnotation.java
package lib;
public @interface JavaAnnotation {
}
// FILE: kotlin_lib.kt
package lib
interface KotlinInterface {}
class KotlinClassWithoutExplicitConstructor {
}
class KotlinClassWithExplicitEmptyConstructor() {}
class KotlinClassWithPrimaryConstructor(x:Int) {
}
class KotlinClassWithExplicitConstructor {
    constructor(x:Int) {}
}
class KotlinClassWithMultipleConstructors1 {
    constructor() {}
    constructor(y:Int): this() {}
    constructor(x: String) : this() {}
}
class KotlinClassWithMultipleConstructors2(z:Float) {
    constructor(y:Int): this(0f) {}
    constructor(x: String) : this(0f) {}
}
abstract class AbstractKotlinClassWithoutExplicitConstructor {
}
abstract class AbstractKotlinClassWithPrimaryConstructor(x:Int) {
}
abstract class AbstractKotlinClassWithExplicitEmptyConstructor() {}
abstract class AbstractKotlinClassWithExplicitConstructor {
    constructor(x:Int) {}
}
abstract class AbstractKotlinClassWithMultipleConstructors1 {
    constructor() {}
    constructor(y:Int): this() {}
    constructor(x: String) : this() {}
}
abstract class AbstractKotlinClassWithMultipleConstructors2(z:Float) {
    constructor(y:Int): this(0f) {}
    constructor(x: String) : this(0f) {}
}
annotation class KotlinAnnotation
object KotlinObject {}
class KotlinClassWithCompanion {
    companion object
}
class KotlinClassWithNamedCompanion {
    companion object MyCompanion
}
data class DataClass(val x:Int, var y:String)
data class DataClassWithSecondaryConstructor(val x:Int, val y:String) {
    constructor(x:Int) : this(x, "")
}
// MODULE: main(lib)
// FILE: JavaInterface.java
interface JavaInterface {
}
// FILE: AbstractJavaClassWithoutExplicitConstructor.java
abstract class AbstractJavaClassWithoutExplicitConstructor {
}
// FILE: AbstractJavaClassWithExplicitEmptyConstructor.java
abstract class AbstractJavaClassWithExplicitEmptyConstructor {
    AbstractJavaClassWithExplicitEmptyConstructor() {}
}
// FILE: AbstractJavaClassWithExplicitConstructor.java
abstract class AbstractJavaClassWithExplicitConstructor {
    AbstractJavaClassWithExplicitConstructor(int x) {}
}
// FILE: AbstractJavaClassWithMultipleConstructors1.java
abstract class AbstractJavaClassWithMultipleConstructors1 {
    AbstractJavaClassWithMultipleConstructors1() {}
    AbstractJavaClassWithMultipleConstructors1(int y) {}
    AbstractJavaClassWithMultipleConstructors1(String x) {}
}
// FILE: JavaClassWithoutExplicitConstructor.java
class JavaClassWithoutExplicitConstructor {
}
// FILE: JavaClassWithExplicitEmptyConstructor.java
class JavaClassWithExplicitEmptyConstructor {
    JavaClassWithExplicitEmptyConstructor() {}
}
// FILE: JavaClassWithExplicitConstructor.java
class JavaClassWithExplicitConstructor {
    JavaClassWithExplicitConstructor(int x) {}
}
// FILE: JavaClassWithMultipleConstructors1.java
class JavaClassWithMultipleConstructors1 {
    JavaClassWithMultipleConstructors1() {}
    JavaClassWithMultipleConstructors1(int y) {}
    JavaClassWithMultipleConstructors1(String x) {}
}
// FILE: JavaAnnotation.java
public @interface JavaAnnotation {
}
// FILE: kotlin.kt
interface KotlinInterface {}
class KotlinClassWithoutExplicitConstructor {
}
class KotlinClassWithExplicitEmptyConstructor() {}
class KotlinClassWithPrimaryConstructor(x:Int) {
}
class KotlinClassWithExplicitConstructor {
    constructor(x:Int) {}
}
class KotlinClassWithMultipleConstructors1 {
    constructor() {}
    constructor(y:Int): this() {}
    constructor(x: String) : this() {}
}
class KotlinClassWithMultipleConstructors2(z:Float) {
    constructor(y:Int): this(0f) {}
    constructor(x: String) : this(0f) {}
}
abstract class AbstractKotlinClassWithoutExplicitConstructor {
}
abstract class AbstractKotlinClassWithPrimaryConstructor(x:Int) {
}
abstract class AbstractKotlinClassWithExplicitEmptyConstructor() {}
abstract class AbstractKotlinClassWithExplicitConstructor {
    constructor(x:Int) {}
}
abstract class AbstractKotlinClassWithMultipleConstructors1 {
    constructor() {}
    constructor(y:Int): this() {}
    constructor(x: String) : this() {}
}
abstract class AbstractKotlinClassWithMultipleConstructors2(z:Float) {
    constructor(y:Int): this(0f) {}
    constructor(x: String) : this(0f) {}
}
annotation class KotlinAnnotation
object KotlinObject {}
class KotlinClassWithCompanion {
    companion object
}
class KotlinClassWithNamedCompanion {
    companion object MyCompanion
}
data class DataClass(val x:Int, var y:String)
data class DataClassWithSecondaryConstructor(val x:Int, val y:String) {
    constructor(x:Int) : this(x, "")
}