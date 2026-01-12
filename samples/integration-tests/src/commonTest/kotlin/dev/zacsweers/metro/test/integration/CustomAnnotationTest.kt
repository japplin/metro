package dev.zacsweers.metro.test.integration

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertTrue

class CustomAnnotationTest {

  @Test
  fun customAnnotationsDefaultBindingValueWorksWithIntoSet() {
    assertTrue {
      createGraph<CustomAnnotationTestGraph>().setWithCustomBindings.single() is FooBarImpl
    }
  }

  interface FooBar : CustomAnnotationsDefaultBoundType

  @CustomIntoSetAnnotation(AppScope::class) @Inject class FooBarImpl : FooBar

  @DependencyGraph(AppScope::class)
  interface CustomAnnotationTestGraph {
    val setWithCustomBindings: Set<CustomAnnotationsDefaultBoundType>
  }
}
