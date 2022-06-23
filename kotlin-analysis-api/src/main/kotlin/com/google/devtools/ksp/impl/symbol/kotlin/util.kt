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

package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.getDocString
import com.google.devtools.ksp.impl.KSPCoreEnvironment
import com.google.devtools.ksp.impl.ResolverAAImpl
import com.google.devtools.ksp.symbol.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.lifetime.KtAlwaysAccessibleLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.psi.KtElement

internal val ktSymbolOriginToOrigin = mapOf(
    KtSymbolOrigin.JAVA to Origin.JAVA,
    KtSymbolOrigin.SOURCE to Origin.KOTLIN,
    KtSymbolOrigin.SAM_CONSTRUCTOR to Origin.SYNTHETIC,
    KtSymbolOrigin.SOURCE_MEMBER_GENERATED to Origin.SYNTHETIC,
    KtSymbolOrigin.DELEGATED to Origin.SYNTHETIC,
    KtSymbolOrigin.PROPERTY_BACKING_FIELD to Origin.KOTLIN,
    KtSymbolOrigin.JAVA_SYNTHETIC_PROPERTY to Origin.SYNTHETIC,
    KtSymbolOrigin.INTERSECTION_OVERRIDE to Origin.KOTLIN,
    // TODO: distinguish between kotlin library and java library.
    KtSymbolOrigin.LIBRARY to Origin.JAVA_LIB
)

internal fun mapAAOrigin(ktSymbolOrigin: KtSymbolOrigin): Origin {
    return ktSymbolOriginToOrigin[ktSymbolOrigin]
        ?: throw IllegalStateException("unhandled origin ${ktSymbolOrigin.name}")
}

internal fun PsiElement?.toLocation(): Location {
    if (this == null) {
        return NonExistLocation
    }
    val file = this.containingFile
    val document = KSPCoreEnvironment.instance.psiDocumentManager.getDocument(file) ?: return NonExistLocation
    return FileLocation(file.virtualFile.path, document.getLineNumber(this.textOffset) + 1)
}

internal fun KtSymbol.toContainingFile(): KSFile? {
    return when (val psi = this.psi) {
        is KtElement -> analyze {
            KSFileImpl.getCached(psi.containingKtFile.getFileSymbol())
        }
        is PsiElement -> KSFileJavaImpl.getCached(psi.containingFile as PsiJavaFile)
        else -> null
    }
}

internal fun KtSymbol.toDocString(): String? = this.psi?.getDocString()

internal inline fun <R> analyze(crossinline action: KtAnalysisSession.() -> R): R {
    return analyze(ResolverAAImpl.ktModule, KtAlwaysAccessibleLifetimeTokenFactory, action)
}

internal fun KtSymbolWithMembers.declarations(): Sequence<KSDeclaration> {
    return analyze {
        this@declarations.getDeclaredMemberScope().getAllSymbols().map {
            when (it) {
                is KtNamedClassOrObjectSymbol -> KSClassDeclarationImpl.getCached(it)
                is KtFunctionLikeSymbol -> KSFunctionDeclarationImpl.getCached(it)
                is KtPropertySymbol -> KSPropertyDeclarationImpl.getCached(it)
                is KtEnumEntrySymbol -> KSClassDeclarationEnumEntryImpl.getCached(it)
                is KtJavaFieldSymbol -> KSPropertyDeclarationJavaImpl.getCached(it)
                else -> throw IllegalStateException()
            }
        }
    }
}

internal fun KtSymbolWithMembers.getAllProperties(): Sequence<KSPropertyDeclaration> {
    return analyze {
        this@getAllProperties.getMemberScope().getCallableSymbols()
            .mapNotNull { callableSymbol ->
                when (callableSymbol) {
                    is KtPropertySymbol -> KSPropertyDeclarationImpl.getCached(callableSymbol)
                    is KtJavaFieldSymbol -> KSPropertyDeclarationJavaImpl.getCached(callableSymbol)
                    else -> null
                }
            }
    }
}

internal fun KtSymbolWithMembers.getAllFunctions(): Sequence<KSFunctionDeclaration> {
    return analyze {
        this@getAllFunctions.getMemberScope().getCallableSymbols()
            .mapNotNull { callableSymbol ->
                // TODO: replace with single safe cast if no more implementations of KSFunctionDeclaration is added.
                when (callableSymbol) {
                    is KtFunctionLikeSymbol -> KSFunctionDeclarationImpl.getCached(callableSymbol)
                    else -> null
                }
            }
    }
}

internal fun KtAnnotated.annotations(): Sequence<KSAnnotation> {
    return this.annotations.asSequence().map { KSAnnotationImpl.getCached(it) }
}

internal fun KtSymbol.getContainingKSSymbol(): KSDeclaration? {
    return analyze {
        when (val containingSymbol = this@getContainingKSSymbol.getContainingSymbol()) {
            is KtNamedClassOrObjectSymbol -> KSClassDeclarationImpl.getCached(containingSymbol)
            is KtFunctionLikeSymbol -> KSFunctionDeclarationImpl.getCached(containingSymbol)
            else -> null
        }
    }
}
