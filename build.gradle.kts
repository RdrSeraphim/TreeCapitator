plugins {
    java
}

group = "life.srp"
version = "7.0.0-SNAPSHOT"


repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    implementation("io.papermc.paper:paper-api:26.2.build.+")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}