import kotlin.reflect.KClass

interface IntTemplate<T> {
  @Provides fun provideInt(): Int = 1
}

@ContributesBindingContainer(IntTemplate::class)
annotation class IntContainer(val scope: KClass<*>)

@IntContainer(AppScope::class)
@Inject
class OriginalTarget

// Replaces the generated binding container for OriginalTarget via @Origin
@ContributesTo(AppScope::class, replaces = [OriginalTarget::class])
@BindingContainer
object ReplacementBinding {
  @Provides fun provideInt(): Int = 2
}

@DependencyGraph(AppScope::class)
interface AppGraph {
  val int: Int
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  assertEquals(2, graph.int)
  return "OK"
}
