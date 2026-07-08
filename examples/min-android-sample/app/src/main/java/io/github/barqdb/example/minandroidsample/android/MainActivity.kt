package io.github.barqdb.example.minandroidsample.android

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import io.github.barqdb.sample.minandroidsample.Greeting

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.text).text = Greeting().greeting()
    }
}