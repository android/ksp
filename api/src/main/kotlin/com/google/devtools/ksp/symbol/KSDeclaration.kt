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
package com.google.devtools.ksp.symbol

/**
 * A declaration, can be function declaration, clsss declaration and property declaration, or a type alias.
 */
interface KSDeclaration : KSModifierListOwner, KSAnnotated, KSExpectActual {
    /**
     * Simple name of this declaration, usually the name identifier at the declaration site.
     */
    val simpleName: KSName

    /**
     * Fully qualified name of this declaration, might not exist for some declarations like local declarations.
     */
    val qualifiedName: KSName?

    /**
     * List of [type parameters][KSTypeParameter] of the declaration.
     */
    val typeParameters: List<KSTypeParameter>

    /**
     * The name of the package at which this declaration is declared.
     */
    val packageName: KSName

    /**
     * Parent declaration of this declaration, i.e. the declaration that directly contains this declaration.
     * File is not a declaration, so this property will be null for top level declarations.
     */
    val parentDeclaration: KSDeclaration?

    /**
     * The doc string enclosed by \/\*\* and \*\/
     */
    val docString: String?
}
