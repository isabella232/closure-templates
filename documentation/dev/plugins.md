[TOC]

# Creating a Custom Plugin

`Closure Templates` allows users to write functions that templates can call. This
is useful for when there is some logic that is difficult or impossible to
express using `Closure Templates` language features. `Closure Templates` actually has
a number of these built in. For example, the `mapKeys` function which can be
used to get the keys of a map for iteration.

```soy
{template .foo}
  {@param m: map<string, string>}
  {for $key in mapKeys($m)}
    Key: {$key}, Value: {$m[$key]}
  {/for}
{/template}
```

However, it isn't possible for `Closure Templates` to supply all possible desired
functionality so `Closure Templates` allows users to supply custom plugin function
definitions.

## When to define a custom function

Soy functions are a fairly powerful feature, but they aren't always the best
option. This is because:

*   You need to write distinct implementations for all languages (JS, Java,
    Python) that you are compiling your templates for and ensuring consistent
    behavior can be difficult.
*   The registration mechanism is cumbersome.
*   It is difficult to produce `SanitizedContent` from a plugin, so they are a
    poor fit for generating `html` `css` or `JavaScript`

So in general if you can represent your functionality using a shared template
you should do so. However, they are sometimes quite necessary. Some good
usecases include:

*   Custom date or number formatting
*   Complex math functions
*   Date calculations
*   Filtering, sorting or aggregating datastructures
*   ...

All of the above are difficult or impossible to implement in a plain template.
For these reasons `Closure Templates` allows for users to write custom functions.

## How do I create a `SoySourceFunction`?

### 1. Define the signature `SoySourceFunction`

For example, let's assume you wanted to have a function called `uniqueId()` that
returns a unique number. This might be useful for generating DOM ids. You would
start by defining a `SoyFunction` subtype:

```java
@SoyFunctionSignature(
    name = "uniqueId",
    value = @Signature(returnType="string"))
class UniqueIdFunction implements SoySourceFunction {}
```

This tells the compiler basic information for your function (name and arity),
however we don't yet have any implementations that would tell the compiler how
to generate code for this function.

### 2. Define the logic

Depending on what backends you care about, you can also have your function
implement any of the following interfaces:

1.  `SoyJavaScriptSourceFunction` for generating JS code
1.  `SoyJavaSourceFunction` for generating Java bytecode
1.  `SoyPySrcFunction` for generating Python code
1.  _experimental_ `LoggingFunction` for interacting with a `SoyLogger`. See the
    [doc-logging](doc-logging#logging_function) guide for more information

For example, if you wanted to have an implementation for both Java and JS you
would implement both those interfaces and write something like

```java
@SoyFunctionSignature(
    name = "uniqueId",
    value = @Signature(returnType="string"))
public class UniqueIdFunction implements
    SoyJavaSourceFunction, SoyJavaScriptSourceFunction {
  private static final AtomicLong counter = new AtomicLong();

  public static String nextId() {
    return "id-" + counter.incrementAndGet();
  }

  private static final Method NEXT_ID =
      JavaValueFactory.createMethod(UniqueIdFunction.class, "nextId");

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(NEXT_ID);
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
     JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    // Note: If the library isn't provided by goog.module, use callNamespaceFunction instead.
    return factory.callModuleFunction("some.js.lib", "uniqueId");
  }
}
```

Given this implementation, the `applyForJavaSource` method will invoked by the
compiler to generate Java bytecode to call the "nextId" method for server-side
rendering, and the `computeForJsSrc` method will be invoked by the compiler to
generate JS code for client-side rendering (the implementation will call the
given JS library function).

If the Java implementation needs any non-static dependencies at runtime (e.g, a
`NextIdService`), the `applyForJavaSource` method can use
`JavaValueFactory.callInstanceMethod`.For rendering to work, the instance the
plugin will use must be passed to the `SoySauce` or `SoyTofu` constructor, or if
that is impossible the Renderer's `setPluginInstances` method. For example,

```java
@SoyFunctionSignature(
    name = "uniqueId",
    value = @Signature(returnType="string"))
public class UniqueIdFunction implements
    SoyJavaSourceFunction, SoyJavaScriptSourceFunction {

  private static final Method NEXT_ID =
      JavaValueFactory.createMethod(NextIdService.class, "nextId");

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callInstanceMethod(NEXT_ID);
  }

  @Override
  JavaScriptValue applyForJavaScriptSource(
     JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    // Note: If the library isn't provided by goog.module, use callNamespaceFunction instead.
    return factory.callModuleFunction("some.js.lib", "uniqueId");
  }
}

class NextIdService {
  private final AtomicLong counter = new AtomicLong();

  public String nextId() {
    return "id-" + counter.incrementAndGet();
  }
}
```

Once you are satisfied with the implementation, you would register it with the
compiler.

#### Contextual data available to plugins

For Java, JavaPluginContext provides some contextual data.

*   getULocale

    Provides the current locale being used for rendering, as a
    `com.ibm.icu.util.ULocale`.

### 3. Register your plugin with the compiler

All SoySourceFunctions (e.g, `SoyJavaSourceFunction`) must be passed to the
compiler via the `--pluginFunctions` flag. The flag takes a comma separated list
of the fully qualified class name of the function. You will also need to make
sure that the class is on the compiler's classpath.

{{#internal}}

Google users should use the `af_soy_plugin` build rule to register their
plugins. See go/af-soy/build_rules#af-soy-plugin-and-af-soy-legacy-plugin

{{/internal}}

### 4. Use It!

At this point, you can start calling your new plugin function from your
templates. For example:

```soy
{template .foo}
  {let $id : uniqueId() /}
  <div id={$id}>
    Some content
  </div>
  ...
  <a href="#{$id}">Scroll up</a>
{/template}
```

When rendered server side, the methods referenced in `applyForJavaSource` will
be invoked. If `applyForJavaSource` used `callInstanceMethod`, the instance
classes must be supplied to `SoySauce` or `Tofu` constructor (or
`Renderer.setPluginInstances` if the former is infeasible). When compiled for
JS, the compiler will output a `goog.require('some.js.lib');` and invoke
`some.js.lib.uniqueId()` for each call to `uniqueId()`.


## More about SoyFunctionSignature...

All types that are understandable by `Closure Templates` compiler will be supported
in the annotation.

Some plugins might behave differently when the input size changes. It is
possible to define some overloads in the function signature annotation:

```java
@SoyFunctionSignature(
  name = "foo",
  value = {
    @Signature(
      parameterTypes = {"string"},
      returnType = "int"
    ),
    @Signature(
      parameterTypes = {"string", "string"},
      returnType = "string"
    )})
class FooFunction implements SoySourceFunction {}
```

There are some restrictions to this annotation. First, it does not support
overloads that have the same amount of arguments. Second, generic overloads are
unsupported. It is impossible to say this function will return `T` when the
input is `list<T>`.


## Custom print directives, not recommended

`Closure Templates` also allows the creation of custom print directives, for
example, in `{$foo|truncate:100}` `truncate` is a print directive.

The method for authoring and configuring print directives is nearly identical to
configuring a custom function (described above). However, it is no longer
recommended for projects to create these. All the usecases for a custom print
directive can be satisfied by a custom function and the syntax/behavior of those
is more intuitive.