import kotlin.reflect.KClass

// MODULE: lib
interface MyTemplate<T : Any> {
  @Provides fun provideName(target: T): String = target::class.simpleName!!
}

@ContributesBindingContainer(MyTemplate::class)
annotation class MyContainer(val scope: KClass<*>)

// MODULE: main(lib)
@MyContainer(AppScope::class)
@Inject
class SomeTarget

@DependencyGraph(AppScope::class)
interface AppGraph {
  val name: String
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals("SomeTarget", graph.name)
  return "OK"
}
