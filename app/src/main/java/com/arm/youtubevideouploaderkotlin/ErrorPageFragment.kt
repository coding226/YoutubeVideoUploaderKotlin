package com.arm.youtubevideouploaderkotlin

import android.os.Bundle
import android.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_error_page.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val Error_Message = "param1"

class ErrorPageFragment : Fragment(), View.OnClickListener {
    // TODO: Rename and change types of parameters
    private var errorMesssage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            errorMesssage = it.getString(Error_Message)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_error_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        errorTv.setText(errorMesssage)
        closeBtn.setOnClickListener(this)
        view.setOnTouchListener { view, motionEvent -> true}
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param errorMess Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ErrorPageFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(errorMess: String) =
            ErrorPageFragment().apply {
                arguments = Bundle().apply {
                    putString(Error_Message, errorMess)
                }
            }
    }

    override fun onClick(p0: View?) {
        val transaction = activity?.fragmentManager?.beginTransaction();
        transaction?.remove(this)?.commit()
    }
}