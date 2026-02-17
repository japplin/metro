import kotlin.reflect.KClass

sealed interface OtherScope

interface IntTemplate<T> {
  @Provides fun provideInt(): Int = 42
}

@ContributesBindingContainer(IntTemplate::class, defaultScope = OtherScope::class)
annotation class IntContainer(val scope: KClass<*> = OtherScope::class)

// Explicit scope overrides defaultScope
@IntContainer(scope = AppScope::class)
@Inject
class SomeTarget

@DependencyGraph(AppScope::class)
interface AppGraph {
  val int: Int
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals(42, graph.int)
  return "OK"
}
