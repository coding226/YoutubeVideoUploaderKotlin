package com.arm.youtubevideouploaderkotlin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.opengl.Visibility
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler
import kotlinx.android.synthetic.main.fragment_video_record.*
import kotlinx.android.synthetic.main.fragment_video_record.progressBar
import kotlinx.android.synthetic.main.fragment_video_record.progressTv
import kotlinx.android.synthetic.main.fragment_video_record.videoThumbImageView
import java.io.File

class VideoRecordFragment(val videoFile: File?) : Fragment(), View.OnClickListener {

    private val TAG = "ffmpegLog"
    private var ffMpeg: FFmpeg? = null
    private var successfullyLoaded: Boolean = false

    //    var videoPath = "/storage/emulated/0/videokit/vitamin.mp4"
    var watermarkPath: String? = null
    private val output: String? = "/storage/emulated/0/videokit/" + videoFile?.name

    private val MY_PERMISSIONS_STORAGE = 0
    private val IMAGE_RETURN = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_video_record, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnTouchListener { view, motionEvent -> true }
        addWatermarkBtn.setOnClickListener(this)
        ffMpeg = FFmpeg.getInstance(context)
        var success = false
        ffMpeg?.loadBinary(object : LoadBinaryResponseHandler() {
            override fun onStart() {}
            override fun onFailure() {}
            override fun onSuccess() {
                success = true
            }

            override fun onFinish() {
                if (success) {
                    successfullyLoaded = true
                }
            }
        })
        initVideoThumb()
        watermarkImage.setOnClickListener(View.OnClickListener {
            initImagePicker()
        })
    }

    private fun initImagePicker() {

        val intent = Intent()
        intent.action = Intent.ACTION_PICK
        intent.type = "image/*"
        val list =
            activity?.packageManager?.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
        if (list?.size!! <= 0) {
            Toast.makeText(context, "No video picker found on device", Toast.LENGTH_SHORT)
                .show()
            return
        }
        startActivityForResult(intent, IMAGE_RETURN)
    }

    private fun initVideoThumb() {
//        val file = UploadTask.getFileFromUri(videoUri, this)

        val thumb = ThumbnailUtils.createVideoThumbnail(
            videoFile?.path!!,
            MediaStore.Images.Thumbnails.MINI_KIND
        )
        videoThumbImageView.setImageBitmap(thumb)
        tvVideoName.setText(videoFile.name)

    }

    companion object {

        @JvmStatic
        fun newInstance(videoFile: File?) =
            VideoRecordFragment(videoFile)
    }


    fun isValid(): Boolean {
        if (watermarkPath == null || !File(watermarkPath).exists()) {
            Toast.makeText(
                context,
                "Please choose Watermark image from gallery",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Please choose Watermark image from gallery")
            return false
        }
        if (!videoFile!!.exists()) {
            Toast.makeText(context, "Video File doesnt exists", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    var isBackEnabled = false;
    override fun onClick(view: View?) {
        if (isBackEnabled) {
            fragmentManager?.beginTransaction()?.remove(this)?.commit()
            return
        }
        if (!isValid()) {
            return
        }
        view?.isEnabled = false
        watermarkImage.isEnabled = false
        val cmd = arrayOf(
            "-i",
            videoFile!!.path,
            "-i",
            watermarkPath,
            "-filter_complex",
            "overlay=x=main_w-main_w:y=main_h-overlay_h",
            "-c:v","libx264","-preset", "ultrafast",
            output
        )
        val fileLength: Double = (videoFile!!.length() / 1024).toDouble()
        Log.d(TAG, "File length " + fileLength)
        if (successfullyLoaded) {
            ffMpeg?.execute(cmd, object : FFmpegExecuteResponseHandler {
                override fun onSuccess(message: String) {
                    Log.d(TAG, "success")
                    progressTv.setText("Watermark Successfully added")
                    view?.isEnabled = true
                    isBackEnabled = true
                    addWatermarkBtn.setText("Back")
                    if (videoFile.exists()) {
                        videoFile.delete()
                    }

                }

                override fun onProgress(message: String) {

//                    val frameSize: List<String> = message.split("size=")
//                    if (frameSize.size > 1) {
//                        val currentProgress: Int = Integer.parseInt(
//                            frameSize[1].split("kB")[0]
//                                .replace(" ", "")
//                        )
//                        val progress: Int = ((currentProgress / fileLength) * 100).toInt()
//                        Log.d(TAG, "progress $progress")
//                    } else {
//                    }
                    Log.d(TAG, "progress $message")
                }

                override fun onFailure(message: String) {
                    Log.d(TAG, "failure $message")
                    progressTv.setText("Failure")
                }

                override fun onStart() {
                    progressBar.visibility = VISIBLE
                    progressTv.setText("Video processing started")
                    Log.d(TAG, "start")
                }

                override fun onFinish() {
                    progressBar.visibility = GONE
                    Log.d(TAG, "finish")
                }
            })
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_RETURN) {
            if (resultCode == Activity.RESULT_OK) {
                val imageUri = data!!.data
                val file: File = UploadTask.getFileFromUri(imageUri, activity)
                watermarkPath = file.path
                watermarkImageTV.setText(file.name)
                watermarkImage.setImageBitmap(BitmapFactory.decodeFile(watermarkPath))
            }
        }
    }
}