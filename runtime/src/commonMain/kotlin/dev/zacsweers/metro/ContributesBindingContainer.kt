// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro

import kotlin.reflect.KClass

/**
 * A meta-annotation that links a custom annotation to a binding container interface template.
 *
 * This enables defining reusable binding container templates as interfaces with a single type
 * parameter, then stamping out implementations for annotated classes.
 *
 * ## Example
 *
 * ```
 * // 1. Define a template interface with one type parameter
 * interface MyBindingContainerInterface<Target> {
 *   @Provides fun customProvides(target: Target): WhateverTypeIWant = WhateverTypeIWant(target)
 * }
 *
 * // 2. Create a custom annotation linked to the template
 * @ContributesBindingContainer(MyBindingContainerInterface::class)
 * annotation class CustomAnnotation(val scope: KClass<*>, val replaces: Array<KClass<*>> = [])
 *
 * // 3. Apply it to target classes
 * @CustomAnnotation(AppScope::class)
 * class TargetClass @Inject constructor()
 * ```
 *
 * Metro will generate a nested binding container object for each annotated class that implements the
 * template interface with the target class as the type argument, contributing it to the specified
 * scope.
 *
 * ## Default Scope
 *
 * If all targets should contribute to the same scope, you can specify a [defaultScope] to avoid
 * requiring a `scope` parameter on every usage:
 *
 * ```
 * @ContributesBindingContainer(MyBindingContainerInterface::class, defaultScope = AppScope::class)
 * annotation class CustomAnnotation(val replaces: Array<KClass<*>> = [])
 *
 * @CustomAnnotation  // no scope needed!
 * class TargetClass @Inject constructor()
 * ```
 *
 * If the custom annotation still has a `scope` parameter, the user-provided scope takes precedence
 * over [defaultScope].
 *
 * ## Requirements
 *
 * - The annotated annotation must have a `scope: KClass<*>` parameter, or [defaultScope] must be
 *   set.
 * - The [template] must be an interface with exactly one type parameter.
 * - Classes annotated with the custom annotation must be injectable (`@Inject` constructor).
 *   Kotlin `object` classes are also supported without `@Inject` — any template parameters typed as
 *   `T` are automatically resolved to the object singleton.
 * - Template `@Provides` functions may also accept `KClass<T>` parameters. These are
 *   compiler-provided (not DI-injected) and resolved to `TargetClass::class` in the generated code.
 *   This works for both `@Inject` classes and `object` targets.
 * - Template `@Provides` functions may also declare parameters matching the custom annotation's
 *   constructor parameters by name and type. These are compiler-provided and resolved to the
 *   annotation argument values at each usage site.
 *
 * @property template The binding container interface template with exactly one type parameter.
 * @property defaultScope The default scope to use when the custom annotation does not have a
 *   `scope` parameter. `Nothing::class` (the default) means no default scope is set and the custom
 *   annotation must provide its own `scope` parameter.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class ContributesBindingContainer(
  val template: KClass<*>,
  val defaultScope: KClass<*> = Nothing::class,
)
