package com.example.smgallery

class NasObject<T>(public val data: T, public val success: Boolean = true, public val error: NasError) {
}