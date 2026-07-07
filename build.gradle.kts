plugins {
    // The org.zaproxy.add-on plugin does NOT apply the java plugin itself (the
    // zap-extensions monorepo applies it centrally via org.zaproxy.common); a
    // standalone add-on must apply it so java{}/implementation/etc. resolve.
    `java-library`
    id("org.zaproxy.add-on") version "0.13.1"
}

group = "io.disclose"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Gson is NOT provided by ZAP at runtime -> bundled into the .zap add-on.
    implementation("com.google.code.gson:gson:2.14.0")
}

zapAddOn {
    addOnName.set("Disclosure Contact Lookup")
    addOnStatus.set(org.zaproxy.gradle.addon.AddOnStatus.ALPHA)
    zapVersion.set("2.16.0")
    manifest {
        author.set("disclose.io")
        repo.set("https://github.com/disclose/zap-lookup")
        url.set("https://github.com/disclose/zap-lookup")
    }
}
