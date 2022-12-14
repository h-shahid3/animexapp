package com.project24.animexapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.project24.animexapp.databinding.ActivityLogInBinding

class LogInActivity : AppCompatActivity() {

    private lateinit var binding:ActivityLogInBinding
    private lateinit var firebaseAuth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLogInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        binding.signUpBtn.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.logInBtn.setOnClickListener {
            val userEmail = binding.emailInput.text.toString()
            val userPass = binding.passwordInput.text.toString()

            if (userEmail.isNotEmpty() && userPass.isNotEmpty()) {
                firebaseAuth.signInWithEmailAndPassword(userEmail, userPass).addOnCompleteListener {
                    if(it.isSuccessful) {
                        Toast.makeText(this, "Logged in successfully", Toast.LENGTH_SHORT).show()


                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)

                    } else {
                        Toast.makeText(this, "Incorrect email or password. Please try again", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "All fields must be filled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}