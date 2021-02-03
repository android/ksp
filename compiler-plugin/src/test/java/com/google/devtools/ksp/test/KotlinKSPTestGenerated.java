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


package com.google.devtools.ksp.test;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.test.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("all")
@TestMetadata("testData/api")
@TestDataPath("$PROJECT_ROOT/compiler-plugin")
@RunWith(JUnit3RunnerWithInners.class)
public class KotlinKSPTestGenerated extends AbstractKotlinKSPTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("allFunctions.kt")
    public void testAllFunctions() throws Exception {
        runTest("testData/api/allFunctions.kt");
    }

    @TestMetadata("annotationInDependencies.kt")
    public void testAnnotationsInDependencies() throws Exception {
        runTest("testData/api/annotationInDependencies.kt");
    }

    @TestMetadata("annotationValue.kt")
    public void testAnnotationValue() throws Exception {
        runTest("testData/api/annotationValue.kt");
    }

    @TestMetadata("annotationWithArrayValue.kt")
    public void testAnnotationWithArrayValue() throws Exception {
        runTest("testData/api/annotationWithArrayValue.kt");
    }

    @TestMetadata("annotationWithDefault.kt")
    public void testAnnotationWithDefault() throws Exception {
        runTest("testData/api/annotationWithDefault.kt");
    }

    @TestMetadata("asMemberOf.kt")
    public void testAsMemberOf() throws Exception {
        runTest("testData/api/asMemberOf.kt");
    }

    @TestMetadata("builtInTypes.kt")
    public void testBuiltInTypes() throws Exception {
        runTest("testData/api/builtInTypes.kt");
    }

    @TestMetadata("checkOverride.kt")
    public void testCheckOverride() throws Exception {
        runTest("testData/api/checkOverride.kt");
    }

    @TestMetadata("classKinds.kt")
    public void testClassKinds() throws Exception {
        runTest("testData/api/classKinds.kt");
    }

    @TestMetadata("companion.kt")
    public void testCompanion() throws Exception {
        runTest("testData/api/companion.kt");
    }

    @TestMetadata("constructorDeclarations.kt")
    public void testConstructorDeclarations() throws Exception {
        runTest("testData/api/constructorDeclarations.kt");
    }

    @TestMetadata("crossModuleTypeAlias.kt")
    public void testCrossModuleTypeAlias() throws Exception {
        runTest("testData/api/crossModuleTypeAlias.kt");
    }

    @TestMetadata("declarationInconsistency.kt")
    public void testDeclarationInconsistency() throws Exception {
        runTest("testData/api/declarationInconsistency.kt");
    }

    @TestMetadata("declarationPackageName.kt")
    public void testDeclarationPackageName() throws Exception {
        runTest("testData/api/declarationPackageName.kt");
    }

    @TestMetadata("declarationUtil.kt")
    public void testDeclarationUtil() throws Exception {
        runTest("testData/api/declarationUtil.kt");
    }

    @TestMetadata("declared.kt")
    public void testDeclared() throws Exception {
        runTest("testData/api/declared.kt");
    }

    @TestMetadata("errorTypes.kt")
    public void testErrorTypes() throws Exception {
        runTest("testData/api/errorTypes.kt");
    }

    @TestMetadata("functionTypeAlias.kt")
    public void testFunctionTypeAlias() throws Exception {
        runTest("testData/api/functionTypeAlias.kt");
    }

    @TestMetadata("getSymbolsFromAnnotation.kt")
    public void testGetSymbolsFromAnnotation() throws Exception {
        runTest("testData/api/getSymbolsFromAnnotation.kt");
    }

    @TestMetadata("hello.kt")
    public void testHello() throws Exception {
        runTest("testData/api/hello.kt");
    }

    @TestMetadata("implicitElements.kt")
    public void testImplicitElements() throws Exception {
        runTest("testData/api/implicitElements.kt");
    }

    @TestMetadata("implicitPropertyAccessors.kt")
    public void testImplicitPropertyAccessors() throws Exception {
        runTest("testData/api/implicitPropertyAccessors.kt");
    }

    @TestMetadata("interfaceWithDefault.kt")
    public void testInterfaceWithDefault() throws Exception {
        runTest("testData/api/interfaceWithDefault.kt");
    }

    @TestMetadata("javaModifiers.kt")
    public void testJavaModifiers() throws Exception {
        runTest("testData/api/javaModifiers.kt");
    }

    @TestMetadata("javaTypes.kt")
    public void testJavaTypes() throws Exception {
        runTest("testData/api/javaTypes.kt");
    }

    @TestMetadata("javaTypes2.kt")
    public void testJavaTypes2() throws Exception {
        runTest("testData/api/javaTypes2.kt");
    }

    @TestMetadata("makeNullable.kt")
    public void testMakeNullable() throws Exception {
        runTest("testData/api/makeNullable.kt");
    }

    @TestMetadata("mangledNames.kt")
    public void testMangledNames() throws Exception {
        runTest("testData/api/mangledNames.kt");
    }

    @TestMetadata(("multipleModules.kt"))
    public void testMultipleModules() throws Exception {
        runTest("testData/api/multipleModules.kt");
    }

    @TestMetadata(("overridee.kt"))
    public void testOverridee() throws Exception {
        runTest("testData/api/overridee.kt");
    }

    @TestMetadata("parameterTypes.kt")
    public void testParameterTypes() throws Exception {
        runTest("testData/api/parameterTypes.kt");
    }

    @TestMetadata("platformDeclaration.kt")
    public void testPlatformDeclaration() throws Exception {
        runTest("testData/api/platformDeclaration.kt");
    }

    @TestMetadata("recordJavaAnnotationTypes.kt")
    public void testRecordJavaAnnotationTypes() throws Exception {
        runTest("testData/api/recordJavaAnnotationTypes.kt");
    }

    @TestMetadata("recordJavaAsMemberOf.kt")
    public void testRecordJavaAsMemberOf() throws Exception {
        runTest("testData/api/recordJavaAsMemberOf.kt");
    }

    @TestMetadata("recordJavaGetAllMembers.kt")
    public void testRecordJavaGetAllMembers() throws Exception {
        runTest("testData/api/recordJavaGetAllMembers.kt");
    }

    @TestMetadata("recordJavaOverrides.kt")
    public void testRecordJavaOverrides() throws Exception {
        runTest("testData/api/recordJavaOverrides.kt");
    }

    @TestMetadata("recordJavaSupertypes.kt")
    public void testRecordJavaSupertypes() throws Exception {
        runTest("testData/api/recordJavaSupertypes.kt");
    }

    @TestMetadata("referenceElement.kt")
    public void testReferenceElement() throws Exception {
        runTest("testData/api/referenceElement.kt");
    }

    @TestMetadata("resolveJavaType.kt")
    public void testResolveJavaType() throws Exception {
        runTest("testData/api/resolveJavaType.kt");
    }

    @TestMetadata("signatureMapper.kt")
    public void testSignatureMapper() throws Exception {
        runTest("testData/api/signatureMapper.kt");
    }

    @TestMetadata("typeAlias.kt")
    public void testTypeAlias() throws Exception {
        runTest("testData/api/typeAlias.kt");
    }

    @TestMetadata("typeAliasComparison.kt")
    public void testTypeAliasComparison() throws Exception {
        runTest("testData/api/typeAliasComparison.kt");
    }

    @TestMetadata("typeComposure.kt")
    public void testTypeComposure() throws Exception {
        runTest("testData/api/typeComposure.kt");
    }

    @TestMetadata("typeParameterReference.kt")
    public void testTypeParameterReference() throws Exception {
        runTest("testData/api/typeParameterReference.kt");
    }

    @TestMetadata("varianceTypeCheck.kt")
    public void testVarianceTypeCheck() throws Exception {
        runTest("testData/api/varianceTypeCheck.kt");
    }

    @TestMetadata("validateTypes.kt")
    public void testValidateTypes() throws Exception {
        runTest("testData/api/validateTypes.kt");
    }

    @TestMetadata("visibilities.kt")
    public void testVisibilities() throws Exception {
        runTest("testData/api/visibilities.kt");
    }

    @Override
    protected @NotNull List<KspTestFile> createTestFilesFromFile(@NotNull File file, @NotNull String expectedText) {
        return TestFiles.createTestFiles(file.getName(), expectedText, new TestFiles.TestFileFactory<TestModule, KspTestFile>() {
            @Override
            public KspTestFile createFile(@Nullable TestModule module, @NotNull String fileName, @NotNull String text, @NotNull Directives directives) {
                return new KspTestFile(fileName, text, directives, module);
            }

            @Override
            public TestModule createModule(@NotNull String name, @NotNull List<String> dependencies, @NotNull List<String> friends) {
                return new TestModule(name, dependencies, friends);
            }
        });
    }
}
