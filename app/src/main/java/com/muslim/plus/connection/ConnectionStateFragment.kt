package com.muslim.plus.connection

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.muslim.plus.MainActivity
import com.muslim.plus.databinding.FragmentConnectionStateBinding

class ConnectionStateFragment : Fragment() {


    private var _binding: FragmentConnectionStateBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentConnectionStateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val checkNet = CheckInternetAccess(context as AppCompatActivity?)
        binding.progressBarConnection.visibility = View.VISIBLE
        binding.btnCekConnection.setOnClickListener {
            if (checkNet.checkInternetAccess()) {
                binding.progressBarConnection.visibility = View.GONE
                startActivity(Intent(activity, MainActivity::class.java))
                Toast.makeText(context, "Internet Tersedia", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Pastikan Internet Tersedia", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}