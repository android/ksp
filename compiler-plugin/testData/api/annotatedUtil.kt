/*
 * Copyright 2021 Google LLC
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
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

// TEST PROCESSOR: AnnotatedUtilProcessor
// EXPECTED:
// Test: OnlyTestAnnotation
// Test: ParametersTestAnnotationWithValuesTest
// IsPresent: class com.google.devtools.ksp.processor.ParametersTestAnnotation
// ByType: com.google.devtools.ksp.processor.ParametersTestAnnotation[booleanValue=true, byteValue=5, charValue=k, doubleValue=5.12, floatValue=123.3, intValue=2, longValue=4, stringValue=someValue, kClassValue=class java.lang.Throwable, enumValue=VALUE1]
// Test: ParametersTestAnnotationWithDefaultsTest
// IsPresent: class com.google.devtools.ksp.processor.ParametersTestAnnotation
// ByType: com.google.devtools.ksp.processor.ParametersTestAnnotation[kClassValue=interface com.google.devtools.ksp.processor.ParametersTestAnnotation, booleanValue=false, byteValue=2, charValue=b, doubleValue=3.0, floatValue=4.0, intValue=5, longValue=6, stringValue=emptystring, enumValue=NONE]
// ByType: com.google.devtools.ksp.processor.ParametersTestAnnotation[kClassValue=interface com.google.devtools.ksp.processor.ParametersTestAnnotation, booleanValue=false, byteValue=2, charValue=b, doubleValue=3.0, floatValue=4.0, intValue=5, longValue=6, stringValue=emptystring, enumValue=NONE]
// Test: ParametersTestWithNegativeDefaultsAnnotationTest
// IsPresent: class com.google.devtools.ksp.processor.ParametersTestWithNegativeDefaultsAnnotation
// ByType: com.google.devtools.ksp.processor.ParametersTestWithNegativeDefaultsAnnotation[byteValue=-2, doubleValue=-3.0, floatValue=-4.0, intValue=-5, longValue=-6]
// ByType: com.google.devtools.ksp.processor.ParametersTestWithNegativeDefaultsAnnotation[byteValue=-2, doubleValue=-3.0, floatValue=-4.0, intValue=-5, longValue=-6]
// Test: ParameterArraysTestAnnotationWithDefaultTest
// IsPresent: class com.google.devtools.ksp.processor.ParameterArraysTestAnnotation
// ByType: ParameterArraysTestAnnotation[booleanArrayValue=[true, false],byteArrayValue=[-2, 4],charArrayValue=[a, b, c],doubleArrayValue=[1.1, 2.2, 3.3],floatArrayValue=[1.0, 2.0, 3.3],intArrayValue=[1, 2, 4, 8, 16],longArrayValue=[1, 2, 4, 8, 16, 32],stringArrayValue=[first, second, third],kClassArrayValue=[class kotlin.Throwable, class com.google.devtools.ksp.processor.ParametersTestAnnotation],enumArrayValue=[VALUE1, VALUE2, VALUE1, VALUE2]]
// Test: AnnotationWithinAnAnnotationTest
// IsPresent: class com.google.devtools.ksp.processor.OuterAnnotation
// ByType: com.google.devtools.ksp.processor.OuterAnnotation[innerAnnotation=com.google.devtools.ksp.processor.InnerAnnotation[value=hello from the other side]]
// END
// MODULE: annotations
// FILE: com/google/devtools/ksp/processor/a.kt
package com.google.devtools.ksp.processor

import kotlin.reflect.KClass
import java.lang.Throwable

annotation class Test

@Suppress("LongParameterList")
annotation class ParametersTestAnnotation(
    val booleanValue: Boolean = false,
    val byteValue: Byte = 2,
    val charValue: Char = 'b',
    val doubleValue: Double = 3.0,
    val floatValue: Float = 4.0f,
    val intValue: Int = 5,
    val longValue: Long = 6L,
    val stringValue: String = "emptystring",
    // fails on getting the arguments from the KSAnnotation when no value is set for the kClassValue in
    // the declaration. Throws an NPE with using a default value
    val kClassValue: KClass<*> = ParametersTestAnnotation::class,
    val enumValue: TestEnum = TestEnum.NONE,
)

@Suppress("LongParameterList")
annotation class ParameterArraysTestAnnotation(
    val booleanArrayValue: BooleanArray = booleanArrayOf(),
    val byteArrayValue: ByteArray = byteArrayOf(),
    val charArrayValue: CharArray = charArrayOf(),
    val doubleArrayValue: DoubleArray = doubleArrayOf(),
    val floatArrayValue: FloatArray = floatArrayOf(),
    val intArrayValue: IntArray = intArrayOf(),
    val longArrayValue: LongArray = longArrayOf(),
    val stringArrayValue: Array<String> = emptyArray(),
    val kClassArrayValue: Array<KClass<*>> = emptyArray(),
    val enumArrayValue: Array<TestEnum> = emptyArray(),
)

annotation class ParametersTestWithNegativeDefaultsAnnotation(
    val byteValue: Byte = -2,
    val doubleValue: Double = -3.0,
    val floatValue: Float = -4.0f,
    val intValue: Int = -5,
    val longValue: Long = -6L,
)

enum class TestEnum {
    NONE, VALUE1, VALUE2
}

annotation class InnerAnnotation(val value: String = "default")

annotation class OuterAnnotation(
    val innerAnnotation : InnerAnnotation = InnerAnnotation()
)

/////////////////////////////////////////////////////////
// Tests
/////////////////////////////////////////////////////////

@Test
@Test
class OnlyTestAnnotation

@ParametersTestAnnotation(
    booleanValue = true,
    byteValue = 5,
    charValue = 'k',
    doubleValue = 5.12,
    floatValue = 123.3f,
    intValue = 2,
    longValue = 4L,
    stringValue = "someValue",
    java.lang.Throwable::class,
    TestEnum.VALUE1,
)
@Test
class ParametersTestAnnotationWithValuesTest

@ParametersTestAnnotation(kClassValue = ParametersTestAnnotation::class)
@ParametersTestAnnotation(kClassValue = ParametersTestAnnotation::class)
@Test
class ParametersTestAnnotationWithDefaultsTest

@ParametersTestWithNegativeDefaultsAnnotation
@ParametersTestWithNegativeDefaultsAnnotation
@Test
class ParametersTestWithNegativeDefaultsAnnotationTest

@ParameterArraysTestAnnotation(
    booleanArrayValue = booleanArrayOf(true, false),
    byteArrayValue = byteArrayOf(-2, 4),
    charArrayValue = charArrayOf('a', 'b', 'c'),
    doubleArrayValue = doubleArrayOf(1.1, 2.2, 3.3),
    floatArrayValue = floatArrayOf(1.0f, 2.0f, 3.3f),
    intArrayValue = intArrayOf(1, 2, 4, 8, 16),
    longArrayValue = longArrayOf(1L, 2L, 4L, 8L, 16, 32L),
    stringArrayValue = arrayOf("first", "second", "third"),
    kClassArrayValue = arrayOf(Throwable::class, ParametersTestAnnotation::class),
    enumArrayValue = arrayOf(TestEnum.VALUE1, TestEnum.VALUE2, TestEnum.VALUE1, TestEnum.VALUE2),
)
@Test
class ParameterArraysTestAnnotationWithDefaultTest

@OuterAnnotation(innerAnnotation = InnerAnnotation("hello from the other side"))
@Test
class AnnotationWithinAnAnnotationTest
