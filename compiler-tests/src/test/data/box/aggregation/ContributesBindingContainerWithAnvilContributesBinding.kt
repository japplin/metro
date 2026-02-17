// WITH_ANVIL
import kotlin.reflect.KClass

// MODULE: lib
interface MultibindingScopedTemplate<T : Any> {
  @Provides @IntoSet fun intoSet(target: T): Any = target
}

@ContributesBindingContainer(MultibindingScopedTemplate::class)
annotation class ContributesMultibindingScoped(val scope: KClass<*>)

// MODULE: main(lib)
import com.squareup.anvil.annotations.ContributesBinding

interface ForegroundActivityProvider

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ForegroundActivityProvider::class)
@ContributesMultibindingScoped(AppScope::class)
@Inject
class ActivityListener : ForegroundActivityProvider

@DependencyGraph(AppScope::class)
interface AppGraph {
  val provider: ForegroundActivityProvider
  val allListeners: Set<Any>
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  val provider = graph.provider
  assertIs<ActivityListener>(provider)
  // Verify it's scoped (same instance)
  assertSame(provider, graph.provider)
  // Verify it's also contributed into set
  assertEquals(1, graph.allListeners.size)
  assertContains(graph.allListeners, provider)
  return "OK"
}
