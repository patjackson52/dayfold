rootProject.name = "familyai-client-core"
// M0: the shared client core (redux store + sync) as a pure-Kotlin module so it
// builds + tests without the Android SDK / Xcode. The Compose UI + Android/iOS
// targets wrap this core later (it stays commonMain-compatible).
