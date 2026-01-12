package dev.zacsweers.metro.test.integration

import dev.zacsweers.metro.binding
import kotlin.reflect.KClass

annotation class CustomIntoSetAnnotation(
  val scope: KClass<*>,
  // Should not be set by usage site.
  val binding: binding<CustomAnnotationsDefaultBoundType> = binding(),
)

interface CustomAnnotationsDefaultBoundType
