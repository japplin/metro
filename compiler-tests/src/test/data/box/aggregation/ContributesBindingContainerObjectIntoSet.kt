import kotlin.reflect.KClass

interface IntoSetTemplate<T : Any> {
  @Provides @IntoSet fun intoSet(target: T): Any = target
}

@ContributesBindingContainer(IntoSetTemplate::class)
annotation class AddToSet(val scope: KClass<*>)

@AddToSet(AppScope::class)
object FeatureA

@AddToSet(AppScope::class)
object FeatureB

@DependencyGraph(AppScope::class)
interface AppGraph {
  val features: Set<Any>
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals(setOf(FeatureA, FeatureB), graph.features)
  return "OK"
}
