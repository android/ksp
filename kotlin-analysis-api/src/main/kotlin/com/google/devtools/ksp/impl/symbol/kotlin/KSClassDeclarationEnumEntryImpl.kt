package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.impl.recordLookup
import com.google.devtools.ksp.impl.recordLookupForGetAllFunctions
import com.google.devtools.ksp.impl.recordLookupForGetAllProperties
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSExpectActual
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol

class KSClassDeclarationEnumEntryImpl private constructor(private val ktEnumEntrySymbol: KaEnumEntrySymbol) :
    KSClassDeclaration,
    AbstractKSDeclarationImpl(ktEnumEntrySymbol),
    KSExpectActual by KSExpectActualImpl(ktEnumEntrySymbol) {
    companion object : KSObjectCache<KaEnumEntrySymbol, KSClassDeclarationEnumEntryImpl>() {
        fun getCached(ktEnumEntrySymbol: KaEnumEntrySymbol) =
            cache.getOrPut(ktEnumEntrySymbol) { KSClassDeclarationEnumEntryImpl(ktEnumEntrySymbol) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val qualifiedName: KSName? by lazy {
        KSNameImpl.getCached("${this.parentDeclaration!!.qualifiedName!!.asString()}.${simpleName.asString()}")
    }

    override val classKind: ClassKind = ClassKind.ENUM_ENTRY

    override val primaryConstructor: KSFunctionDeclaration? = null

    override val superTypes: Sequence<KSTypeReference> = emptySequence()

    override val isCompanionObject: Boolean = false

    override fun getSealedSubclasses(): Sequence<KSClassDeclaration> {
        return emptySequence()
    }

    override fun getAllFunctions(): Sequence<KSFunctionDeclaration> {
        analyze {
            ktEnumEntrySymbol.returnType.directSupertypes.forEach {
                recordLookup(it, this@KSClassDeclarationEnumEntryImpl)
            }
            recordLookupForGetAllFunctions(ktEnumEntrySymbol.returnType.directSupertypes.toList())
        }
        return ktEnumEntrySymbol.enumEntryInitializer?.declarations()?.filterIsInstance<KSFunctionDeclaration>()
            ?: emptySequence()
    }

    override fun getAllProperties(): Sequence<KSPropertyDeclaration> {
        analyze {
            ktEnumEntrySymbol.returnType.directSupertypes.forEach {
                recordLookup(it, this@KSClassDeclarationEnumEntryImpl)
            }
            recordLookupForGetAllProperties(ktEnumEntrySymbol.returnType.directSupertypes.toList())
        }
        return ktEnumEntrySymbol.enumEntryInitializer?.declarations()?.filterIsInstance<KSPropertyDeclaration>()
            ?: emptySequence()
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        return KSTypeImpl.getCached(ktEnumEntrySymbol.returnType).replace(typeArguments)
    }

    override fun asStarProjectedType(): KSType {
        return KSTypeImpl.getCached(ktEnumEntrySymbol.returnType).starProjection()
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val packageName: KSName by lazy {
        ktEnumEntrySymbol.toContainingFile()!!.packageName
    }

    override val parentDeclaration: KSDeclaration? by lazy {
        analyze {
            (
                ktEnumEntrySymbol.containingSymbol
                    as? KaNamedClassSymbol
                )?.let { KSClassDeclarationImpl.getCached(it) }
        }
    }

    override val containingFile: KSFile? by lazy {
        ktEnumEntrySymbol.toContainingFile()
    }

    override val location: Location by lazy {
        ktEnumEntrySymbol.psi.toLocation()
    }

    override val parent: KSNode by lazy {
        analyze {
            (ktEnumEntrySymbol.containingSymbol as KaNamedClassSymbol)
                .let { KSClassDeclarationImpl.getCached(it) }
        }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }

    override val declarations: Sequence<KSDeclaration> by lazy {
        // TODO: fix after .getDeclaredMemberScope() works for enum entry with no initializer.
        emptySequence()
    }

    override fun toString(): String {
        return "$parent.${simpleName.asString()}"
    }

    override fun defer(): Restorable? {
        return ktEnumEntrySymbol.defer(Companion::getCached)
    }
}
