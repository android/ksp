package com.google.devtools.ksp.impl.symbol.kotlin.synthetic

import com.google.devtools.ksp.impl.ResolverAAImpl
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplicationWithArgumentsInfo
import org.jetbrains.kotlin.analysis.api.lifetime.KtAlwaysAccessibleLifetimeToken
import org.jetbrains.kotlin.name.ClassId

fun getExtensionFunctionTypeAnnotation(index: Int) = KtAnnotationApplicationWithArgumentsInfo(
    ClassId.fromString(ExtensionFunctionType::class.qualifiedName!!),
    null,
    null,
    emptyList(),
    index,
    null,
    KtAlwaysAccessibleLifetimeToken(ResolverAAImpl.ktModule.project!!)
)
