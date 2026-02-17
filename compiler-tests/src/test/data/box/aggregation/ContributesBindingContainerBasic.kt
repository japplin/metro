import kotlin.reflect.KClass

interface MyTemplate<T : Any> {
  @Provides fun provideInt(): Int = 42
  @Provides fun provideName(target: T): String = target::class.simpleName!!
}

@ContributesBindingContainer(MyTemplate::class)
annotation class MyContainer(val scope: KClass<*>)

@MyContainer(AppScope::class)
@Inject
class SomeTarget

@DependencyGraph(AppScope::class)
interface AppGraph {
  val int: Int
  val name: String
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals(42, graph.int)
  assertEquals("SomeTarget", graph.name)
  return "OK"
}
