import kotlin.reflect.KClass

interface IntProviderTemplate<T> {
  @Provides fun provideInt(): Int = 42
}

@ContributesBindingContainer(IntProviderTemplate::class, defaultScope = AppScope::class)
annotation class IntProvider

@IntProvider
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
