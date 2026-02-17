import kotlin.reflect.KClass

data class TargetWrapperWithMetadata(
  val target: Any,
  val tag: String,
)

interface CustomGeneratorTemplate<Target : Any> {
  @Provides @IntoSet fun myProvider(
    target: Target,
    kClass: KClass<Target>,
    tag: String,
  ): TargetWrapperWithMetadata = TargetWrapperWithMetadata(target, "$tag:${kClass.simpleName}")
}

@ContributesBindingContainer(CustomGeneratorTemplate::class, defaultScope = AppScope::class)
annotation class MyCustomAnnotation(val tag: String)

@MyCustomAnnotation("Hello")
object TypeToAddA

@MyCustomAnnotation("World")
object TypeToAddB

@DependencyGraph(AppScope::class)
interface AppGraph {
  val wrappers: Set<TargetWrapperWithMetadata>
}

fun box(): String {
  val graph = createGraph<AppGraph>()
  val wrappers = graph.wrappers
  assertEquals(2, wrappers.size)
  val wrapperA = wrappers.first { it.target == TypeToAddA }
  assertEquals("Hello:TypeToAddA", wrapperA.tag)
  val wrapperB = wrappers.first { it.target == TypeToAddB }
  assertEquals("World:TypeToAddB", wrapperB.tag)
  return "OK"
}
