// Только плагины сборки. В самом приложении нет сторонних библиотек:
// ни Compose, ни AndroidX, ни Retrofit/OkHttp/Glide — UI и сеть написаны на платформенном API.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}
