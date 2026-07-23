// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Contributes an [IntoMap] binding of the annotated type to the given [scope] as a [binding] (if
 * specified) or single declared supertype. A [MapKey] _must_ be declared on the annotated class,
 * [binding], a generic type argument of the bound type, or a type parameter marked with an
 * implicit class [MapKey] by a [DefaultBinding].
 *
 * ```
 * // Implicit supertype is Base
 * @ClassKey(Impl::class)
 * @ContributesIntoMap(AppScope::class)
 * @Inject
 * class Impl : Base
 * ```
 *
 * Use [binding] to specify a specific bound type if an implicit one is not possible.
 *
 * ```
 * // Explicit supertype is Base
 * @ClassKey(Impl::class)
 * @ContributesIntoMap(AppScope::class, binding = binding<Base>())
 * @Inject
 * class Impl : Base, AnotherBase
 * ```
 *
 * [binding]'s type argument can also be annotated with a [MapKey].
 *
 * ```
 * // Explicit supertype is Base
 * @ContributesIntoMap(AppScope::class, binding = binding<@ClassKey(Impl::class) Base>())
 * @Inject
 * class Impl : Base, AnotherBase
 * ```
 *
 * A map key can also be declared on a generic type argument of the bound type.
 *
 * ```
 * interface Screen<T>
 *
 * @ContributesIntoMap(AppScope::class)
 * @Inject
 * class HomeScreen : Screen<@ClassKey HomeKey>
 * ```
 *
 * A [DefaultBinding] can declare that one of its type parameters supplies the map key for
 * implementing classes.
 *
 * ```
 * @DefaultBinding<Screen<*>>
 * interface Screen<@ClassKey T>
 *
 * @ContributesIntoMap(AppScope::class)
 * @Inject
 * class HomeScreen : Screen<HomeKey>
 * ```
 *
 * This annotation is _repeatable_, allowing for contributions as multiple bound types. Note that
 * all repeated annotations must use the same [scope].
 *
 * If this declaration is scoped, the [Scope] annotation will be propagated to the generated
 * [IntoMap] declaration.
 *
 * If this declaration is qualified, the [Qualifier] annotation will be propagated to the generated
 * [IntoMap] declaration.
 *
 * @property scope The scope this binding contributes to.
 * @property binding The explicit bound type for this contribution, if not using the implicit
 *   supertype.
 * @property replaces List of other contributing classes that this binding should replace in the
 *   scope.
 */
@Target(CLASS)
@Repeatable
public annotation class ContributesIntoMap(
  val scope: KClass<*>,
  val binding: binding<*> = binding<Nothing>(),
  val replaces: Array<KClass<*>> = [],
)
