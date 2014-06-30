package com.campmobile.core.compressvideo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.VideoView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by abyss on 2014. 6. 5..
 */
public class MainActivity extends Activity {
	String TAG = "MainActivity";
	private ListView mListView;
	private ThumbnailAdapter mThumbnailAdapter;
	private List<VideoThumb> mVideoList = new ArrayList<VideoThumb>();

	private static final String FRAME_DUMP_FOLDER_PATH = Environment.getExternalStorageDirectory() + File.separator
		+ "ffmpeg-test";

	private static class DumpFrameTask extends AsyncTask<Void, Integer, Void> {
		ProgressDialog mlDialog;
		String mInput;
		MainActivity mlOuterAct;

		DumpFrameTask(MainActivity pContext, String input) {
			mlOuterAct = pContext;
			mInput = input;
		}

		@Override
		protected void onPreExecute() {
			mlDialog = ProgressDialog.show(mlOuterAct, "Dump Frames", "Processing..Wait..", false);
		}

		@Override
		protected Void doInBackground(Void... params) {
			transMain(mInput, FRAME_DUMP_FOLDER_PATH + "/" +  "h263_aac_encoded.3gp");
			return null;
		}

		@Override
		protected void onPostExecute(Void param) {
			if (null != mlDialog && mlDialog.isShowing()) {
				mlDialog.dismiss();
			}
		}
	}

	private static native int transMain(String inputFileName, String outputFileName);

	static {
		//        System.loadLibrary("x264");
//		System.loadLibrary("avutil-52");
//		System.loadLibrary("avcodec-55");
//		System.loadLibrary("avformat-55");
//		System.loadLibrary("avfilter-3");
//		System.loadLibrary("swscale-2");
//		System.loadLibrary("swresample-0");
//		System.loadLibrary("postproc-52");
//		System.loadLibrary("transcoder");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
//		Utils.copyAssets(this, "libx264.so.142", FRAME_DUMP_FOLDER_PATH);
//        Utils.copyAssets(this, "libopencore-amrnb.so.0", FRAME_DUMP_FOLDER_PATH);
//		System.load(FRAME_DUMP_FOLDER_PATH + "/" + "libx264.so.142");
//        System.load(FRAME_DUMP_FOLDER_PATH + "/" + "libopencore-amrnb.so.0");
        System.loadLibrary("avutil-52");
        System.loadLibrary("avcodec-55");
        System.loadLibrary("avformat-55");
        System.loadLibrary("avfilter-3");
        System.loadLibrary("swscale-2");
        System.loadLibrary("swresample-0");
        System.loadLibrary("transcoder");

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		getVideoList();
		mListView = (ListView)findViewById(R.id.list_view);
		mThumbnailAdapter = new ThumbnailAdapter(this, mVideoList);
		mListView.setAdapter(mThumbnailAdapter);
		mListView.setOnItemClickListener(mItemClickListener);

	}

	public void getVideoList() {
		String[] proj = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DISPLAY_NAME,
			MediaStore.Video.Media.SIZE, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION,
			MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.RESOLUTION};

		Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		String sortOrder = MediaStore.Video.VideoColumns.DATE_TAKEN + " DESC";
		CursorLoader cursorLoader = new CursorLoader(this, contentUri, proj, null, null, sortOrder);
		Cursor cursor = cursorLoader.loadInBackground();
		cursor.moveToFirst();
		int id = cursor.getColumnIndex(MediaStore.Video.Media._ID);
		int name = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
		int size = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);
		int title = cursor.getColumnIndex(MediaStore.Video.Media.TITLE);
		int data = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
		int duration = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
		int date = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED);
		int resolution = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION);
		do {
			VideoThumb video = new VideoThumb();
			video.setFileName(cursor.getString(data));
			video.setTitle(cursor.getString(title));
			video.setThumbnail(getVideoThumbnail(cursor.getLong(id)));
			Log.d(TAG, "resolution : " + cursor.getString(resolution));
			Log.d(TAG, "title : " + cursor.getString(title));
			Log.d(TAG, "filename : " + cursor.getString(data));
			mVideoList.add(video);
		} while ((cursor.moveToNext()));

	}

	public Bitmap getVideoThumbnail(long id) {
		ContentResolver cr = getContentResolver();
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = 1;

		Bitmap videoThumbnail = MediaStore.Video.Thumbnails.getThumbnail(cr, id,
			MediaStore.Video.Thumbnails.MICRO_KIND, options);
		cr = null;
		options = null;
		return videoThumbnail;
	}

	private AdapterView.OnItemClickListener mItemClickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			transCode(position);
		}
	};

	String fileName = "h264_aac_10MB.mp4";

	private void transCode(int position) {
		Utils.copyAssets(this, fileName, FRAME_DUMP_FOLDER_PATH);
		Log.d(TAG, mVideoList.get(position).getFileName());
		DumpFrameTask dumpFrameTask = new DumpFrameTask(MainActivity.this, FRAME_DUMP_FOLDER_PATH + "/" + fileName);
		//        DumpFrameTask dumpFrameTask = new DumpFrameTask(MainActivity.this, mVideoList.get(position).getFileName());
		dumpFrameTask.execute();
		//        TransCoder transCoder = new TransCoder();
		//        transCoder.encode(mVideoList.get(position).getFileName());
	}

	class VideoThumb {
		String title;
		String fileName;
		Bitmap thumbnail;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public Bitmap getThumbnail() {
			return thumbnail;
		}

		public void setThumbnail(Bitmap thumbnail) {
			this.thumbnail = thumbnail;
		}
	}
}
