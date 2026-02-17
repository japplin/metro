import kotlin.reflect.KClass

interface KClassTemplate<T : Any> {
  @Provides fun provideName(targetClass: KClass<T>): String = targetClass.simpleName!!
  @Provides fun provideHash(target: T): Int = target.hashCode()
}

@ContributesBindingContainer(KClassTemplate::class)
annotation class KClassContainer(val scope: KClass<*>)

@KClassContainer(AppScope::class)
@Inject
class SomeTarget

@DependencyGraph(AppScope::class)
interface AppGraph {
  val name: String
  val hash: Int
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals("SomeTarget", graph.name)
  return "OK"
}
