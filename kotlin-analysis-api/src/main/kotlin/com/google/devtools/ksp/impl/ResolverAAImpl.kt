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
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.google.devtools.ksp.impl

import com.google.devtools.ksp.BinaryClassInfoCache
import com.google.devtools.ksp.JVM_DEFAULT_ANNOTATION_FQN
import com.google.devtools.ksp.JVM_DEFAULT_WITHOUT_COMPATIBILITY_ANNOTATION_FQN
import com.google.devtools.ksp.JVM_STATIC_ANNOTATION_FQN
import com.google.devtools.ksp.JVM_STRICTFP_ANNOTATION_FQN
import com.google.devtools.ksp.JVM_SYNCHRONIZED_ANNOTATION_FQN
import com.google.devtools.ksp.JVM_TRANSIENT_ANNOTATION_FQN
import com.google.devtools.ksp.JVM_VOLATILE_ANNOTATION_FQN
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.extractThrowsAnnotation
import com.google.devtools.ksp.extractThrowsFromClassFile
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.hasAnnotation
import com.google.devtools.ksp.impl.symbol.kotlin.KSClassDeclarationEnumEntryImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSClassDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSFunctionDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSPropertyAccessorImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSPropertyDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSPropertyDeclarationJavaImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSTypeAliasImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSTypeArgumentLiteImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSTypeImpl
import com.google.devtools.ksp.impl.symbol.kotlin.analyze
import com.google.devtools.ksp.impl.symbol.kotlin.findParentOfType
import com.google.devtools.ksp.impl.symbol.kotlin.toKtClassSymbol
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.javaModifiers
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.impl.KSNameImpl
import com.google.devtools.ksp.processing.impl.KSTypeReferenceSyntheticImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.toKSName
import com.google.devtools.ksp.visitor.CollectAnnotatedSymbolsVisitor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.file.impl.JavaFileManager
import org.jetbrains.kotlin.analysis.api.fir.types.KtFirType
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.fir.types.isRaw
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.Opcodes

@OptIn(KspExperimental::class)
class ResolverAAImpl(
    val allKSFiles: List<KSFile>,
    val newKSFiles: List<KSFile>,
    val kspConfig: KSPJvmConfig,
    val project: Project
) : Resolver {
    companion object {
        lateinit var instance: ResolverAAImpl
        lateinit var ktModule: KtModule
    }

    val javaFileManager = project.getService(JavaFileManager::class.java) as KotlinCliJavaFileManagerImpl

    // TODO: fix in upstream for builtin types.
    override val builtIns: KSBuiltIns by lazy {
        val builtIns = analyze { analysisSession.builtinTypes }
        object : KSBuiltIns {
            override val anyType: KSType by lazy { KSTypeImpl.getCached(builtIns.ANY) }
            override val nothingType: KSType by lazy { KSTypeImpl.getCached(builtIns.NOTHING) }
            override val unitType: KSType by lazy { KSTypeImpl.getCached(builtIns.UNIT) }
            override val numberType: KSType by lazy { TODO() }
            override val byteType: KSType by lazy { KSTypeImpl.getCached(builtIns.BYTE) }
            override val shortType: KSType by lazy { KSTypeImpl.getCached(builtIns.SHORT) }
            override val intType: KSType by lazy { KSTypeImpl.getCached(builtIns.INT) }
            override val longType: KSType by lazy { KSTypeImpl.getCached(builtIns.LONG) }
            override val floatType: KSType by lazy { KSTypeImpl.getCached(builtIns.FLOAT) }
            override val doubleType: KSType by lazy { KSTypeImpl.getCached(builtIns.DOUBLE) }
            override val charType: KSType by lazy { KSTypeImpl.getCached(builtIns.CHAR) }
            override val booleanType: KSType by lazy { KSTypeImpl.getCached(builtIns.BOOLEAN) }
            override val stringType: KSType by lazy { KSTypeImpl.getCached(builtIns.STRING) }
            override val iterableType: KSType by lazy { TODO() }
            override val annotationType: KSType by lazy { TODO() }
            override val arrayType: KSType by lazy { TODO() }
        }
    }

    override fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference {
        return KSTypeReferenceSyntheticImpl.getCached(type, null)
    }

    override fun effectiveJavaModifiers(declaration: KSDeclaration): Set<Modifier> {
        val modifiers = HashSet<Modifier>(declaration.modifiers.filter { it in javaModifiers })

        // This is only needed by sources.
        // PUBLIC, PRIVATE, PROTECTED are already handled in descriptor based impls.
        fun addVisibilityModifiers() {
            when {
                declaration.isPublic() -> modifiers.add(Modifier.PUBLIC)
                declaration.isPrivate() -> modifiers.add(Modifier.PRIVATE)
                declaration.isProtected() -> modifiers.add(Modifier.PROTECTED)
            }
        }

        when (declaration.origin) {
            Origin.JAVA -> {
                addVisibilityModifiers()
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE)
                    modifiers.add(Modifier.ABSTRACT)
            }
            Origin.KOTLIN -> {
                addVisibilityModifiers()
                if (!declaration.isOpen())
                    modifiers.add(Modifier.FINAL)
                (declaration as? KSClassDeclarationImpl)?.let {
                    analyze {
                        if (
                            it.ktClassOrObjectSymbol.getStaticMemberScope()
                                .getAllSymbols().contains(declaration.ktDeclarationSymbol)
                        )
                            modifiers.add(Modifier.JAVA_STATIC)
                    }
                }

                if (declaration.hasAnnotation(JVM_DEFAULT_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_DEFAULT)
                if (declaration.hasAnnotation(JVM_DEFAULT_WITHOUT_COMPATIBILITY_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_DEFAULT)
                if (declaration.hasAnnotation(JVM_STRICTFP_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_STRICT)
                if (declaration.hasAnnotation(JVM_SYNCHRONIZED_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_SYNCHRONIZED)
                if (declaration.hasAnnotation(JVM_TRANSIENT_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_TRANSIENT)
                if (declaration.hasAnnotation(JVM_VOLATILE_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_VOLATILE)
                when (declaration) {
                    is KSClassDeclaration -> {
                        if (declaration.isCompanionObject)
                            modifiers.add(Modifier.JAVA_STATIC)
                        if (declaration.classKind == ClassKind.INTERFACE)
                            modifiers.add(Modifier.ABSTRACT)
                    }
                    is KSPropertyDeclaration -> {
                        if (declaration.isAbstract())
                            modifiers.add(Modifier.ABSTRACT)
                    }
                    is KSFunctionDeclaration -> {
                        if (declaration.isAbstract)
                            modifiers.add(Modifier.ABSTRACT)
                    }
                }
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                if (declaration.hasAnnotation(JVM_STATIC_ANNOTATION_FQN)) {
                    modifiers.add(Modifier.JAVA_STATIC)
                }
                addVisibilityModifiers()
                when (declaration) {
                    is KSPropertyDeclaration -> {
                        if (declaration.jvmAccessFlag and Opcodes.ACC_TRANSIENT != 0)
                            modifiers.add(Modifier.JAVA_TRANSIENT)
                        if (declaration.jvmAccessFlag and Opcodes.ACC_VOLATILE != 0)
                            modifiers.add(Modifier.JAVA_VOLATILE)
                    }
                    is KSFunctionDeclaration -> {
                        if (declaration.jvmAccessFlag and Opcodes.ACC_STRICT != 0)
                            modifiers.add(Modifier.JAVA_STRICT)
                        if (declaration.jvmAccessFlag and Opcodes.ACC_SYNCHRONIZED != 0)
                            modifiers.add(Modifier.JAVA_SYNCHRONIZED)
                    }
                }
            }
            else -> Unit
        }
        return modifiers
    }

    internal val KSPropertyDeclaration.jvmAccessFlag: Int
        get() = when (origin) {
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val fileManager = instance.javaFileManager
                val parentClass = this.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classIdIfNonLocal!!
                val virtualFileContent = analyze {
                    (fileManager.findClass(classId, analysisScope) as JavaClassImpl).virtualFile!!.contentsToByteArray()
                }
                BinaryClassInfoCache.getCached(classId, virtualFileContent)
                    .fieldAccFlags[this.simpleName.asString()] ?: 0
            }
            else -> throw IllegalStateException("this function expects only KOTLIN_LIB or JAVA_LIB")
        }

    internal val KSFunctionDeclaration.jvmAccessFlag: Int
        get() = when (origin) {
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val jvmDesc = mapToJvmSignatureInternal(this)
                val fileManager = instance.javaFileManager
                val parentClass = this.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classIdIfNonLocal!!
                val virtualFileContent = analyze {
                    (fileManager.findClass(classId, analysisScope) as JavaClassImpl).virtualFile!!.contentsToByteArray()
                }
                BinaryClassInfoCache.getCached(classId, virtualFileContent)
                    .methodAccFlags[this.simpleName.asString() + jvmDesc] ?: 0
            }
            else -> throw IllegalStateException("this function expects only KOTLIN_LIB or JAVA_LIB")
        }

    override fun getAllFiles(): Sequence<KSFile> {
        return allKSFiles.asSequence()
    }

    override fun getClassDeclarationByName(name: KSName): KSClassDeclaration? {
        fun findClass(name: KSName): KtSymbolWithMembers? {
            if (name.asString() == "") {
                return null
            }
            val parent = name.getQualifier()
            val simpleName = name.getShortName()
            val classId = ClassId(FqName(parent), Name.identifier(simpleName))
            analyze {
                classId.toKtClassSymbol()
            }?.let { return it }
            return (findClass(KSNameImpl.getCached(name.getQualifier())) as? KtNamedClassOrObjectSymbol)?.let {
                analyze {
                    (
                        it.getStaticMemberScope().getClassifierSymbols { it.asString() == simpleName }.singleOrNull()
                            ?: it.getMemberScope().getClassifierSymbols { it.asString() == simpleName }.singleOrNull()
                        ) as? KtNamedClassOrObjectSymbol
                        ?: it.getStaticMemberScope().getCallableSymbols { it.asString() == simpleName }.singleOrNull()
                            as? KtEnumEntrySymbol
                }
            }
        }
        return findClass(name)?.let {
            when (it) {
                is KtNamedClassOrObjectSymbol -> KSClassDeclarationImpl.getCached(it)
                is KtEnumEntrySymbol -> KSClassDeclarationEnumEntryImpl.getCached(it)
                else -> throw IllegalStateException()
            }
        }
    }

    @KspExperimental
    override fun getDeclarationsFromPackage(packageName: String): Sequence<KSDeclaration> {
        return analyze {
            val packageNames = packageName.split(".")
            var packages = listOf(analysisSession.ROOT_PACKAGE_SYMBOL)
            for (curName in packageNames) {
                packages = packages
                    .flatMap { it.getPackageScope().getPackageSymbols { it.asString() == curName } }
                    .distinct()
            }
            // Above steps forfeited root package, adding it back.
            packages += analysisSession.ROOT_PACKAGE_SYMBOL
            packages.flatMap {
                it.getPackageScope().getAllSymbols().distinct().mapNotNull { symbol ->
                    when (symbol) {
                        is KtNamedClassOrObjectSymbol -> KSClassDeclarationImpl.getCached(symbol)
                        is KtFunctionLikeSymbol -> KSFunctionDeclarationImpl.getCached(symbol)
                        is KtPropertySymbol -> KSPropertyDeclarationImpl.getCached(symbol)
                        is KtTypeAliasSymbol -> KSTypeAliasImpl.getCached(symbol)
                        is KtJavaFieldSymbol -> KSPropertyDeclarationJavaImpl.getCached(symbol)
                        else -> null
                    }
                }
            }.asSequence()
        }
    }

    override fun getDeclarationsInSourceOrder(container: KSDeclarationContainer): Sequence<KSDeclaration> {
        TODO("Not yet implemented")
    }

    override fun getFunctionDeclarationsByName(
        name: KSName,
        includeTopLevel: Boolean
    ): Sequence<KSFunctionDeclaration> {
        TODO("Not yet implemented")
    }

    override fun getJavaWildcard(reference: KSTypeReference): KSTypeReference {
        TODO("Not yet implemented")
    }

    override fun getJvmCheckedException(accessor: KSPropertyAccessor): Sequence<KSType> {
        return when (accessor.origin) {
            Origin.KOTLIN -> {
                extractThrowsAnnotation(accessor)
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val fileManager = javaFileManager
                val parentClass = accessor.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classIdIfNonLocal!!
                val virtualFileContent = analyze {
                    (fileManager.findClass(classId, analysisScope) as JavaClassImpl).virtualFile!!.contentsToByteArray()
                }
                val jvmDesc = this.mapToJvmSignatureInternal(accessor)
                if (virtualFileContent == null) {
                    return emptySequence()
                }
                extractThrowsFromClassFile(
                    virtualFileContent,
                    jvmDesc,
                    (if (accessor is KSPropertyGetter) "get" else "set") +
                        accessor.receiver.simpleName.asString().capitalize()
                )
            }
            else -> emptySequence()
        }
    }

    override fun getJvmCheckedException(function: KSFunctionDeclaration): Sequence<KSType> {
        return when (function.origin) {
            Origin.JAVA -> {
                val psi = (function as KSFunctionDeclarationImpl).ktFunctionSymbol.psi as PsiMethod
                psi.throwsList.referencedTypes.asSequence().mapNotNull {
                    analyze {
                        it.resolve()?.qualifiedName?.let { getClassDeclarationByName(it)?.asStarProjectedType() }
                    }
                }
            }
            Origin.KOTLIN -> {
                extractThrowsAnnotation(function)
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val fileManager = javaFileManager
                val parentClass = function.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classIdIfNonLocal!!
                val virtualFileContent = analyze {
                    (fileManager.findClass(classId, analysisScope) as JavaClassImpl).virtualFile!!.contentsToByteArray()
                }
                val jvmDesc = this.mapToJvmSignature(function)
                if (virtualFileContent == null) {
                    return emptySequence()
                }
                extractThrowsFromClassFile(virtualFileContent, jvmDesc, function.simpleName.asString())
            }
            else -> emptySequence()
        }
    }

    override fun getJvmName(accessor: KSPropertyAccessor): String? {
        TODO("Not yet implemented")
    }

    override fun getJvmName(declaration: KSFunctionDeclaration): String? {
        TODO("Not yet implemented")
    }

    override fun getKSNameFromString(name: String): KSName {
        return KSNameImpl.getCached(name)
    }

    // FIXME: correct implementation after incremental is ready.
    override fun getNewFiles(): Sequence<KSFile> {
        return newKSFiles.asSequence()
    }

    override fun getOwnerJvmClassName(declaration: KSFunctionDeclaration): String? {
        TODO("Not yet implemented")
    }

    override fun getOwnerJvmClassName(declaration: KSPropertyDeclaration): String? {
        TODO("Not yet implemented")
    }

    override fun getPropertyDeclarationByName(name: KSName, includeTopLevel: Boolean): KSPropertyDeclaration? {
        TODO("Not yet implemented")
    }

    // TODO: optimization and type alias handling.
    override fun getSymbolsWithAnnotation(annotationName: String, inDepth: Boolean): Sequence<KSAnnotated> {
        val visitor = CollectAnnotatedSymbolsVisitor(inDepth)

        for (file in getNewFiles()) {
            file.accept(visitor, Unit)
        }

        return visitor.symbols.asSequence().filter {
            it.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
            }
        }
    }

    override fun getTypeArgument(typeRef: KSTypeReference, variance: Variance): KSTypeArgument {
        return KSTypeArgumentLiteImpl.getCached(typeRef, variance)
    }

    override fun isJavaRawType(type: KSType): Boolean {
        return type is KSTypeImpl && (type.type as KtFirType).coneType.isRaw()
    }

    @KspExperimental
    override fun getPackageAnnotations(packageName: String): Sequence<KSAnnotation> {
        TODO("Not yet implemented")
    }

    @KspExperimental
    override fun getPackagesWithAnnotation(annotationName: String): Sequence<String> {
        TODO("Not yet implemented")
    }

    @KspExperimental
    override fun mapJavaNameToKotlin(javaName: KSName): KSName? {
        return JavaToKotlinClassMap.mapJavaToKotlin(FqName(javaName.asString()))?.toKSName()
    }

    @KspExperimental
    override fun mapKotlinNameToJava(kotlinName: KSName): KSName? {
        return JavaToKotlinClassMap.mapKotlinToJava(FqNameUnsafe(kotlinName.asString()))?.toKSName()
    }

    @KspExperimental
    override fun mapToJvmSignature(declaration: KSDeclaration): String? {
        return mapToJvmSignatureInternal(declaration)
    }

    override fun overrides(overrider: KSDeclaration, overridee: KSDeclaration): Boolean {
        val overriderSymbol = when (overrider) {
            is KSFunctionDeclarationImpl -> if (overrider.ktFunctionSymbol is KtPropertyAccessorSymbol) {
                (overrider.parent as KSPropertyDeclarationImpl).ktPropertySymbol
            } else {
                overrider.ktFunctionSymbol
            }
            is KSPropertyDeclarationImpl -> overrider.ktPropertySymbol
            else -> return false
        }
        val overrideeSymbol = when (overridee) {
            is KSFunctionDeclarationImpl -> overridee.ktFunctionSymbol
            is KSPropertyDeclarationImpl -> overridee.ktPropertySymbol
            else -> return false
        }
        return analyze {
            overriderSymbol.getAllOverriddenSymbols().contains(overrideeSymbol) ||
                overriderSymbol.getIntersectionOverriddenSymbols().contains(overrideeSymbol)
        }
    }

    override fun overrides(
        overrider: KSDeclaration,
        overridee: KSDeclaration,
        containingClass: KSClassDeclaration
    ): Boolean {
        return when (overrider) {
            is KSPropertyDeclaration -> containingClass.getAllProperties().singleOrNull {
                it.simpleName.asString() == overrider.simpleName.asString()
            }?.let { overrides(it, overridee) } ?: false
            is KSFunctionDeclaration -> {
                val candidates = containingClass.getAllFunctions().filter {
                    it.simpleName.asString() == overridee.simpleName.asString()
                }
                if (overrider.simpleName.asString().startsWith("get") ||
                    overrider.simpleName.asString().startsWith("set")
                ) {
                    candidates.plus(
                        containingClass.getAllProperties().filter {
                            val overriderName = overrider.simpleName.asString().substring(3)
                                .replaceFirstChar { it.lowercase() }
                            it.simpleName.asString() == overriderName ||
                                it.simpleName.asString().replaceFirstChar { it.lowercase() } == overriderName
                        }
                    ).any { overrides(it, overridee) }
                } else {
                    candidates.any { overrides(it, overridee) }
                }
            }
            else -> false
        }
    }

    internal fun mapToJvmSignatureInternal(ksAnnotated: KSAnnotated): String? {
        fun KtType.toSignature(): String {
            return analyze {
                this@toSignature.mapTypeToJvmType().descriptor.let {
                    when (it) {
                        "Ljava.lang.Void;" -> "Ljava/lang/Void;"
                        "Lkotlin/Unit;" -> "V"
                        else -> it
                    }
                }
            }
        }

        fun KSType.toSignature(): String {
            return if (this is KSTypeImpl) {
                analyze {
                    this@toSignature.type.toSignature()
                }
            } else {
                "<ERROR>"
            }
        }

        return when (ksAnnotated) {
            is KSClassDeclaration -> analyze {
                (ksAnnotated.asStarProjectedType() as KSTypeImpl).type.mapTypeToJvmType().descriptor
            }
            is KSFunctionDeclarationImpl -> {
                analyze {
                    buildString {
                        append("(")
                        ksAnnotated.parameters.forEach { append(it.type.resolve().toSignature()) }
                        append(")")
                        if (ksAnnotated.isConstructor()) {
                            append("V")
                        } else {
                            append(ksAnnotated.returnType!!.resolve().toSignature())
                        }
                    }
                }
            }
            is KSPropertyDeclaration -> {
                analyze {
                    ksAnnotated.type.resolve().toSignature()
                }
            }
            is KSPropertyAccessorImpl -> {
                analyze {
                    buildString {
                        append("(")
                        ksAnnotated.ktPropertyAccessorSymbol.valueParameters.forEach {
                            append(it.returnType.toSignature())
                        }
                        append(")")
                        append(ksAnnotated.ktPropertyAccessorSymbol.returnType.toSignature())
                    }
                }
            }
            else -> null
        }
    }
}
