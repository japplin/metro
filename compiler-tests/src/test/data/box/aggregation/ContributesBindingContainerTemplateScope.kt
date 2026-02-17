import kotlin.reflect.KClass

private var count = 0

interface ScopedTemplate<T> {
  @Provides @SingleIn(TemplateScope::class) fun provideInt(): Int = count++
}

@ContributesBindingContainer(ScopedTemplate::class)
annotation class ScopedContainer(val scope: KClass<*>)

@ScopedContainer(AppScope::class)
@Inject
class SomeTarget

@DependencyGraph(AppScope::class)
interface AppGraph {
  val int: Int
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  val first = graph.int
  val second = graph.int
  assertEquals(first, second)
  assertEquals(0, first)
  return "OK"
}
