import kotlin.reflect.KClass

interface MixedTemplate<T : Any> {
  @Provides fun provideDescription(target: T, targetClass: KClass<T>, label: String): String =
    "$label:${targetClass.simpleName}"
}

@ContributesBindingContainer(MixedTemplate::class)
annotation class MixedContainer(val scope: KClass<*>, val label: String)

@MixedContainer(AppScope::class, label = "feature")
@Inject
class MixedTarget

@DependencyGraph(AppScope::class)
interface AppGraph {
  val description: String
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals("feature:MixedTarget", graph.description)
  return "OK"
}
