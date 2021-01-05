package com.arm.youtubevideouploaderkotlin

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ThumbnailUtils
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentTransaction
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTubeScopes
import kotlinx.android.synthetic.main.activity_main.*

const val REQUEST_VIDEO_CAPTURE = 1

class MainActivity : AppCompatActivity() {

    val REQUEST_ACCOUNT_PICKER = 1000
    val MY_PERMISSIONS_STORAGE = 0
    val GALLERY_RETURN = 2

    var videoUri: Uri? = null
    val PREF_ACCOUNT_NAME = "google_account_pref"
    var mCredential: GoogleAccountCredential? = null
    var accountName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val scopes: ArrayList<String> = ArrayList()
        scopes.add(YouTubeScopes.YOUTUBE_READONLY)
        scopes.add(YouTubeScopes.YOUTUBE_UPLOAD)
        mCredential = GoogleAccountCredential.usingOAuth2(
            applicationContext, scopes
        ).setBackOff(ExponentialBackOff())

        accountName = getPreferences(Context.MODE_PRIVATE)
            .getString(PREF_ACCOUNT_NAME, null)
        if (accountName != null) {
            accountNameTv.text = accountName
            mCredential?.setSelectedAccountName(accountName)
        }
        accountNameTv.setOnClickListener(View.OnClickListener { chooseGoogleAccount() })

    }

    fun chooseGoogleAccount() {
        accountNameTv.isEnabled = false
        startActivityForResult(
            mCredential?.newChooseAccountIntent(),
            REQUEST_ACCOUNT_PICKER
        )
    }

    private fun isDeviceOnline(): Boolean {
        val connMgr =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ACCOUNT_PICKER -> {
                if (resultCode == RESULT_OK && data != null &&
                    data.getExtras() != null
                ) {
                    accountName =
                        data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        accountNameTv.isEnabled = true
                        accountNameTv.setText(accountName);
                        mCredential?.setSelectedAccountName(accountName);
                        val settings = getPreferences(Context.MODE_PRIVATE);
                        val editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                    }
                }
            }
            GALLERY_RETURN -> {
                if (resultCode == Activity.RESULT_OK) {
                    videoUri = data!!.data
                    val file = UploadTask.getFileFromUri(videoUri, this)
                    val thumb = ThumbnailUtils.createVideoThumbnail(
                        file.path,
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                    videoThumbImageView.setImageBitmap(thumb)
                }
            }

            UploadTask.AUTH_CODE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val videoName = etVideoName.text.toString()
                    UploadTask(
                        this,
                        mCredential,
                        progressTv,
                        uploadedVideoUrlTv,
                        videoName, progressBar
                    ).execute(videoUri)
                }
            }
        }
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            openRecorderFragment(data?.data);
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun onSelectVideoClicked(view: View) {
        if (checkStoragePermission()) {
            initVideoPicker()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkStoragePermission(): Boolean {
        var permissionGranted = false
        val storage = ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val accounts = ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.GET_ACCOUNTS
        )
        if (storage != PackageManager.PERMISSION_GRANTED || accounts != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.GET_ACCOUNTS
                ), MY_PERMISSIONS_STORAGE
            )
        } else {
            // Permission already granted. Enable the SMS button.
            permissionGranted = true
        }
        return permissionGranted
    }

    private fun initVideoPicker() {
        val intent = Intent()
        intent.action = Intent.ACTION_PICK
        intent.type = "video/*"
        val list =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (list.size <= 0) {
            Toast.makeText(this@MainActivity, "No video picker found on device", Toast.LENGTH_SHORT)
                .show()
            return
        }
        startActivityForResult(intent, GALLERY_RETURN)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_STORAGE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission Granted
                    initVideoPicker();
                } else {

                }
            }
        }
    }

    fun uploadVideo(view: View) {
        if (!isDeviceOnline()) {
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_SHORT).show()
            return
        }
        if (videoUri == null) {
            Toast.makeText(this, "Please select Video", Toast.LENGTH_SHORT).show()
            return
        }
        if (accountName == null) {
            Toast.makeText(this, "Please select Account", Toast.LENGTH_SHORT).show()
            return
        }
        val videoName = etVideoName.text.toString()
        UploadTask(
            this,
            mCredential,
            progressTv,
            uploadedVideoUrlTv,
            videoName,
            progressBar
        ).execute(videoUri)
    }

    fun openRecorderFragment(videoUri: Uri?) {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        val videoFile = UploadTask.getFileFromUri(videoUri, this)
        transaction.add(R.id.fragmentContainer, VideoRecordFragment.newInstance(videoFile))
        transaction.commit()
    }


    fun dispatchTakeVideoIntent(view: View) {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Intent(MediaStore.ACTION_VIDEO_CAPTURE).also { takeVideoIntent ->
                takeVideoIntent.resolveActivity(packageManager)?.also {
                    startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE)
                }
            }
        }else{
            Toast.makeText(baseContext,"No Storage Permission",Toast.LENGTH_SHORT).show()
        }
    }


}