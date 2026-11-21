import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Android 模块仍是 JDK 17 目标；本机只装了 JDK 21，因此不用 toolchain，显式指定字节码与源码级别。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

application {
    mainClass.set("me.rerere.tools.providermock.MainKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // ProviderMockRule 是 JUnit 4 的 ExternalResource，供 :ai 单测直接挂成 @Rule
    implementation(libs.junit)

    testImplementation(libs.junit)
}
