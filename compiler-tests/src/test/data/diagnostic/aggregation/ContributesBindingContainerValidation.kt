// RENDER_DIAGNOSTICS_FULL_TEXT
import kotlin.reflect.KClass

interface ValidTemplate<T>

// Valid annotation class
@ContributesBindingContainer(ValidTemplate::class)
annotation class ValidAnnotation(val scope: KClass<*>, val replaces: Array<KClass<*>> = [])

// Error: template is not an interface (it's a class)
class NotAnInterface<T>

<!AGGREGATION_ERROR!>@ContributesBindingContainer(NotAnInterface::class)<!>
annotation class BadValueNotInterface(val scope: KClass<*>)

// Error: interface has no type parameters
interface NoTypeParams

<!AGGREGATION_ERROR!>@ContributesBindingContainer(NoTypeParams::class)<!>
annotation class BadValueNoTypeParams(val scope: KClass<*>)

// Error: interface has two type parameters
interface TwoTypeParams<A, B>

<!AGGREGATION_ERROR!>@ContributesBindingContainer(TwoTypeParams::class)<!>
annotation class BadValueTwoTypeParams(val scope: KClass<*>)

// Error: annotation class missing scope parameter and no defaultScope
@ContributesBindingContainer(ValidTemplate::class)
annotation class <!AGGREGATION_ERROR!>MissingScopeParam<!>

// Valid: no scope parameter but defaultScope is set
@ContributesBindingContainer(ValidTemplate::class, defaultScope = AppScope::class)
annotation class ValidWithDefaultScope(val replaces: Array<KClass<*>> = [])
