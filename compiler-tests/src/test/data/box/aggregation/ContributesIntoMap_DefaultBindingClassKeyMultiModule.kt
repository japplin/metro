import kotlin.reflect.KClass

// MODULE: api
interface RouteKey

@DefaultBinding<RouteScreen<*>>
interface RouteScreen<@ClassKey T : RouteKey>

// MODULE: impl(api)
class HomeKey : RouteKey

@ContributesIntoMap(AppScope::class)
@Inject
class HomeScreen : RouteScreen<HomeKey>

class OverrideHomeKey : RouteKey

class AlternateHomeKey : RouteKey

@ContributesIntoMap(AppScope::class)
@Inject
class OverrideHomeScreen : RouteScreen<@ClassKey(AlternateHomeKey::class) OverrideHomeKey>

// MODULE: main(api, impl)
import kotlin.reflect.KClass

@DependencyGraph(AppScope::class)
interface RouteGraph {
  val screens: Map<KClass<*>, RouteScreen<*>>
}

fun box(): String {
  val graph = createGraph<RouteGraph>()
  assertEquals(2, graph.screens.size)
  assertIs<HomeScreen>(graph.screens.getValue(HomeKey::class))
  assertIs<OverrideHomeScreen>(graph.screens.getValue(AlternateHomeKey::class))
  assertFalse(graph.screens.containsKey(OverrideHomeKey::class))
  return "OK"
}
