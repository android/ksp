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
// TEST PROCESSOR: AnnotationsInDependenciesProcessor
// EXPECTED:
// main.KotlinClass ->
// class main.KotlinClass : annotations.ClassTarget{[value = onClass]}
// class main.KotlinClass : annotations.NoTargetAnnotation{[value = onClass]}
// function myFun : annotations.FunctionTarget{[value = onMyFun]}
// function myFun : annotations.NoTargetAnnotation{[value = onMyFun]}
// parameter param1 : annotations.NoTargetAnnotation{[value = onParam1]}
// parameter param1 : annotations.ValueParameterTarget{[value = onParam1]}
// parameter param2 : annotations.NoTargetAnnotation{[value = onParam2]}
// parameter param2 : annotations.ValueParameterTarget{[value = onParam2]}
// property prop : annotations.FieldTarget2{[value = field:]}
// property prop : annotations.FieldTarget{[value = onProp]}
// property prop : annotations.NoTargetAnnotation{[value = onProp]}
// property prop : annotations.PropertyGetterTarget{[value = get:]}
// property prop : annotations.PropertySetterTarget{[value = set:]}
// property prop : annotations.PropertyTarget{[value = onProp]}
// lib.KotlinClass ->
// class lib.KotlinClass : annotations.ClassTarget{[value = onClass]}
// class lib.KotlinClass : annotations.NoTargetAnnotation{[value = onClass]}
// function myFun : annotations.FunctionTarget{[value = onMyFun]}
// function myFun : annotations.NoTargetAnnotation{[value = onMyFun]}
// parameter param1 : annotations.NoTargetAnnotation{[value = onParam1]}
// parameter param1 : annotations.ValueParameterTarget{[value = onParam1]}
// parameter param2 : annotations.NoTargetAnnotation{[value = onParam2]}
// parameter param2 : annotations.ValueParameterTarget{[value = onParam2]}
// property prop : annotations.FieldTarget2{[value = field:]}
// property prop : annotations.FieldTarget{[value = onProp]}
// property prop : annotations.NoTargetAnnotation{[value = onProp]}
// property prop : annotations.PropertyGetterTarget{[value = get:]}
// property prop : annotations.PropertySetterTarget{[value = set:]}
// property prop : annotations.PropertyTarget{[value = onProp]}
// main.DataClass ->
// class main.DataClass : annotations.ClassTarget{[value = onDataClass]}
// class main.DataClass : annotations.NoTargetAnnotation{[value = onDataClass]}
// parameter constructorParam : annotations.FieldTarget2{[value = field:]}
// parameter constructorParam : annotations.FieldTarget{[value = onConstructorParam]}
// parameter constructorParam : annotations.NoTargetAnnotation{[value = onConstructorParam]}
// parameter constructorParam : annotations.PropertyGetterTarget{[value = get:]}
// parameter constructorParam : annotations.PropertySetterTarget{[value = set:]}
// parameter constructorParam : annotations.PropertyTarget{[value = onConstructorParam]}
// property constructorParam : annotations.FieldTarget2{[value = field:]}
// property constructorParam : annotations.FieldTarget{[value = onConstructorParam]}
// property constructorParam : annotations.NoTargetAnnotation{[value = onConstructorParam]}
// property constructorParam : annotations.PropertyGetterTarget{[value = get:]}
// property constructorParam : annotations.PropertySetterTarget{[value = set:]}
// property constructorParam : annotations.PropertyTarget{[value = onConstructorParam]}
// lib.DataClass ->
// class lib.DataClass : annotations.ClassTarget{[value = onDataClass]}
// class lib.DataClass : annotations.NoTargetAnnotation{[value = onDataClass]}
// parameter constructorParam : annotations.NoTargetAnnotation{[value = onConstructorParam]}
// property constructorParam : annotations.FieldTarget2{[value = field:]}
// property constructorParam : annotations.FieldTarget{[value = onConstructorParam]}
// property constructorParam : annotations.PropertyGetterTarget{[value = get:]}
// property constructorParam : annotations.PropertySetterTarget{[value = set:]}
// property constructorParam : annotations.PropertyTarget{[value = onConstructorParam]}
// END
// MODULE: annotations
// FILE: Annotations.kt
package annotations;
annotation class NoTargetAnnotation(val value:String)

@Target(AnnotationTarget.FIELD)
annotation class FieldTarget(val value:String)

@Target(AnnotationTarget.FIELD)
annotation class FieldTarget2(val value:String)

@Target(AnnotationTarget.PROPERTY)
annotation class PropertyTarget(val value:String)

@Target(AnnotationTarget.PROPERTY_SETTER)
annotation class PropertySetterTarget(val value:String)

@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class PropertyGetterTarget(val value:String)

@Target(AnnotationTarget.CLASS)
annotation class ClassTarget(val value:String)

@Target(AnnotationTarget.FUNCTION)
annotation class FunctionTarget(val value:String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class ValueParameterTarget(val value:String)
// MODULE: lib(annotations)
// FILE: ClassInLib.kt
package lib;
import annotations.*;
@NoTargetAnnotation("onClass")
@ClassTarget("onClass")
class KotlinClass {
    @NoTargetAnnotation("onProp")
    @FieldTarget("onProp")
    @PropertyTarget("onProp")
    @set:PropertySetterTarget("set:")
    @get:PropertyGetterTarget("get:")
    @field:FieldTarget2("field:")
    var prop : String = ""

    @NoTargetAnnotation("onMyFun")
    @FunctionTarget("onMyFun")
    fun myFun(
        @NoTargetAnnotation("onParam1")
        @ValueParameterTarget("onParam1")
        param1: String,
        @NoTargetAnnotation("onParam2")
        @ValueParameterTarget("onParam2")
        param2: Int
    ) {
    }
}

@NoTargetAnnotation("onDataClass")
@ClassTarget("onDataClass")
class DataClass(
    @NoTargetAnnotation("onConstructorParam")
    @FieldTarget("onConstructorParam")
    @PropertyTarget("onConstructorParam")
    @set:PropertySetterTarget("set:")
    @get:PropertyGetterTarget("get:")
    @field:FieldTarget2("field:")
    var constructorParam : String = ""
)
// FILE: lib/JavaClass.java
package lib;
import annotations.*;
public class JavaClass {}
// MODULE: main(lib, annotations)
// FILE: ClassInModule2.kt
package main;
import annotations.*;
@NoTargetAnnotation("onClass")
@ClassTarget("onClass")
class KotlinClass {
    @NoTargetAnnotation("onProp")
    @FieldTarget("onProp")
    @PropertyTarget("onProp")
    @set:PropertySetterTarget("set:")
    @get:PropertyGetterTarget("get:")
    @field:FieldTarget2("field:")
    var prop : String = ""

    @NoTargetAnnotation("onMyFun")
    @FunctionTarget("onMyFun")
    fun myFun(
        @NoTargetAnnotation("onParam1")
        @ValueParameterTarget("onParam1")
        param1: String,
        @NoTargetAnnotation("onParam2")
        @ValueParameterTarget("onParam2")
        param2: Int
    ) {
    }
}

@NoTargetAnnotation("onDataClass")
@ClassTarget("onDataClass")
class DataClass(
    @NoTargetAnnotation("onConstructorParam")
    @FieldTarget("onConstructorParam")
    @PropertyTarget("onConstructorParam")
    @set:PropertySetterTarget("set:")
    @get:PropertyGetterTarget("get:")
    @field:FieldTarget2("field:")
    var constructorParam : String = ""
)
// FILE: main/JavaClassInModule2.java
pakage main;
import annotations.*;
@NoTargetAnnotation
class JavaClassInMain {
}
