package com.android.assignment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class ResultFragment : Fragment() {

    private lateinit var viewModel: HandDetectionViewModel
    private lateinit var tvHandSide: TextView
    private lateinit var tvScannedFingers: TextView
    private lateinit var tvBrightnessScore: TextView
    private lateinit var tvBlurScore: TextView
    private lateinit var tvFocusDistance: TextView
    private lateinit var tvCameraType: TextView
    private lateinit var tvSessionId: TextView
    private lateinit var btnComplete: Button

    companion object {
        fun newInstance(handSide: String, scannedFingers: Int, sessionId: String): ResultFragment {
            return ResultFragment().apply {
                arguments = Bundle().apply {
                    putString("hand_side", handSide)
                    putInt("scanned_fingers", scannedFingers)
                    putString("session_id", sessionId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[HandDetectionViewModel::class.java]

        tvHandSide = view.findViewById(R.id.tvHandSide)
        tvScannedFingers = view.findViewById(R.id.tvScannedFingers)
        tvBrightnessScore = view.findViewById(R.id.tvBrightnessScore)
        tvBlurScore = view.findViewById(R.id.tvBlurScore)
        tvFocusDistance = view.findViewById(R.id.tvFocusDistance)
        tvCameraType = view.findViewById(R.id.tvCameraType)
        tvSessionId = view.findViewById(R.id.tvSessionId)
        btnComplete = view.findViewById(R.id.btnComplete)

        displayResults()

        btnComplete.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun displayResults() {
        val handSide = arguments?.getString("hand_side") ?: "Unknown"
        val scannedFingers = arguments?.getInt("scanned_fingers") ?: 0

        tvHandSide.text = "Hand Side: $handSide"
        tvScannedFingers.text = "Fingers Scanned: $scannedFingers/5"

        // Display metrics from ViewModel
        viewModel.imageMetrics.value?.let { metrics ->
            tvBrightnessScore.text = "Brightness Score: ${String.format("%.2f", metrics.brightnessScore)}"
            tvBlurScore.text = "Blur Score: ${String.format("%.2f", metrics.blurScore)}"
            tvFocusDistance.text = "Focus Distance: ${String.format("%.2f", metrics.focusDistance)} cm"
        } ?: run {
            tvBrightnessScore.text = "Brightness Score: N/A"
            tvBlurScore.text = "Blur Score: N/A"
            tvFocusDistance.text = "Focus Distance: N/A"
        }

        tvCameraType.text = "Camera Type: REAR" // Could be fetched from preferences
        tvSessionId.text = "Session ID: ${arguments?.getString("session_id") ?: "N/A"}"
    }
}