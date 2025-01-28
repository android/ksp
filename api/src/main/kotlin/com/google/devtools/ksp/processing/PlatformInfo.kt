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

package com.google.devtools.ksp.processing

/**
 * Platform specific information
 */
sealed interface PlatformInfo {
    val platformName: String
}

/*
 * Platform information for JVM
 */
interface JvmPlatformInfo : PlatformInfo {
    /**
     * JVM target version. Correspond to `-jvm-target` to Kotlin compiler
     */
    val jvmTarget: String

    /**
     * JVM default mode. Correspond to `-Xjvm-default' to Kotlin compiler
     */
    val jvmDefaultMode: String
}

/**
 * Platform information for JS
 */
interface JsPlatformInfo : PlatformInfo

/**
 * Platform information for native platforms
 */
interface NativePlatformInfo : PlatformInfo {
    val targetName: String
}

/**
 * Unknown platform to KSP
 */
interface UnknownPlatformInfo : PlatformInfo
