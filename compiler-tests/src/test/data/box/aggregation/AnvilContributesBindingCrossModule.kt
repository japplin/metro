// WITH_ANVIL

// MODULE: lib
import com.squareup.anvil.annotations.ContributesBinding

interface ForegroundActivityProvider

@ContributesBinding(AppScope::class, boundType = ForegroundActivityProvider::class)
@Inject
class ActivityListener : ForegroundActivityProvider

// MODULE: main(lib)
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
