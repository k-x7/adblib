load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.adblib",
    srcs = ["src"],
    iml_files = ["android.sdktools.adblib.iml"],
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

# Build adblib as a standalone library, with side effect of ensuring that adblib does not
# use unwanted dependencies from "studio-sdk" in the iml_module rule above
kotlin_library(
    name = "tools.adblib",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    resource_strip_prefix = "tools/base/adblib",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
    ],
)
