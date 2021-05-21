# Overview

At its core, `adblib` is a Java/Kotlin library that allows invoking services
of an ADB `host` (or `server`) via a socket channel. It is in essence a
library that allows programmatic access to services that can otherwise be
invoked from the `adb` shell command.

`adblib` is usable from various `host` environments, from a default command
line environment using JDK defaults, to a highly customized environments
such as Android Studio/IntelliJ with custom threading and memory
requirements.


TBD: (Add a decription of the various services adblib provides)

# AdbLibHost

As mentioned in the overview, adblib is designed to be usable in a
variety of environements, from simple command line applications, to
servers and complex desktop apps, such as IDEs (Android Studio,
IntelliJ).

Each one of these enviroments has specific requirements, such as
where logs should be recorded, how thread pools should be used,
how memory should be tuned (i.e. heap vs direct `ByteBuffers`),
etc.

`AdbLibHost` is the abstraction that allows adblib to meet these
requirements. The default implementation provides reasonable defaults
for command line apps, but the expectation is that it will be fully
customized by consumers that implement more complex use cases.

# Inversion Of Control

adblib components rely on `Inversion of Control` to obtain the services
they need to perform their tasks. There is not global mutable state
in adblib.

For instance, one of intended design goal is to allow communicating with
multiple instances of an ADB host (on different sockets ports) in a single
JVM instance without any hackery or danger of overwriting global state.

# Timeouts

adblib does not use constant/hard-coded timeout values when communicating
with the ADB host or external processes. Either timeout values are directly
provider by the consumer, and they have defaults that can be overriden.

# adblib internals & threading model

One of the design goals is to never block a thread waiting for I/O completion,
but instead use coroutines and asynchronous APIs to suspend execution and
resume when data has been consumed or is available for use.

In addition to being fully asynchronous, adblib never creates custom threads
but instead "borrows" existing threads from various thread pool abstractions
defined in `AdbLibHost`, e.g. Kotlin `Dispatchers` for coroutines, 
`AsynchronousChannelGroup` for `AsynchronousSocketChannel`, and so on.

Specifically, adblib uses `Dispatchers.IO` only *indirectly* through the
`AdbLibHost` abstraction, which is customizable by the consumer of adblib,
so that custom `Dispatcher` implementation can be used.

In essence, adblib leaves its consumer(s) the ability to fully customize
the thread pooling policy.

# Performance & memory usage

adblib should offer performance similar to the `adb` command line and avoid
excessive use of the GC heap. This implies some potentially "hotspots" of
adblib are consciously written to avoid excessive memory allocations,
sometimes at the cost of readability of the code.

# Callbacks

When adblib service implementations need to invoke user-provided callbacks,
these callbacks are required to provide an execution context (`Dispatcher`
or `Executor`) so that adblib does not have to make an assumption about
threading model.

adblib never "swallows" exceptions from callbacks, instead propagates them
to the caller. It is up to the caller to decide if these exceptions should
be propagated (the default), merely logged, or recovered from.

# Metrics

adblib is written such that consumers can collect various performance and
reliability metrics of the underlying ADB host.

TBD: This is currently not implemented

# Logging

adblib defines its own custom `AdbLogger` abstraction to ensure compatibility
with a wide range of logging facilities.
