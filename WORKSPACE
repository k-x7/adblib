load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# kotlin https://github.com/bazelbuild/rules_kotlin
KOTLIN = (
	"1.7.1",
	"fd92a98bd8a8f0e1cdcb490b93f5acef1f1727ed992571232d33de42395ca9b3",
)

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = KOTLIN[1],
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/v%s/rules_kotlin_release.tgz" % KOTLIN[0]],
)

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")
kotlin_repositories()
load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")
kt_register_toolchains()


# proto https://github.com/bazelbuild/rules_proto
PROTOBUF = (
	"5.3.0-21.7",
	"dc3fb206a2cb3441b485eb1e423165b231235a1ea9b031b4433cf7bc1fa460dd",
)

http_archive(
    name = "rules_proto",
    sha256 = PROTOBUF[1],
    strip_prefix = "rules_proto-%s" % PROTOBUF[0],
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/%s.tar.gz" % PROTOBUF[0],
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")
rules_proto_dependencies()
rules_proto_toolchains()

# java proto https://github.com/bazelbuild/rules_java
JAVA_PROTOBUF = (
	"5.4.0",
	"9b87757af5c77e9db5f7c000579309afae75cf6517da745de01ba0c6e4870951"
)

http_archive(
    name = "rules_java",
    url = "https://github.com/bazelbuild/rules_java/releases/download/%s/rules_java-%s.tar.gz" % (JAVA_PROTOBUF[0], JAVA_PROTOBUF[0]),
    sha256 = JAVA_PROTOBUF[1],
)

load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")
rules_java_dependencies()
rules_java_toolchains()

# jvm external https://github.com/bazelbuild/rules_jvm_external
JVM_EXTERNAL = (
	"4.5",
	"b17d7388feb9bfa7f2fa09031b32707df529f26c91ab9e5d909eb1676badd9a6",
)

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % JVM_EXTERNAL[0],
    sha256 = JVM_EXTERNAL[1],
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/refs/tags/%s.zip" % JVM_EXTERNAL[0],
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")
rules_jvm_external_deps()
load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")
rules_jvm_external_setup()

# external deps
load("@rules_jvm_external//:defs.bzl", "maven_install")
maven_install(
    artifacts = [
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4",
        "com.google.protobuf:protobuf-java:3.21.12",
    ],
    repositories = [
        "https://maven.google.com",
	"https://repo1.maven.org/maven2",
    ],
)
