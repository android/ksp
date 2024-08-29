package com.google.devtools.ksp.standalone

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.fir.utils.isSubclassOf
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionCache
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.contains

// TODO: copied from upstream as a workaround, remove after upstream fixes standalone session builder for KSP.
@OptIn(LLFirInternals::class, SymbolInternals::class)
class KspStandaloneDirectInheritorsProvider(private val project: Project) : KotlinDirectInheritorsProvider {
    private val declarationProviderFactory by lazy {
        (KotlinDeclarationProviderFactory.getInstance(project) as? IncrementalKotlinDeclarationProviderFactory)
            ?: error(
                "KotlinStandaloneDirectInheritorsProvider" +
                    "` expects the following declaration provider factory to be" +
                    " registered: `${KotlinStandaloneDeclarationProviderFactory::class.simpleName}`"
            )
    }

    @OptIn(KaPlatformInterface::class)
    override fun getDirectKotlinInheritors(
        ktClass: KtClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean,
    ): Iterable<KtClassOrObject> {
        val classId = ktClass.getClassId() ?: return emptyList()

        val aliases = mutableSetOf(classId.shortClassName)
        calculateAliases(classId.shortClassName, aliases)

        val possibleInheritors = aliases.flatMap { declarationProviderFactory.getDirectInheritorCandidates(it) }

        if (possibleInheritors.isEmpty()) {
            return emptyList()
        }

        // The index provides candidates from an original module, not dangling files. If we resolve the supertypes of a candidate in the
        // context of its session, we will resolve to FIR classes from non-dangling, original modules. If `ktClass` is inside a dangling
        // file, the FIR class for `ktClass` will come from the dangling module. So we'd compare the original FIR class for the supertype
        // with the dangling FIR class for `ktClass`, resulting in a mismatch. To avoid such incompatible comparisons, we need to resolve
        // `ktClass` to the original FIR class.
        //
        // Note that this means we don't support providing inheritors based on the dangling file yet, for example if an inheritor was added
        // or removed only in the dangling file.
        val baseKtModule = when (
            val ktModule = KaModuleProvider.getModule(project, ktClass, useSiteModule = null)
        ) {
            is KaDanglingFileModule -> ktModule.contextModule
            else -> ktModule
        }

        val baseFirClass = ktClass.toFirSymbol(classId, baseKtModule)?.fir as? FirClass ?: return emptyList()
        return possibleInheritors.filter { isValidInheritor(it, baseFirClass, scope, includeLocalInheritors) }
    }

    private fun calculateAliases(aliasedName: Name, aliases: MutableSet<Name>) {
        declarationProviderFactory.getInheritableTypeAliases(aliasedName).forEach { alias ->
            val aliasName = alias.nameAsSafeName
            val isNewAliasName = aliases.add(aliasName)
            if (isNewAliasName) {
                calculateAliases(aliasName, aliases)
            }
        }
    }

    @OptIn(KaImplementationDetail::class)
    private fun isValidInheritor(
        candidate: KtClassOrObject,
        baseFirClass: FirClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean,
    ): Boolean {
        if (!includeLocalInheritors && candidate.isLocal) {
            return false
        }

        if (!scope.contains(candidate)) {
            return false
        }

        val candidateClassId = candidate.getClassId() ?: return false
        val candidateKtModule = KaModuleProvider.getModule(project, candidate, useSiteModule = null)
        val candidateFirSymbol = candidate.toFirSymbol(candidateClassId, candidateKtModule) ?: return false
        val candidateFirClass = candidateFirSymbol.fir as? FirClass ?: return false

        return isSubclassOf(
            candidateFirClass,
            baseFirClass,
            candidateFirClass.moduleData.session,
            allowIndirectSubtyping = false
        )
    }

    private fun KtClassOrObject.toFirSymbol(classId: ClassId, ktModule: KaModule): FirClassLikeSymbol<*>? {
        val session = LLFirSessionCache.getInstance(project).getSession(ktModule, preferBinary = true)
        return session.symbolProvider.getClassLikeSymbolByClassId(classId)
    }
}
