// The home screen photo's prep tool: cuts the photo's sky away and makes the layers the
// app paints with (see scripts/fetch-home-photo.sh). Runs on the Mac only; not in the app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions { allWarningsAsErrors = true }
}

application {
    mainClass.set("tv.livingroom.photoprep.MainKt")
}

dependencies {
    testImplementation(libs.junit)
}
