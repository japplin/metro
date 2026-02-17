// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.fir.checkers

import dev.zacsweers.metro.compiler.fir.MetroDiagnostics
import dev.zacsweers.metro.compiler.fir.argumentAsOrNull
import dev.zacsweers.metro.compiler.fir.classIds
import dev.zacsweers.metro.compiler.fir.isResolved
import dev.zacsweers.metro.compiler.fir.resolvedClassId
import dev.zacsweers.metro.compiler.symbols.Symbols
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Validates annotation classes meta-annotated with `@ContributesBindingContainer`.
 *
 * Reports [MetroDiagnostics.AGGREGATION_ERROR] when any of the following are violated:
 * - `template` must be a resolvable class reference to an interface with exactly one type parameter
 * - The annotation must have a `scope: KClass<*>` parameter, or the meta-annotation must specify a
 *   `defaultScope` (where `Nothing::class` is the sentinel for "not set")
 */
internal object ContributesBindingContainerChecker : FirClassChecker(MppCheckerKind.Common) {
  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    val source = declaration.source ?: return
    val session = context.session
    val classIds = session.classIds

    if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return

    val metaAnnotation =
      declaration.annotations.firstOrNull {
        it.isResolved &&
          it.toAnnotationClassIdSafe(session) == classIds.contributesBindingContainerAnnotation
      } ?: return

    val getClassCall =
      metaAnnotation.argumentAsOrNull<FirGetClassCall>(Symbols.Names.template, 0)
    if (getClassCall == null) {
      reporter.reportOn(
        metaAnnotation.source ?: source,
        MetroDiagnostics.AGGREGATION_ERROR,
        "`@ContributesBindingContainer` requires a `template` parameter that is an interface class reference.",
      )
      return
    }

    val templateClassId = getClassCall.resolvedClassId()
    if (templateClassId == null) {
      reporter.reportOn(
        metaAnnotation.source ?: source,
        MetroDiagnostics.AGGREGATION_ERROR,
        "`@ContributesBindingContainer` template could not be resolved.",
      )
      return
    }

    val templateSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(templateClassId) as? FirRegularClassSymbol
    if (templateSymbol == null) {
      reporter.reportOn(
        metaAnnotation.source ?: source,
        MetroDiagnostics.AGGREGATION_ERROR,
        "`@ContributesBindingContainer` template '${templateClassId.asFqNameString()}' could not be found.",
      )
      return
    }

    if (templateSymbol.classKind != ClassKind.INTERFACE) {
      reporter.reportOn(
        metaAnnotation.source ?: source,
        MetroDiagnostics.AGGREGATION_ERROR,
        "`@ContributesBindingContainer` template '${templateClassId.asFqNameString()}' must be an interface, but is a ${templateSymbol.classKind}.",
      )
      return
    }

    if (templateSymbol.typeParameterSymbols.size != 1) {
      reporter.reportOn(
        metaAnnotation.source ?: source,
        MetroDiagnostics.AGGREGATION_ERROR,
        "`@ContributesBindingContainer` template '${templateClassId.asFqNameString()}' must have exactly one type parameter, but has ${templateSymbol.typeParameterSymbols.size}.",
      )
      return
    }

    val defaultScopeArg =
      metaAnnotation.argumentAsOrNull<FirGetClassCall>(Symbols.Names.defaultScope, 1)
    val hasDefaultScope =
      defaultScopeArg != null && defaultScopeArg.resolvedClassId() != StandardClassIds.Nothing

    val primaryCtor = declaration.primaryConstructorIfAny(session)
    val hasScopeParam =
      primaryCtor?.valueParameterSymbols?.any { it.name.identifier == "scope" } ?: false
    if (!hasScopeParam && !hasDefaultScope) {
      reporter.reportOn(
        declaration.source,
        MetroDiagnostics.AGGREGATION_ERROR,
        "Annotation class annotated with `@ContributesBindingContainer` must have a `scope: KClass<*>` parameter or specify a `defaultScope` in `@ContributesBindingContainer`.",
      )
    }
  }
}
