package io.github.barqdb.sample.minandroidsample

class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}