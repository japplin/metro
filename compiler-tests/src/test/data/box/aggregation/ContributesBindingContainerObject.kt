import kotlin.reflect.KClass

interface ObjectTemplate<T : Any> {
  @Provides fun provideName(target: T, targetClass: KClass<T>): String = targetClass.simpleName!!
}

@ContributesBindingContainer(ObjectTemplate::class)
annotation class ObjectContainer(val scope: KClass<*>)

@ObjectContainer(AppScope::class)
object MyObject

@DependencyGraph(AppScope::class)
interface AppGraph {
  val name: String
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals("MyObject", graph.name)
  return "OK"
}
