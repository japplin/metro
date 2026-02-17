// WITH_ANVIL
import kotlin.reflect.KClass

// MODULE: lib
interface MultibindingScopedTemplate<T : Any> {
  @Provides @IntoSet fun intoSet(target: T): Any = target
}

@ContributesBindingContainer(MultibindingScopedTemplate::class)
annotation class ContributesMultibindingScoped(val scope: KClass<*>)

// MODULE: impl(lib)
import com.squareup.anvil.annotations.ContributesBinding

interface ForegroundActivityProvider

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ForegroundActivityProvider::class)
@ContributesMultibindingScoped(AppScope::class)
@Inject
class ActivityListener : ForegroundActivityProvider

// MODULE: main(impl)
@DependencyGraph(AppScope::class)
interface AppGraph {
  val provider: ForegroundActivityProvider
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  val provider = graph.provider
  assertIs<ActivityListener>(provider)
  return "OK"
}
