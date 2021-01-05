package com.arm.youtubevideouploaderkotlin;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public class UploadTask extends AsyncTask<Uri, MediaHttpUploader, String> {
    private Activity activity;
    private GoogleAccountCredential mCredential;
    private TextView tvProgress;
    private final String API_KEY = "AIzaSyA-Gg3lV3Lo9nJZf6u6iDoBhCnsTWA-6yM";
    public static final int AUTH_CODE_REQUEST_CODE = 5;
    private String videoName;
    private TextView videoUrlTv;
    private ProgressBar progressBar;
    private String errorMessage = null;

    public UploadTask(Activity activity, GoogleAccountCredential mCredential, TextView tcProgress, TextView videoUrlTv, String videoName, ProgressBar progressBar) {
        this.activity = activity;
        this.mCredential = mCredential;
        this.tvProgress = tcProgress;
        this.videoName = videoName;
        this.videoUrlTv = videoUrlTv;
        this.progressBar = progressBar;
    }

    private String TAG = "log_data";

    @Override
    protected String doInBackground(Uri... voids) {
        String videoUrl = null;
        try {
            videoUrl = uploadYoutube(voids[0]);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        if (videoUrl != null) {
            videoUrl = "https://www.youtube.com/watch?v=" + videoUrl;
        }
        return videoUrl;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        if (s != null) {
            videoUrlTv.setText(s);
        } else if (errorMessage != null) {
            Fragment fr = ErrorPageFragment.newInstance(errorMessage);
            FragmentTransaction transaction = activity.getFragmentManager().beginTransaction();
            transaction.replace(R.id.fragmentContainer,fr,"add");
            transaction.commit();
        }
    }


    private String uploadYoutube(Uri data) {

        HttpTransport transport = AndroidHttp.newCompatibleTransport();
//        JsonFactory jsonFactory = new AndroidJsonFactory(); // GsonFactory
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        HttpRequestInitializer initializer = new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                mCredential.initialize(request);
                request.setLoggingEnabled(true);
//                request.setIOExceptionHandler(new HttpBackOffIOExceptionHandler(new ExponentialBackOff()));
            }
        };

        YouTube.Builder youtubeBuilder = new YouTube.Builder(transport, jsonFactory, initializer);
        youtubeBuilder.setApplicationName(activity.getApplicationContext().getString(R.string.app_name));
//        youtubeBuilder.setYouTubeRequestInitializer(new YouTubeRequestInitializer(API_KEY));
        YouTube youtube = youtubeBuilder.build();

        String PRIVACY_STATUS = "public"; // or public,private
        String PARTS = "snippet,status,contentDetails";

        String videoId = null;
        try {
            Video videoObjectDefiningMetadata = new Video();
            videoObjectDefiningMetadata.setStatus(new VideoStatus().setPrivacyStatus(PRIVACY_STATUS));
            String title = videoName != null ? videoName : "My Video " + System.currentTimeMillis();
            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle(title);
//            snippet.setDescription("MyDescription");
            snippet.setTags(Arrays.asList(new String[]{"TaG1,TaG2"}));
            videoObjectDefiningMetadata.setSnippet(snippet);

            YouTube.Videos.Insert videoInsert = youtube.videos().insert(
                    PARTS,
                    videoObjectDefiningMetadata,
                    getMediaContent(
                            getFileFromUri(data, activity)));/*.setKey(API_KEY).setOauthToken(token);*/

            MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(false);

            MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener() {

                public void progressChanged(MediaHttpUploader uploader) throws IOException {
                    Log.d(TAG, "progressChanged: " + uploader.getUploadState());
                    Log.d(TAG, "Progress " + uploader.getProgress());
                    publishProgress(uploader);
                }
            };
            uploader.setProgressListener(progressListener);


            Log.d(TAG, "Uploading..");
            Video returnedVideo = videoInsert.execute();
            Log.d(TAG, "Video upload completed");
            videoId = returnedVideo.getId();
//            https://www.youtube.com/watch?v=
            Log.d(TAG, String.format("videoId = [%s]", videoId));
        } catch (final GooglePlayServicesAvailabilityIOException availabilityException) {
            Log.e(TAG, "GooglePlayServicesAvailabilityIOException", availabilityException);
            errorMessage = availabilityException.getMessage();
        } catch (UserRecoverableAuthIOException userRecoverableException) {
            Log.i(TAG, String.format("UserRecoverableAuthIOException: %s",
                    userRecoverableException.getMessage()));
            activity.startActivityForResult(userRecoverableException.getIntent(), AUTH_CODE_REQUEST_CODE);
        } catch (IOException e) {
            errorMessage = e.getMessage();
            Log.e(TAG, "IOException", e);
        }

        return videoId;

    }


    public static File getFileFromUri(Uri uri, Activity activity) {

        try {
            String filePath = null;

            String[] proj = {MediaStore.Video.VideoColumns.DATA};

            Cursor cursor = activity.getContentResolver().query(uri, proj, null, null, null);

            if (cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATA);
                filePath = cursor.getString(column_index);
            }

            cursor.close();

            File file = new File(filePath);
            cursor.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onProgressUpdate(MediaHttpUploader... uploaders) {
        super.onProgressUpdate(uploaders);
        MediaHttpUploader.UploadState state = uploaders[0].getUploadState();
        videoUrlTv.setText(state.name());
        switch (uploaders[0].getUploadState()) {
            case INITIATION_STARTED:
                break;
            case INITIATION_COMPLETE:
                break;
            case MEDIA_IN_PROGRESS:
                try {
                    int progress = (int) Math.round(uploaders[0].getProgress() * 100);
                    progressBar.setProgress(progress);
                    tvProgress.setText(progress + " %");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
            case MEDIA_COMPLETE:
                progressBar.setProgress(100);
                tvProgress.setText("100 %");
                break;
            case NOT_STARTED:
                Log.d(TAG, "progressChanged: upload_not_started");
                break;
        }
    }

    private AbstractInputStreamContent getMediaContent(File file) throws FileNotFoundException {
        InputStreamContent mediaContent = new InputStreamContent(
                "video/*",
                new BufferedInputStream(new FileInputStream(file)));
        mediaContent.setLength(file.length());

        return mediaContent;
    }

}
