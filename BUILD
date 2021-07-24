load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library_legacy", "kotlin_test")

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
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
    ],
)

# Build adblib as a standalone library, with side effect of ensuring that adblib does not
# use unwanted dependencies from "studio-sdk" in the iml_module rule above
# Build with: bazel build //tools/base/adblib:tools.adblib
kotlin_library_legacy(
    name = "tools.adblib",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    resource_strip_prefix = "tools/base/adblib",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib-jdk8",
        "//tools/base/third_party:org.jetbrains.kotlinx_kotlinx-coroutines-core",
    ],
)

# Test adblib as a standalone library, with side effect of ensuring that adblib does not
# use unwanted dependencies from "studio-sdk" in the iml_module rule above
# Run tests with: bazel test //tools/base/adblib:tools.adblib.tests.test
kotlin_test(
    name = "tools.adblib.tests",
    srcs = glob([
        "test/src/**/*.kt",
        "test/src/**/*.java",
    ]),
    # So that we can use adblib "internal" classes/functions from Unit Tests
    friends = [":tools.adblib"],
    jvm_flags = ["-Dtest.suite.jar=tools.adblib.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.adblib",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "//tools/base/third_party:junit_junit",
        # FakeAdbServer
        "//tools/base/fakeadbserver:tools.fakeadbserver",
    ],
)
