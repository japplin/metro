// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.fir.generators

import dev.zacsweers.metro.compiler.MetroAnnotations
import dev.zacsweers.metro.compiler.asName
import dev.zacsweers.metro.compiler.capitalizeUS
import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.metro.compiler.decapitalizeUS
import dev.zacsweers.metro.compiler.fir.Keys
import dev.zacsweers.metro.compiler.fir.MetroFirValueParameter
import dev.zacsweers.metro.compiler.fir.classIds
import dev.zacsweers.metro.compiler.fir.hasOrigin
import dev.zacsweers.metro.compiler.fir.isAnnotatedWithAny
import dev.zacsweers.metro.compiler.fir.getAnnotationArgParamNames
import dev.zacsweers.metro.compiler.fir.argumentAsOrNull
import dev.zacsweers.metro.compiler.fir.isKClassOfTypeParameter
import dev.zacsweers.metro.compiler.fir.resolvedClassId
import dev.zacsweers.metro.compiler.fir.isResolved
import dev.zacsweers.metro.compiler.fir.isProvidesAnnotated
import dev.zacsweers.metro.compiler.fir.isCli
import dev.zacsweers.metro.compiler.fir.markAsDeprecatedHidden
import dev.zacsweers.metro.compiler.fir.metroFirBuiltIns
import dev.zacsweers.metro.compiler.fir.predicates
import dev.zacsweers.metro.compiler.fir.replaceAnnotationsSafe
import dev.zacsweers.metro.compiler.mapNotNullToSet
import dev.zacsweers.metro.compiler.memoize
import dev.zacsweers.metro.compiler.metroAnnotations
import dev.zacsweers.metro.compiler.reportCompilerBug
import dev.zacsweers.metro.compiler.symbols.Symbols
import java.util.EnumSet
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.isObject
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.computeTypeAttributes
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.extensions.ExperimentalSupertypesGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.withParameterNameAnnotation
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirBackingFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.CompilerConeAttributes
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirFunctionTypeRef
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructClassType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.functionTypeService
import org.jetbrains.kotlin.fir.types.parametersCount
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.ConstantValueKind

/** Generates factory declarations for `@Provides`-annotated members. */
internal class ProvidesFactoryFirGenerator(session: FirSession, compatContext: CompatContext) :
  FirDeclarationGenerationExtension(session), CompatContext by compatContext {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(session.predicates.providesAnnotationPredicate)
  }

  // TODO apparently writing these types of caches is bad and
  //  generate* functions should be side-effect-free, but honestly
  //  how is this practical without this? Or is it ok if it's just an
  //  internal cache? Unclear what "should not leak" means.
  private val providerFactoryClassIdsToCallables = mutableMapOf<ClassId, ProviderCallable>()
  private val providerFactoryClassIdsToSymbols = mutableMapOf<ClassId, FirClassLikeSymbol<*>>()

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val callable =
      if (classSymbol.hasOrigin(Keys.ProviderFactoryCompanionDeclaration)) {
        val owner = classSymbol.getContainingClassSymbol() ?: return emptySet()
        providerFactoryClassIdsToCallables[owner.classId]
      } else {
        providerFactoryClassIdsToCallables[classSymbol.classId]
      } ?: return emptySet()

    return buildSet {
      add(SpecialNames.INIT)
      if (classSymbol.classKind == ClassKind.OBJECT) {
        // Generate create() and newInstance headers
        add(Symbols.Names.create)
        add(callable.newInstanceName)
      }
    }
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val constructor =
      if (context.owner.classKind == ClassKind.OBJECT) {
        createDefaultPrivateConstructor(context.owner, Keys.Default)
      } else {
        val callable =
          providerFactoryClassIdsToCallables[context.owner.classId] ?: return emptyList()
        buildFactoryConstructor(
          context,
          callable.instanceReceiver,
          null,
          callable.valueParameters.dedupeParameters(session),
        )
      }
    return listOf(constructor.symbol)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val nonNullContext = context ?: return emptyList()
    val factoryClassId =
      if (nonNullContext.owner.isCompanion) {
        nonNullContext.owner.getContainingClassSymbol()?.classId ?: return emptyList()
      } else {
        nonNullContext.owner.classId
      }
    val callable = providerFactoryClassIdsToCallables[factoryClassId] ?: return emptyList()
    val function =
      when (callableId.callableName) {
        Symbols.Names.create -> {
          buildFactoryCreateFunction(
            context = nonNullContext,
            returnType =
              Symbols.ClassIds.metroFactory.constructClassLikeType(arrayOf(callable.returnType)),
            instanceReceiver = callable.instanceReceiver,
            extensionReceiver = null,
            valueParameters = callable.valueParameters.dedupeParameters(session),
          )
        }
        callable.newInstanceName -> {
          buildNewInstanceFunction(
            nonNullContext,
            callable.newInstanceName,
            callable.returnType,
            callable.instanceReceiver,
            null,
            callable.valueParameters,
          )
        }
        else -> {
          println("Unrecognized function $callableId")
          return emptyList()
        }
      }
    return listOf(function)
  }

  // TODO can we get a finer-grained callback other than just per-class?
  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    return if (classSymbol.hasOrigin(Keys.ProviderFactoryCompanionDeclaration)) {
      // It's a factory's companion object
      emptySet()
    } else if (classSymbol.classId in providerFactoryClassIdsToCallables) {
      // It's a generated factory, give it a companion object if it isn't going to be an object
      if (classSymbol.classKind.isObject) {
        emptySet()
      } else {
        setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
      }
    } else if (classSymbol.hasOrigin(Keys.BindingContainerObjectDeclaration)) {
      // For generated binding container objects, discover @Provides from template interface
      // supertypes and generate factory classes for them.
      discoverBindingContainerFactories(classSymbol)
    } else {
      // Skip generic interfaces — they're template interfaces for
      // @ContributesBindingContainer whose @Provides functions are only relevant
      // when inherited by generated binding container objects. Concrete interfaces
      // (graph extensions, binding containers, contributed) don't have type parameters.
      if (
        classSymbol.classKind == ClassKind.INTERFACE &&
          classSymbol.typeParameterSymbols.isNotEmpty()
      ) {
        return emptySet()
      }

      // It's a provider-containing class, generated factory class names and store callable info
      val result =
        classSymbol.declarationSymbols
          .filterIsInstance<FirCallableSymbol<*>>()
          .filter { it.isProvidesAnnotated(session, session.classIds.providesAnnotations) }
          .mapNotNullToSet { providesCallable ->
            val providerCallable =
              providesCallable.asProviderCallable(classSymbol) ?: return@mapNotNullToSet null
            val simpleName =
              buildString {
                  append(providerCallable.name.capitalizeUS())
                  append(Symbols.Names.MetroFactory.asString())
                }
                .asName()
            simpleName.also {
              providerFactoryClassIdsToCallables[
                classSymbol.classId.createNestedClassId(simpleName)] = providerCallable
            }
          }

      result
    }
  }

  /**
   * Discovers @Provides functions from a binding container object's template interface supertypes
   * and generates factory class names for them. The binding container extends the template interface
   * (e.g., `MyTemplate<TargetClass>`), so we look at the template's @Provides functions and
   * create factory entries using the original function names (not _impl names).
   */
  @OptIn(DirectDeclarationsAccess::class)
  private fun discoverBindingContainerFactories(classSymbol: FirClassSymbol<*>): Set<Name> {
    val result = mutableSetOf<Name>()

    // Check if the target class (parent of binding container) is an object.
    // For object targets, T-typed parameters are removed from _impl functions
    // because the object singleton is passed directly instead.
    val targetClassSymbol =
      classSymbol.getContainingClassSymbol() as? FirRegularClassSymbol
    val isTargetObject = targetClassSymbol?.classKind == ClassKind.OBJECT

    // Build set of annotation-arg param names for this binding container's custom annotation
    val annotationArgParamNames = buildAnnotationArgParamNames(targetClassSymbol, classSymbol)

    // Filter out KClass<T> params (always compiler-provided), T-typed params for object targets,
    // and params matching custom annotation constructor args
    val paramFilter: ((FirValueParameterSymbol) -> Boolean) = { param ->
      val type = param.resolvedReturnType
      !isKClassOfTypeParameter(type) &&
        !(isTargetObject && type is ConeTypeParameterType) &&
        param.name !in annotationArgParamNames
    }

    for ((_, superClassSymbol) in templateInterfaceSupertypes(session, classSymbol)) {
      // Find @Provides functions on the template interface
      superClassSymbol.declarationSymbols
        .filterIsInstance<FirCallableSymbol<*>>()
        .filter { it.isProvidesAnnotated(session, session.classIds.providesAnnotations) }
        .forEach { providesCallable ->
          val providerCallable =
            providesCallable.asProviderCallable(
              classSymbol,
              instanceReceiver = null,
              filterParams = paramFilter,
            ) ?: return@forEach

          val simpleName =
            buildString {
                append(providerCallable.name.capitalizeUS())
                append(Symbols.Names.MetroFactory.asString())
              }
              .asName()

          val factoryClassId = classSymbol.classId.createNestedClassId(simpleName)
          providerFactoryClassIdsToCallables[factoryClassId] = providerCallable
          result.add(simpleName)
        }
    }

    return result
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    return if (name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
      // It's a factory's companion object, just generate the declaration
      createCompanionObject(owner, Keys.ProviderFactoryCompanionDeclaration).symbol
    } else if (owner.classId.createNestedClassId(name) in providerFactoryClassIdsToCallables) {
      // It's a factory class itself
      val classId = owner.classId.createNestedClassId(name)
      val sourceCallable = providerFactoryClassIdsToCallables[classId] ?: return null

      val classKind =
        if (sourceCallable.shouldGenerateObject) {
          ClassKind.OBJECT
        } else {
          ClassKind.CLASS
        }

      createNestedClass(
          owner,
          name.capitalizeUS(),
          Keys.ProviderFactoryClassDeclaration,
          classKind = classKind,
        ) {
          // For binding container factories, set Factory<ReturnType> supertype directly since
          // computeAdditionalSupertypesForGeneratedNestedClass is not called for second-level
          // generated nested classes (factory nested inside generated binding container object).
          if (owner.hasOrigin(Keys.BindingContainerObjectDeclaration)) {
            computeFactorySupertypeForBindingContainer(owner, sourceCallable)?.let {
              superType(it)
            }
          }
        }
        .apply {
          markAsDeprecatedHidden(session)
          // Add the source callable info
          replaceAnnotationsSafe(
            annotations + listOf(buildCallableMetadataAnnotation(sourceCallable))
          )
        }
        .symbol
        .also { providerFactoryClassIdsToSymbols[it.classId] = it }
    } else {
      null
    }
  }

  /**
   * For binding container factory classes, compute `Factory<ReturnType>` as a supertype directly.
   * This is needed because the Kotlin compiler framework doesn't call
   * [ProvidesFactorySupertypeGenerator.computeAdditionalSupertypesForGeneratedNestedClass] for
   * second-level generated nested classes (factory nested inside a generated binding container).
   */
  private fun computeFactorySupertypeForBindingContainer(
    owner: FirClassSymbol<*>,
    callable: ProviderCallable,
  ): ConeKotlinType? {
    for ((coneType, superClassSymbol) in templateInterfaceSupertypes(session, owner)) {
      // Build substitution map: template type params -> actual type args
      val substitutionMap =
        superClassSymbol.typeParameterSymbols
          .zip(coneType.typeArguments.mapNotNull { it as? ConeKotlinType })
          .toMap()
      val substitutor = substitutorByMap(substitutionMap, session)
      val substitutedReturnType = substitutor.substituteOrSelf(callable.returnType)

      return Symbols.ClassIds.metroFactory.constructClassLikeType(arrayOf(substitutedReturnType))
    }
    return null
  }

  private fun FirCallableSymbol<*>.asProviderCallable(owner: FirClassSymbol<*>): ProviderCallable? {
    val instanceReceiver = if (owner.classKind.isObject) null else owner.defaultType()
    return asProviderCallable(owner, instanceReceiver)
  }

  private fun FirCallableSymbol<*>.asProviderCallable(
    owner: FirClassSymbol<*>,
    instanceReceiver: ConeClassLikeType?,
    filterParams: ((FirValueParameterSymbol) -> Boolean)? = null,
  ): ProviderCallable? {
    val params =
      when (this) {
        is FirPropertySymbol -> emptyList()
        is FirNamedFunctionSymbol -> {
          val symbols =
            if (filterParams != null) {
              this.valueParameterSymbols.filter(filterParams)
            } else {
              this.valueParameterSymbols
            }
          symbols.map { MetroFirValueParameter(session, it) }
        }
        else -> return null
      }
    return ProviderCallable(owner, this, instanceReceiver, params)
  }

  private fun buildCallableMetadataAnnotation(sourceCallable: ProviderCallable): FirAnnotation {
    return buildAnnotation {
      val anno = session.metroFirBuiltIns.callableMetadataClassSymbol

      annotationTypeRef = anno.defaultType().toFirResolvedTypeRef()

      argumentMapping = buildAnnotationArgumentMapping {
        mapping[Name.identifier("callableName")] =
          buildLiteralExpression(
            source = null,
            kind = ConstantValueKind.String,
            value = sourceCallable.callableId.callableName.asString(),
            annotations = null,
            setType = true,
            prefix = null,
          )

        val symbolToMap =
          when (val symbol = sourceCallable.symbol) {
            is FirPropertyAccessorSymbol -> symbol.propertySymbol
            is FirPropertySymbol -> symbol
            is FirNamedFunctionSymbol -> symbol
            is FirBackingFieldSymbol -> symbol.propertySymbol
            is FirFieldSymbol -> symbol
            else -> reportCompilerBug("Unexpected callable symbol type: $symbol")
          }

        // Only set propertyName if it's a property
        val propertyName =
          if (symbolToMap !is FirNamedFunctionSymbol) {
            symbolToMap.name.asString()
          } else {
            ""
          }
        mapping[Name.identifier("propertyName")] =
          buildLiteralExpression(
            source = null,
            kind = ConstantValueKind.String,
            value = propertyName,
            annotations = null,
            setType = true,
            prefix = null,
          )

        mapping[Name.identifier("startOffset")] =
          buildLiteralExpression(
            source = null,
            kind = ConstantValueKind.Int,
            value = symbolToMap.source?.startOffset ?: UNDEFINED_OFFSET,
            annotations = null,
            setType = true,
            prefix = null,
          )

        mapping[Name.identifier("endOffset")] =
          buildLiteralExpression(
            source = null,
            kind = ConstantValueKind.Int,
            value = symbolToMap.source?.endOffset ?: UNDEFINED_OFFSET,
            annotations = null,
            setType = true,
            prefix = null,
          )

        mapping[Name.identifier("newInstanceName")] =
          buildLiteralExpression(
            source = null,
            kind = ConstantValueKind.String,
            value = sourceCallable.newInstanceName.asString(),
            annotations = null,
            setType = true,
            prefix = null,
          )
      }
    }
  }

  class ProviderCallable(
    val owner: FirClassSymbol<*>,
    val symbol: FirCallableSymbol<*>,
    val instanceReceiver: ConeClassLikeType?,
    val valueParameters: List<MetroFirValueParameter>,
  ) {
    val callableId = CallableId(owner.classId, symbol.name)
    val name = symbol.name
    val shouldGenerateObject by memoize {
      instanceReceiver == null && (isProperty || valueParameters.isEmpty())
    }
    private val isProperty
      get() = symbol is FirPropertySymbol

    val returnType
      get() = symbol.resolvedReturnType

    val newInstanceName: Name
      get() = name
  }
}

internal class ProvidesFactorySupertypeGenerator(
  session: FirSession,
  compatContext: CompatContext,
) : FirSupertypeGenerationExtension(session), CompatContext by compatContext {

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return declaration.symbol.hasOrigin(Keys.ProviderFactoryClassDeclaration)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> = emptyList()

  @OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
  @ExperimentalSupertypesGenerationApi
  override fun computeAdditionalSupertypesForGeneratedNestedClass(
    klass: FirRegularClass,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {
    val originClassSymbol =
      klass.getContainingClassSymbol() as? FirClassSymbol<*> ?: return emptyList()

    val klassNameStr = klass.name.asString()
    val withoutFactory = klassNameStr.removeSuffix(Symbols.Names.MetroFactory.asString())
    val callableName = withoutFactory.decapitalizeUS()
    val callable =
      originClassSymbol.declarationSymbols.filterIsInstance<FirCallableSymbol<*>>().firstOrNull {
        val nameMatches =
          it.name.asString().equals(callableName, ignoreCase = true) ||
            (it is FirPropertySymbol &&
              it.name
                .asString()
                .equals(callableName.removePrefix("get").decapitalizeUS(), ignoreCase = true))
        if (nameMatches) {
          // Secondary check to ensure it's a @Provides-annotated callable. Otherwise we may
          // match against overloaded non-Provides declarations
          val metroAnnotations =
            it.metroAnnotations(session, kinds = EnumSet.of(MetroAnnotations.Kind.Provides))
          metroAnnotations.isProvides
        } else {
          false
        }
      }
        // Fallback: for binding containers, look at interface supertypes for the @Provides function
        ?: findInheritedProvidesCallable(originClassSymbol, callableName)
        ?: return emptyList()

    val returnType =
      when (val type = callable.fir.returnTypeRef) {
        is FirUserTypeRef -> {
          typeResolver
            .resolveUserType(type)
            .also {
              if (it is FirErrorTypeRef) {
                val message =
                  """
                Could not resolve provider return type for provider: ${callable.callableId}
                This can happen if the provider references a class that is nested within the same parent class and has cyclical references to other classes.
                ${callable.fir.render()}
              """
                    .trimIndent()
                if (session.isCli()) {
                  reportCompilerBug(message)
                } else {
                  // TODO TypeResolveService appears to be unimplemented in the IDE
                  //  https://youtrack.jetbrains.com/issue/KT-74553/
                  System.err.println(message)
                  return emptyList()
                }
              }
            }
            .coneType
        }
        is FirFunctionTypeRef -> {
          createFunctionType(type, typeResolver) ?: return emptyList()
        }
        is FirResolvedTypeRef -> type.coneType
        is FirImplicitTypeRef -> {
          // Ignore, will report in FIR checker
          return emptyList()
        }
        else -> return emptyList()
      }

    val factoryType =
      session.symbolProvider
        .getClassLikeSymbolByClassId(Symbols.ClassIds.metroFactory)!!
        .constructType(arrayOf(returnType))
    return listOf(factoryType.toFirResolvedTypeRef().coneType)
  }

  /**
   * For binding containers with interface supertypes, find a @Provides-annotated callable by name
   * in the interface supertypes.
   */
  @OptIn(DirectDeclarationsAccess::class)
  private fun findInheritedProvidesCallable(
    classSymbol: FirClassSymbol<*>,
    callableName: String,
  ): FirCallableSymbol<*>? {
    for ((_, superClassSymbol) in templateInterfaceSupertypes(session, classSymbol)) {
      val callable =
        superClassSymbol.declarationSymbols
          .filterIsInstance<FirCallableSymbol<*>>()
          .firstOrNull {
            val nameMatches =
              it.name.asString().equals(callableName, ignoreCase = true) ||
                (it is FirPropertySymbol &&
                  it.name
                    .asString()
                    .equals(
                      callableName.removePrefix("get").decapitalizeUS(),
                      ignoreCase = true,
                    ))
            if (nameMatches) {
              val metroAnnotations =
                it.metroAnnotations(session, kinds = EnumSet.of(MetroAnnotations.Kind.Provides))
              metroAnnotations.isProvides
            } else {
              false
            }
          }
      if (callable != null) return callable
    }
    return null
  }

  private fun FirTypeRef.coneTypeLayered(typeResolver: TypeResolveService): ConeKotlinType? {
    return when (this) {
      is FirUserTypeRef ->
        typeResolver.resolveUserType(this).takeUnless { it is FirErrorTypeRef }?.coneType
      is FirFunctionTypeRef -> createFunctionType(this, typeResolver)
      else -> coneTypeOrNull
    }
  }

  private fun createFunctionType(
    typeRef: FirFunctionTypeRef,
    typeResolver: TypeResolveService,
  ): ConeClassLikeType? {
    val parametersWithNulls =
      typeRef.contextParameterTypeRefs.map { it.coneTypeLayered(typeResolver) } +
        listOfNotNull(typeRef.receiverTypeRef?.coneTypeLayered(typeResolver)) +
        typeRef.parameters.map {
          it.returnTypeRef.coneTypeLayered(typeResolver)?.withParameterNameAnnotation(it)
        } +
        listOf(typeRef.returnTypeRef.coneTypeLayered(typeResolver))
    val parameters = parametersWithNulls.filterNotNull()
    if (parameters.size != parametersWithNulls.size) {
      val message =
        "Could not resolve function type parameters for function type: ${typeRef.render()}"
      if (session.isCli()) {
        reportCompilerBug(message)
      } else {
        // TODO TypeResolveService appears to be unimplemented in the IDE
        //  https://youtrack.jetbrains.com/issue/KT-74553/
        System.err.println(message)
        return null
      }
    }
    val functionKinds =
      session.functionTypeService.extractAllSpecialKindsForFunctionTypeRef(typeRef)
    val kind =
      when (functionKinds.size) {
        0 -> FunctionTypeKind.Function
        1 -> functionKinds.single()
        else -> {
          FunctionTypeKind.Function
        }
      }

    val classId = kind.numberedClassId(typeRef.parametersCount)

    val attributes =
      typeRef.annotations.computeTypeAttributes(
        session,
        predefined =
          buildList {
            if (typeRef.receiverTypeRef != null) {
              add(CompilerConeAttributes.ExtensionFunctionType)
            }

            if (typeRef.contextParameterTypeRefs.isNotEmpty()) {
              add(
                CompilerConeAttributes.ContextFunctionTypeParams(
                  typeRef.contextParameterTypeRefs.size
                )
              )
            }
          },
        shouldExpandTypeAliases = true,
      )
    return classId
      .toLookupTag()
      .constructClassType(parameters.toTypedArray(), typeRef.isMarkedNullable, attributes)
  }
}

/**
 * Returns the template interface supertypes of a [FirClassSymbol] (i.e. interfaces with type
 * parameters) as [ConeClassLikeType] and [FirRegularClassSymbol] pairs.
 *
 * Uses raw `superTypeRefs` instead of `resolvedSuperTypeRefs` because this may be called during
 * early phases (COMPANION_GENERATION, SUPER_TYPES) when supertypes aren't fully resolved yet.
 */
@OptIn(SymbolInternals::class)
private fun templateInterfaceSupertypes(
  session: FirSession,
  classSymbol: FirClassSymbol<*>,
): List<Pair<ConeClassLikeType, FirRegularClassSymbol>> {
  return buildList {
    for (typeRef in (classSymbol as FirRegularClassSymbol).fir.superTypeRefs) {
      val coneType = typeRef.coneTypeOrNull as? ConeClassLikeType ?: continue
      val superClassSymbol =
        session.symbolProvider.getClassLikeSymbolByClassId(coneType.lookupTag.classId)
          as? FirRegularClassSymbol ?: continue
      if (superClassSymbol.classKind != ClassKind.INTERFACE) continue
      if (superClassSymbol.typeParameterSymbols.isEmpty()) continue
      add(coneType to superClassSymbol)
    }
  }
}

/**
 * Finds the annotation-arg param names for a binding container's custom annotation.
 * Iterates the target class's annotations, finds the one whose `@ContributesBindingContainer`
 * template matches the binding container's template interface supertype, and returns its
 * non-scope/replaces constructor param names.
 */
private fun buildAnnotationArgParamNames(
  targetClassSymbol: FirRegularClassSymbol?,
  bindingContainerSymbol: FirClassSymbol<*>,
): Set<Name> {
  if (targetClassSymbol == null) return emptySet()
  val session = bindingContainerSymbol.moduleData.session

  // Get the template interface ClassId from the binding container's supertypes
  val templateClassIds = templateInterfaceSupertypes(session, bindingContainerSymbol)
    .map { (_, superClassSymbol) -> superClassSymbol.classId }
    .toSet()
  if (templateClassIds.isEmpty()) return emptySet()

  val classIds = session.classIds

  // Find the custom annotation on the target whose @ContributesBindingContainer points to this template
  for (annotation in targetClassSymbol.resolvedCompilerAnnotationsWithClassIds) {
    if (!annotation.isResolved) continue
    val annotationClassId = annotation.toAnnotationClassIdSafe(session) ?: continue
    val annotationClassSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(annotationClassId)
        as? FirRegularClassSymbol ?: continue

    val metaAnnotation =
      annotationClassSymbol.resolvedCompilerAnnotationsWithClassIds.firstOrNull {
        it.isResolved &&
          it.toAnnotationClassIdSafe(session) == classIds.contributesBindingContainerAnnotation
      } ?: continue

    val templateArg =
      metaAnnotation.argumentAsOrNull<FirGetClassCall>(
        Symbols.Names.template, 0
      )?.resolvedClassId() ?: continue

    if (templateArg in templateClassIds) {
      return getAnnotationArgParamNames(annotationClassSymbol, session)
    }
  }

  return emptySet()
}
