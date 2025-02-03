/*
 * Copyright 2022 Google LLC
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
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

package com.google.devtools.ksp.test

import org.jetbrains.kotlin.test.TestMetadata
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class KSPAATest : AbstractKSPAATest() {

    @TestMetadata("annotatedUtil.kt")
    @Test
    fun testAnnotatedUtil() {
        runTest("../test-utils/testData/api/annotatedUtil.kt")
    }

    @TestMetadata("javaAnnotatedUtil.kt")
    @Test
    fun testJavaAnnotatedUtil() {
        runTest("../test-utils/testData/api/javaAnnotatedUtil.kt")
    }

    @TestMetadata("abstractFunctions.kt")
    @Test
    fun testAbstractFunctions() {
        runTest("../test-utils/testData/api/abstractFunctions.kt")
    }

    @TestMetadata("allFunctions_java_inherits_kt.kt")
    @Test
    fun testAllFunctions_java_inherits_kt() {
        runTest("../test-utils/testData/api/allFunctions_java_inherits_kt.kt")
    }

    @TestMetadata("allFunctions_kotlin.kt")
    @Test
    fun testAllFunctions_kotlin() {
        runTest("../test-utils/testData/api/allFunctions_java_inherits_kt.kt")
    }

    @TestMetadata("allFunctions_kt_inherits_java.kt")
    @Test
    fun testAllFunctions_kt_inherits_java() {
        runTest("../kotlin-analysis-api/testData/allFunctions_kt_inherits_java.kt")
    }

    @TestMetadata("annotationInDependencies.kt")
    @Test
    fun testAnnotationsInDependencies() {
        runTest("../kotlin-analysis-api/testData/annotationInDependencies.kt")
    }

    @TestMetadata("annotationOnConstructorParameter.kt")
    @Test
    fun testAnnotationOnConstructorParameter() {
        runTest("../test-utils/testData/api/annotationOnConstructorParameter.kt")
    }

    @TestMetadata("annotationOnReceiver.kt")
    @Test
    fun testAnnotationOnReceiver() {
        runTest("../test-utils/testData/api/annotationOnReceiver.kt")
    }

    @TestMetadata("annotationsRepeatable.kt")
    @Test
    fun testAnnotationsRepeatable() {
        runTest("../kotlin-analysis-api/testData/annotationsRepeatable.kt")
    }

    @TestMetadata("annotationWithArbitraryClassValue.kt")
    @Test
    fun testAnnotationWithArbitraryClassValue() {
        runTest("../test-utils/testData/api/annotationWithArbitraryClassValue.kt")
    }

    @TestMetadata("defaultKClassValue.kt")
    @Test
    fun testAnnotationValue_defaultKClassValue() {
        runTest("../kotlin-analysis-api/testData/annotationValue/defaultKClassValue.kt")
    }

    @TestMetadata("annotationValue_java.kt")
    @Test
    fun testAnnotationValue_java() {
        runTest("../kotlin-analysis-api/testData/annotationValue/annotationValue_java.kt")
    }

    @TestMetadata("annotationValue_java2.kt")
    @Test
    fun testAnnotationValue_java2() {
        runTest("../kotlin-analysis-api/testData/annotationValue/annotationValue_java2.kt")
    }

    @TestMetadata("annotationValue_kt.kt")
    @Test
    fun testAnnotationValue_kt() {
        runTest("../kotlin-analysis-api/testData/annotationValue/annotationValue_kt.kt")
    }

    @TestMetadata("annotationWithArrayValue.kt")
    @Test
    fun testAnnotationWithArrayValue() {
        runTest("../test-utils/testData/api/annotationWithArrayValue.kt")
    }

    @TestMetadata("annotationWithDefault.kt")
    @Test
    fun testAnnotationWithDefault() {
        runTest("../kotlin-analysis-api/testData/annotationWithDefault.kt")
    }

    @TestMetadata("annotationWithDefaultValues.kt")
    @Test
    fun testAnnotationWithDefaultValues() {
        runTest("../kotlin-analysis-api/testData/annotationWithDefaultValues.kt")
    }

    @TestMetadata("annotationWithJavaTypeValue.kt")
    @Test
    fun testAnnotationWithJavaTypeValue() {
        runTest("../test-utils/testData/api/annotationWithJavaTypeValue.kt")
    }

    @TestMetadata("asMemberOf.kt")
    @Test
    fun testAsMemberOf() {
        runTest("../kotlin-analysis-api/testData/asMemberOf.kt")
    }

    @TestMetadata("backingFields.kt")
    @Test
    fun testBackingFields() {
        runTest("../test-utils/testData/api/backingFields.kt")
    }

    @TestMetadata("builtInTypes.kt")
    @Test
    fun testBuiltInTypes() {
        runTest("../test-utils/testData/api/builtInTypes.kt")
    }

    @TestMetadata("objCacheA.kt")
    @Test
    fun testObjCacheA() {
        runTest("../test-utils/testData/api/objCacheA.kt")
    }

    @TestMetadata("objCacheB.kt")
    @Test
    fun testObjCacheB() {
        runTest("../test-utils/testData/api/objCacheB.kt")
    }

    @TestMetadata("checkOverride.kt")
    @Test
    fun testCheckOverride() {
        runTest("../kotlin-analysis-api/testData/checkOverride.kt")
    }

    @TestMetadata("classKinds.kt")
    @Test
    fun testClassKinds() {
        runTest("../test-utils/testData/api/classKinds.kt")
    }

    @TestMetadata("companion.kt")
    @Test
    fun testCompanion() {
        runTest("../test-utils/testData/api/companion.kt")
    }

    @TestMetadata("constProperties.kt")
    @Test
    fun testConstProperties() {
        runTest("../test-utils/testData/api/constProperties.kt")
    }

    @TestMetadata("constructorDeclarations.kt")
    @Test
    fun testConstructorDeclarations() {
        runTest("../test-utils/testData/api/constructorDeclarations.kt")
    }

    @TestMetadata("crossModuleTypeAlias.kt")
    @Test
    fun testCrossModuleTypeAlias() {
        runTest("../test-utils/testData/api/crossModuleTypeAlias.kt")
    }

    @TestMetadata("declarationInconsistency.kt")
    @Test
    fun testDeclarationInconsistency() {
        runTest("../test-utils/testData/api/declarationInconsistency.kt")
    }

    @TestMetadata("declarationPackageName.kt")
    @Test
    fun testDeclarationPackageName() {
        runTest("../test-utils/testData/api/declarationPackageName.kt")
    }

    @TestMetadata("declarationsInAccessor.kt")
    @Test
    fun testDeclarationsInAccessor() {
        runTest("../test-utils/testData/api/declarationsInAccessor.kt")
    }

    @TestMetadata("declarationOrder.kt")
    @Test
    fun testDeclarationOrder() {
        runTest("../kotlin-analysis-api/testData/declarationOrder.kt")
    }

    @TestMetadata("declarationUtil.kt")
    @Test
    fun testDeclarationUtil() {
        runTest("../kotlin-analysis-api/testData/declarationUtil.kt")
    }

    @TestMetadata("declared.kt")
    @Test
    fun testDeclared() {
        runTest("../test-utils/testData/api/declared.kt")
    }

    @TestMetadata("docString.kt")
    @Test
    fun testDocString() {
        runTest("../test-utils/testData/api/docString.kt")
    }

    @TestMetadata("equals.kt")
    @Test
    fun testEquals() {
        runTest("../test-utils/testData/api/equals.kt")
    }

    @TestMetadata("equivalentJavaWildcards.kt")
    @Test
    fun testEquivalentJavaWildcards() {
        runTest("../kotlin-analysis-api/testData/equivalentJavaWildcards.kt")
    }

    @TestMetadata("errorTypes.kt")
    @Test
    fun testErrorTypes() {
        runTest("../kotlin-analysis-api/testData/errorTypes.kt")
    }

    @TestMetadata("functionTypeAlias.kt")
    @Test
    fun testFunctionTypeAlias() {
        runTest("../test-utils/testData/api/functionTypeAlias.kt")
    }

    @TestMetadata("functionTypeAnnotation.kt")
    @Test
    fun testFunctionTypeAnnotation() {
        runTest("../test-utils/testData/api/functionTypeAnnotation.kt")
    }

    @TestMetadata("functionTypes.kt")
    @Test
    fun testFunctionTypes() {
        runTest("../test-utils/testData/api/functionTypes.kt")
    }

    @TestMetadata("getAnnotationByTypeWithInnerDefault.kt")
    @Test
    fun testGetAnnotationByTypeWithInnerDefault() {
        runTest("../test-utils/testData/api/getAnnotationByTypeWithInnerDefault.kt")
    }

    @TestMetadata("getPackage.kt")
    @Test
    fun testGetPackage() {
        runTest("../kotlin-analysis-api/testData/getPackage.kt")
    }

    @TestMetadata("getByName.kt")
    @Test
    fun testGetByName() {
        runTest("../test-utils/testData/api/getByName.kt")
    }

    @TestMetadata("getSymbolsFromAnnotation.kt")
    @Test
    fun testGetSymbolsFromAnnotation() {
        runTest("../kotlin-analysis-api/testData/getSymbolsFromAnnotation.kt")
    }

    @TestMetadata("getSymbolsFromAnnotationInLib.kt")
    @Test
    fun testGetSymbolsFromAnnotationInLib() {
        runTest("../kotlin-analysis-api/testData/getSymbolsFromAnnotationInLib.kt")
    }

    @TestMetadata("hello.kt")
    @Test
    fun testHello() {
        runTest("../test-utils/testData/api/hello.kt")
    }

    @TestMetadata("implicitElements.kt")
    @Test
    fun testImplicitElements() {
        runTest("../test-utils/testData/api/implicitElements.kt")
    }

    @TestMetadata("implicitPropertyAccessors.kt")
    @Test
    fun testImplicitPropertyAccessors() {
        runTest("../kotlin-analysis-api/testData/implicitPropertyAccessors.kt")
    }

    @TestMetadata("inheritedTypeAlias.kt")
    @Test
    fun testInheritedTypeAlias() {
        runTest("../test-utils/testData/api/inheritedTypeAlias.kt")
    }

    @TestMetadata("innerTypes.kt")
    @Test
    fun testInnerTypes() {
        runTest("../kotlin-analysis-api/testData/innerTypes.kt")
    }

    @TestMetadata("interfaceWithDefault.kt")
    @Test
    fun testInterfaceWithDefault() {
        runTest("../test-utils/testData/api/interfaceWithDefault.kt")
    }

    @TestMetadata("javaModifiers.kt")
    @Test
    fun testJavaModifiers() {
        runTest("../kotlin-analysis-api/testData/javaModifiers.kt")
    }

    @TestMetadata("javaNonNullTypes.kt")
    @Test
    fun testJavaNonNullTypes() {
        runTest("../test-utils/testData/api/javaNonNullTypes.kt")
    }

    @TestMetadata("javaSubtype.kt")
    @Test
    fun testJavaSubtype() {
        runTest("../test-utils/testData/api/javaSubtype.kt")
    }

    @TestMetadata("javaToKotlinMapper.kt")
    @Test
    fun testJavaToKotlinMapper() {
        runTest("../test-utils/testData/api/javaToKotlinMapper.kt")
    }

    @TestMetadata("javaTypes.kt")
    @Test
    fun testJavaTypes() {
        runTest("../test-utils/testData/api/javaTypes.kt")
    }

    @TestMetadata("javaTypes2.kt")
    @Test
    fun testJavaTypes2() {
        runTest("../test-utils/testData/api/javaTypes2.kt")
    }

    @TestMetadata("javaWildcards2.kt")
    @Test
    fun testJavaWildcards2() {
        runTest("../kotlin-analysis-api/testData/javaWildcards2.kt")
    }

    @TestMetadata("jvmName.kt")
    @Test
    fun testJvmName() {
        runTest("../kotlin-analysis-api/testData/jvmName.kt")
    }

    @TestMetadata("lateinitProperties.kt")
    @Test
    fun testLateinitProperties() {
        runTest("../test-utils/testData/api/lateinitProperties.kt")
    }

    @Disabled
    @TestMetadata("libOrigins.kt")
    @Test
    fun testLibOrigins() {
        runTest("../test-utils/testData/api/libOrigins.kt")
    }

    @TestMetadata("locations.kt")
    @Test
    fun testLocations() {
        runTest("../kotlin-analysis-api/testData/locations.kt")
    }

    @TestMetadata("makeNullable.kt")
    @Test
    fun testMakeNullable() {
        runTest("../test-utils/testData/api/makeNullable.kt")
    }

    @TestMetadata("mangledNames.kt")
    @Test
    fun testMangledNames() {
        runTest("../kotlin-analysis-api/testData/mangledNames.kt")
    }

    @TestMetadata("multipleModules.kt")
    @Test
    fun testMultipleModules() {
        runTest("../test-utils/testData/api/multipleModules.kt")
    }

    @TestMetadata("nestedAnnotations.kt")
    @Test
    fun testNestedAnnotations() {
        runTest("../test-utils/testData/api/nestedAnnotations.kt")
    }

    @TestMetadata("nestedClassType.kt")
    @Test
    fun testNestedClassType() {
        runTest("../kotlin-analysis-api/testData/nestedClassType.kt")
    }

    @TestMetadata("nullableTypes.kt")
    @Test
    fun testNullableTypes() {
        runTest("../test-utils/testData/api/nullableTypes.kt")
    }

    @TestMetadata("conflictingOverride.kt")
    @Test
    fun testConflictingOverride() {
        runTest("../test-utils/testData/api/overridee/conflictingOverride.kt")
    }

    @TestMetadata("javaAccessor.kt")
    @Test
    fun testJavaAccessor() {
        runTest("../test-utils/testData/api/overridee/javaAccessor.kt")
    }

    @TestMetadata("javaAnno.kt")
    @Test
    fun testJavaAnno() {
        runTest("../kotlin-analysis-api/testData/overridee/javaAnno.kt")
    }

    @TestMetadata("javaOverrideInSource.kt")
    @Test
    fun testJavaOverrideInSource() {
        runTest("../test-utils/testData/api/overridee/javaOverrideInSource.kt")
    }

    @TestMetadata("noOverride.kt")
    @Test
    fun testNoOverride() {
        runTest("../test-utils/testData/api/overridee/noOverride.kt")
    }

    @TestMetadata("overrideInLib.kt")
    @Test
    fun testOverrideInLib() {
        runTest("../test-utils/testData/api/overridee/overrideInLib.kt")
    }

    @TestMetadata("overrideInSource.kt")
    @Test
    fun testOverrideInSource() {
        runTest("../test-utils/testData/api/overridee/overrideInSource.kt")
    }

    @TestMetadata("overrideOrder.kt")
    @Test
    fun testOverrideOrder() {
        runTest("../kotlin-analysis-api/testData/overridee/overrideOrder.kt")
    }

    @TestMetadata("packageAnnotations.kt")
    @Test
    fun testPackageAnnotation() {
        runTest("../test-utils/testData/api/packageAnnotations.kt")
    }

    @TestMetadata("primaryConstructorOverride.kt")
    @Test
    fun testPrimaryConstructorOverride() {
        runTest("../test-utils/testData/api/overridee/primaryConstructorOverride.kt")
    }

    @TestMetadata("parameterTypes.kt")
    @Test
    fun testParameterTypes() {
        runTest("../test-utils/testData/api/parameterTypes.kt")
    }

    @TestMetadata("parent.kt")
    @Test
    fun testParent() {
        runTest("../kotlin-analysis-api/testData/parent.kt")
    }

    @Disabled
    @TestMetadata("platformDeclaration.kt")
    @Test
    fun testPlatformDeclaration() {
        runTest("../test-utils/testData/api/platformDeclaration.kt")
    }

    @TestMetadata("rawTypes.kt")
    @Test
    fun testRawTypes() {
        runTest("../test-utils/testData/api/rawTypes.kt")
    }

    @DisabledOnOs(OS.WINDOWS)
    @TestMetadata("recordJavaAnnotationTypes.kt")
    @Test
    fun testRecordJavaAnnotationTypes() {
        runTest("../kotlin-analysis-api/testData/recordJavaAnnotationTypes.kt")
    }

    @DisabledOnOs(OS.WINDOWS)
    @TestMetadata("recordJavaAsMemberOf.kt")
    @Test
    fun testRecordJavaAsMemberOf() {
        runTest("../kotlin-analysis-api/testData/recordJavaAsMemberOf.kt")
    }

    @DisabledOnOs(OS.WINDOWS)
    @TestMetadata("recordJavaGetAllMembers.kt")
    @Test
    fun testRecordJavaGetAllMembers() {
        runTest("../kotlin-analysis-api/testData/recordJavaGetAllMembers.kt")
    }

    @DisabledOnOs(OS.WINDOWS)
    @TestMetadata("recordJavaOverrides.kt")
    @Test
    fun testRecordJavaOverrides() {
        runTest("../kotlin-analysis-api/testData/recordJavaOverrides.kt")
    }

    @DisabledOnOs(OS.WINDOWS)
    @TestMetadata("recordJavaResolutions.kt")
    @Test
    fun testRecordJavaResolutions() {
        runTest("../kotlin-analysis-api/testData/recordJavaResolutions.kt")
    }

    @DisabledOnOs(OS.WINDOWS)
    @TestMetadata("recordJavaSupertypes.kt")
    @Test
    fun testRecordJavaSupertypes() {
        runTest("../kotlin-analysis-api/testData/recordJavaSupertypes.kt")
    }

    @TestMetadata("referenceElement.kt")
    @Test
    fun testReferenceElement() {
        runTest("../kotlin-analysis-api/testData/referenceElement.kt")
    }

    @TestMetadata("replaceWithErrorTypeArgs.kt")
    @Test
    fun testReplaceWithErrorTypeArgs() {
        runTest("../kotlin-analysis-api/testData/replaceWithErrorTypeArgs.kt")
    }

    @TestMetadata("resolveJavaType.kt")
    @Test
    fun testResolveJavaType() {
        runTest("../kotlin-analysis-api/testData/resolveJavaType.kt")
    }

    @TestMetadata("sealedClass.kt")
    @Test
    fun testSealedClass() {
        runTest("../test-utils/testData/api/sealedClass.kt")
    }

    @TestMetadata("signatureMapper.kt")
    @Test
    fun testSignatureMapper() {
        runTest("../kotlin-analysis-api/testData/signatureMapper.kt")
    }

    @TestMetadata("superTypes.kt")
    @Test
    fun testSuperTypes() {
        runTest("../test-utils/testData/api/superTypes.kt")
    }

    @TestMetadata("throwList.kt")
    @Test
    fun testThrowList() {
        runTest("../test-utils/testData/api/throwList.kt")
    }

    @TestMetadata("topLevelMembers.kt")
    @Test
    fun testTopLevelMembers() {
        runTest("../test-utils/testData/api/topLevelMembers.kt")
    }

    @TestMetadata("typeAlias.kt")
    @Test
    fun testTypeAlias() {
        runTest("../kotlin-analysis-api/testData/typeAlias.kt")
    }

    @TestMetadata("typeAliasComparison.kt")
    @Test
    fun testTypeAliasComparison() {
        runTest("../test-utils/testData/api/typeAliasComparison.kt")
    }

    @TestMetadata("typeComposure.kt")
    @Test
    fun testTypeComposure() {
        runTest("../test-utils/testData/api/typeComposure.kt")
    }

    @TestMetadata("typeComparison2.kt")
    @Test
    fun testTypeComparison2() {
        runTest("../test-utils/testData/api/typeComparison2.kt")
    }

    @TestMetadata("typeParameterReference.kt")
    @Test
    fun testTypeParameterReference() {
        runTest("../kotlin-analysis-api/testData/typeParameterReference.kt")
    }

    @TestMetadata("typeParameterVariance.kt")
    @Test
    fun testTypeParameterVariance() {
        runTest("../kotlin-analysis-api/testData/typeParameterVariance.kt")
    }

    @TestMetadata("varianceTypeCheck.kt")
    @Test
    fun testVarianceTypeCheck() {
        runTest("../test-utils/testData/api/varianceTypeCheck.kt")
    }

    @TestMetadata("validateTypes.kt")
    @Test
    fun testValidateTypes() {
        runTest("../test-utils/testData/api/validateTypes.kt")
    }

    @TestMetadata("visibilities.kt")
    @Test
    fun testVisibilities() {
        runTest("../kotlin-analysis-api/testData/visibilities.kt")
    }

    @TestMetadata("multipleround.kt")
    @Test
    fun testMultipleround() {
        runTest("../test-utils/testData/api/multipleround.kt")
    }

    @TestMetadata("deferredSymbols.kt")
    @Test
    fun testDeferredSymbols() {
        runTest("../test-utils/testData/api/deferredSymbols.kt")
    }

    @TestMetadata("deferredJavaSymbols.kt")
    @Test
    fun testDeferredJavaSymbols() {
        runTest("../test-utils/testData/api/deferredJavaSymbols.kt")
    }

    @Disabled
    @TestMetadata("deferredTypeRefs.kt")
    @Test
    fun testDeferredTypeRefs() {
        runTest("../test-utils/testData/api/deferredTypeRefs.kt")
    }

    @TestMetadata("exitCode.kt")
    @Test
    fun testExitCode() {
        runTest("../test-utils/testData/api/exitCode.kt")
    }

    @TestMetadata("packageProviderForGenerated.kt")
    @Test
    fun testPackageProviderForGenerated() {
        runTest("../test-utils/testData/api/packageProviderForGenerated.kt")
    }
}
