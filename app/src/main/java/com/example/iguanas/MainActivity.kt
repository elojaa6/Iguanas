package com.example.iguanas

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.iguanas.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    //val btnIngresar : Button = findViewById(R.id.btnIngresar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnIngresar.setOnClickListener(View.OnClickListener {
            val intent = Intent(this,Iguanas::class.java)
            startActivity(intent)
        })

        /*btnIngresar.setOnClickListener(View.OnClickListener {
            val intent = Intent(this, Iguanas::class.java)
            startActivity(intent)
        })

        fun btnIngresar_Click(view: Vi){
            val intent = Intent(this, Iguanas::class.java)
            startActivity(intent)
        }*/

        //Example of a call to a native method


}
}