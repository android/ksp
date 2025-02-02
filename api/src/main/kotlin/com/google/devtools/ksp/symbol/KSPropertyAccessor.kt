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
 * The common base of property getter and setter.
 * Note that annotation use-site targets such as @get: @set: is not copied to accessor's annotations attribute.
 * Use KSAnnotated.findAnnotationFromUseSiteTarget() to ensure annotations from parent is obtained.
 */
sealed interface KSPropertyAccessor : KSDeclarationContainer, KSAnnotated, KSModifierListOwner {
    /**
     * The owner of the property accessor.
     */
    val receiver: KSPropertyDeclaration
}

/**
 * A property setter
 */
interface KSPropertySetter : KSPropertyAccessor {
    val parameter: KSValueParameter
}

/**
 * A property getter
 */
interface KSPropertyGetter : KSPropertyAccessor {
    val returnType: KSTypeReference?
}
