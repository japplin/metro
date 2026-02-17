import kotlin.reflect.KClass

interface TaggedTemplate<T : Any> {
  @Provides fun provideTag(tag: String): String = tag
  @Provides fun providePriority(priority: Int): Int = priority
}

@ContributesBindingContainer(TaggedTemplate::class)
annotation class TaggedContainer(val scope: KClass<*>, val tag: String, val priority: Int)

@TaggedContainer(AppScope::class, tag = "hello", priority = 42)
@Inject
class MyTarget

@DependencyGraph(AppScope::class)
interface AppGraph {
  val tag: String
  val priority: Int
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals("hello", graph.tag)
  assertEquals(42, graph.priority)
  return "OK"
}
