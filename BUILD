load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.adblib",
    srcs = ["src"],
    iml_files = ["android.sdktools.adblib.iml"],
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
    ],
)

# Build adblib as a standalone library, with side effect of ensuring that adblib does not
# use unwanted dependencies from "studio-sdk" in the iml_module rule above
# Build with: bazel build //tools/base/adblib:adblib
kotlin_library(
    name = "adblib",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    resource_strip_prefix = "tools/base/adblib",
    visibility = [
        "//tools/base/adblib-tools:__subpackages__",
    ],
    deps = [
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

# Test adblib as a standalone library, with side effect of ensuring that adblib does not
# use unwanted dependencies from "studio-sdk" in the iml_module rule above
# Run tests with: bazel test //tools/base/adblib:adblib.tests.test
kotlin_test(
    name = "adblib.tests",
    srcs = glob([
        "test/src/**/*.kt",
        "test/src/**/*.java",
    ]),
    # So that we can use adblib "internal" classes/functions from Unit Tests
    friends = [":adblib"],
    jvm_flags = ["-Dtest.suite.jar=adblib.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    visibility = [
        "//tools/base/adblib-tools:__subpackages__",
    ],
    deps = [
        ":adblib",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "@maven//:junit.junit",
        # FakeAdbServer
        "//tools/base/fakeadbserver:tools.fakeadbserver",
    ],
)
